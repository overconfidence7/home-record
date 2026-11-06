package com.kurtz.homerecord.housemanual

import androidx.glance.appwidget.updateAll
import com.kurtz.homerecord.housemodel.overlays.nameRes
import android.app.Application
import android.content.Context
import android.util.Log
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.auth.AuthRepository
import com.kurtz.homerecord.core.auth.CredentialManagerGoogleSource
import com.kurtz.homerecord.core.auth.EncryptedTokenStore
import com.kurtz.homerecord.core.auth.RetrofitAuthApi
import com.kurtz.homerecord.core.auth.SessionInterceptor
import androidx.room.Room
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.core.ocr.TextRecognizer
import com.kurtz.homerecord.housemodel.ui.MlKitTextRecognizer
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.upkeep.UpkeepRepository
import com.kurtz.homerecord.housemodel.items.WarrantyLookupRepository
import com.kurtz.homerecord.core.warranty.RetrofitWarrantyLookupApi
import com.kurtz.homerecord.housemodel.ui.AndroidMediaFiles
import com.kurtz.homerecord.housemodel.ui.AndroidDocumentFiles
import com.kurtz.homerecord.housemodel.documents.DocumentRepository
import com.kurtz.homerecord.housemodel.blueprint.PrefsLayerStore
import com.kurtz.homerecord.core.auth.AuthState
import com.kurtz.homerecord.core.sync.AndroidConnectivityMonitor
import com.kurtz.homerecord.core.sync.DeviceIdStore
import com.kurtz.homerecord.core.sync.HomeRegistrar
import com.kurtz.homerecord.core.sync.RetrofitHomeRegistrationApi
import com.kurtz.homerecord.core.sync.RetrofitSyncApi
import com.kurtz.homerecord.core.sync.RoomRowStore
import com.kurtz.homerecord.core.sync.SyncCoordinator
import com.kurtz.homerecord.core.sync.SyncEngine
import com.kurtz.homerecord.core.sync.endedTenants
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import com.kurtz.homerecord.core.net.LazyCallFactory
import okhttp3.Call
import okhttp3.OkHttpClient

/** Manual DI: one container per process, created in [HomeRecordApplication.onCreate]. */
class AppContainer(private val app: Application) {
    /** Tabs and screens asked for from outside the UI: the Emergency tile and widget (AC-H11). */
    val tabRequests = com.kurtz.homerecord.coreui.shell.TabRequests()
    /** Set by MainActivity while it is resumed so Credential Manager can show its sheet. */
    @Volatile var activityContext: Context? = null

    internal val db = Room.databaseBuilder(app, HomeRecordDatabase::class.java, "homerecord.db").build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val tokens = EncryptedTokenStore(app)
    private val authApi = RetrofitAuthApi.create(BuildConfig.API_BASE_URL)
    val auth = AuthRepository(authApi, tokens, CredentialManagerGoogleSource({ activityContext }, BuildConfig.GOOGLE_WEB_CLIENT_ID), restoreNow = false)

    /** Client for authenticated calls (sync): adds the bearer token and handles refresh and 401. */
    val http: Call.Factory = LazyCallFactory { OkHttpClient.Builder().addInterceptor(SessionInterceptor(auth, tokens)).build() }
    val account = com.kurtz.homerecord.core.auth.RetrofitAccountApi.create(BuildConfig.API_BASE_URL, http)

    /** The home is made offline; the first pass after sign-in creates it on the server (AC-H15). */
    private val homeRegistrar = HomeRegistrar(RetrofitHomeRegistrationApi.create(BuildConfig.API_BASE_URL, http), { db.homeDao().get(it)?.name })
        .also { registrar -> auth.addSignOutListener(registrar::reset) }

    val syncEngine = SyncEngine(
        RetrofitSyncApi.create(BuildConfig.API_BASE_URL, http),
        RoomRowStore(db),
        db.outboxDao(),
        db.syncCursorDao(),
        db.syncBaseDao(),
        DeviceIdStore.get(app),
        prepare = homeRegistrar::ensure,
    )

    private val connectivity = AndroidConnectivityMonitor(app)

    private val homeTenant = db.homeDao().observeAll().map { it.firstOrNull()?.let { h -> TenantRef(TenantKind.Home, h.id) } }

    val syncCoordinator = SyncCoordinator(
        scope,
        { tenant ->
            syncEngine.sync(tenant).also { result ->
                if (result is ApiResult.Err) Log.w(SYNC_LOG_TAG, "Sync of ${tenant.key} failed: ${result.failure}")
            }
        },
        auth.state.map { it is AuthState.SignedIn },
        homeTenant,
        connectivity,
    )

    init {
        // A home that is gone (or replaced) starts from scratch if it comes back (cr:sync-global-cursor).
        scope.launch { homeTenant.endedTenants().collect { syncEngine.forgetTenant(it) } }
    }

    /** Every write goes to the outbox against the device's home, signed in or not; the coordinator sends it. */
    private suspend fun recordHomeChange(entity: String, id: String) {
        val homeId = if (entity == "home") id else db.homeDao().observeAll().first().firstOrNull()?.id
        if (homeId != null) {
            syncEngine.recordChange(TenantRef(TenantKind.Home, homeId), entity, id)
            syncCoordinator.requestSync()
        }
        // The Emergency widget shows the shutoff count (AC-H11); any item change may move it.
        if (entity == "item") runCatching { com.kurtz.homerecord.housemanual.emergency.EmergencyWidget().updateAll(app) }
        // Warranty reminders follow the stored warranty end date and the item's name (AC-C49); run apart from the
        // writer, which may still be inside its transaction.
        if (entity == "item") scope.launch { reminders.syncWarrantyFor(id) }
        if (entity == "warranty") scope.launch {
            val itemId = db.warrantyDao().get(id)?.itemId
            if (itemId != null) reminders.syncWarrantyFor(itemId) else homeId?.let { reminders.syncAllWarranties(it) }
        }
    }

    val homes = HomeRepository(
        db, app.resources::getString, onChange = { entity, id -> recordHomeChange(entity, id) },
        onPlanPhotosChanged = { changes -> scope.launch { wallPhotos.afterPlanChange(changes, wallTextures) } },
    )

    val items = ItemRepository(db, app.resources::getString) { entity, id -> recordHomeChange(entity, id) }

    /** Deletes the files of replaced or removed wall and overlay photos once no live row uses them (R10c). */
    private val replacedFiles = com.kurtz.homerecord.housemodel.walls.ReplacedFiles(db) { path ->
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { media.discard(path) }
    }

    val wallPhotos = com.kurtz.homerecord.housemodel.walls.WallPhotoRepository(
        db, onChange = { entity, id -> recordHomeChange(entity, id) }, sweep = replacedFiles::sweep,
    )
    val wallTextures: com.kurtz.homerecord.housemodel.walls.WallTextureMaker = com.kurtz.homerecord.housemodel.walls.AndroidWallTextureMaker()
    /** Pipes, wires and boxes drawn on walls and the plan (AC-C25..C28). */
    val overlays = com.kurtz.homerecord.housemodel.overlays.OverlayRepository(
        db, onChange = { entity, id -> recordHomeChange(entity, id) }, sweep = replacedFiles::sweep,
    )

    val search = com.kurtz.homerecord.housemodel.search.SearchRepository(
        db, overlayKindName = { app.resources.getString(it.nameRes) }, subtypeName = items::subtypeName,
        breakerName = { app.resources.getString(R.string.item_detail_breaker_chip, it) },
    )

    /** Breaker panels and their breakers (AC-C40). */
    val power: com.kurtz.homerecord.core.mapping.PowerMonitor = com.kurtz.homerecord.core.mapping.BatteryPowerMonitor(app)
    val mappingAlarm: com.kurtz.homerecord.core.mapping.MappingAlarm = com.kurtz.homerecord.core.mapping.SystemMappingAlarm(app)
    val panels = com.kurtz.homerecord.housemodel.panel.PanelRepository(db, onChange = { entity, id -> recordHomeChange(entity, id) })

    /** The home report PDF (AC-H13): read from Room, written to cache/exports, shared through FileProvider. */
    private val legendWords = com.kurtz.homerecord.housemodel.pdf.PanelLegendExporter(app).words
    val reportSource = com.kurtz.homerecord.housemodel.pdf.HomeReportSource(
        homes, items, panels,
        com.kurtz.homerecord.housemodel.pdf.ReportSourceWords(
            exterior = app.getString(com.kurtz.homerecord.housemodel.R.string.item_room_exterior),
            wholeHouse = app.getString(com.kurtz.homerecord.housemodel.R.string.item_room_whole_house),
            unassigned = app.getString(com.kurtz.homerecord.housemodel.R.string.report_unassigned),
            houseType = { app.getString(com.kurtz.homerecord.housemanual.onboarding.houseTypeLabelRes(it)) },
        ),
        legendWords,
    )
    val reportFile = com.kurtz.homerecord.housemodel.pdf.HomeReportFile(app, reportSource, legendWords)

    /** HM-Reminders (AC-C49, AC-H12). */
    val reminders = com.kurtz.homerecord.housemodel.reminders.ReminderRepository(db, app.resources::getString) { entity, id -> recordHomeChange(entity, id) }

    /** The daily 08:00 reminder summary (AC-C50); run by [com.kurtz.homerecord.housemanual.reminders.ReminderCheckWorker]. */
    val reminderNotifier = com.kurtz.homerecord.housemanual.reminders.ReminderNotifier(app)
    val reminderCheck = com.kurtz.homerecord.housemodel.reminders.ReminderCheck(
        homeIds = { db.homeDao().observeAll().first().map { it.id } }, repo = reminders, notices = reminderNotifier,
    )

    private val notificationAsk by lazy { app.getSharedPreferences("notifications", Context.MODE_PRIVATE) }

    /**
     * True once per install on Android 13+ when notifications are not allowed yet and the home has a reminder
     * (AC-C50); call off the main thread.
     */
    suspend fun shouldAskNotifications(homeId: String): Boolean {
        val granted = android.os.Build.VERSION.SDK_INT < 33 ||
            app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return com.kurtz.homerecord.housemodel.reminders.DailyReminderCheck.shouldAskPermission(
            android.os.Build.VERSION.SDK_INT, granted, notificationAsk.getBoolean(ASKED_KEY, false), reminders.count(homeId),
        )
    }

    fun markNotificationsAsked() {
        notificationAsk.edit().putBoolean(ASKED_KEY, true).apply()
    }

    val upkeep = UpkeepRepository(db) { entity, id -> recordHomeChange(entity, id) }

    /** Data plate vision fallback (AC-B11) and recalls (AC-B12). */
    val lookupApi = com.kurtz.homerecord.core.lookup.RetrofitLookupApi.create(BuildConfig.API_BASE_URL, http, com.kurtz.homerecord.core.lookup.PlateImageEncoder::encode)

    val warrantyLookup = WarrantyLookupRepository(db, RetrofitWarrantyLookupApi.create(BuildConfig.API_BASE_URL, http)) { entity, id -> recordHomeChange(entity, id) }

    /** The automatic warranty attempt after an item is first saved (AC-C34); runs past the form closing. */
    fun autoLookupWarranty(itemId: String) {
        scope.launch { warrantyLookup.autoLookup(itemId) }
    }

    val documents = DocumentRepository(db, AndroidDocumentFiles(app)) { entity, id -> recordHomeChange(entity, id) }

    val media: MediaFiles = AndroidMediaFiles(app)
    val importFiles = com.kurtz.homerecord.housemanual.importing.ImportFiles(app, db, { entity, id -> recordHomeChange(entity, id) }, scope)
    /** Claim links from the contractor's handoff email (AC-H17, AC-B40). */
    val handoffClaims: com.kurtz.homerecord.core.bundle.HandoffClaimApi =
        com.kurtz.homerecord.core.bundle.RetrofitHandoffClaimApi.create(BuildConfig.API_BASE_URL, http)
    val textRecognizer: TextRecognizer = MlKitTextRecognizer(app)

    val layers = PrefsLayerStore(app)

    /** Pro state (AC-H18); without a RevenueCat key everyone stays on the free tier. */
    val billing: com.kurtz.homerecord.core.billing.ProBilling =
        if (BuildConfig.REVENUECAT_API_KEY.isEmpty()) com.kurtz.homerecord.core.billing.UnavailableBilling
        else com.kurtz.homerecord.housemanual.billing.RevenueCatBilling(
            app, BuildConfig.REVENUECAT_API_KEY, { activityContext },
            homeId = { db.homeDao().observeAll().first().firstOrNull()?.id },
        )

    val addItemGate = com.kurtz.homerecord.housemanual.items.AddItemGate(items::count, billing, items::countPanels)

    init {
        // Keystore-backed prefs are slow to open; keep them off the main thread during cold start.
        scope.launch(Dispatchers.IO) { auth.restore() }
        syncCoordinator.start()
        (billing as? com.kurtz.homerecord.housemanual.billing.RevenueCatBilling)?.let { rc -> scope.launch { rc.refresh() } }
        // Queued warranty lookups run when connectivity returns (AC-C34).
        scope.launch { connectivity.online.collect { if (it) warrantyLookup.runPending() } }
    }
}

/** Device-level flag that the notification permission was asked, so signing out or a new home does not ask again. */
private const val ASKED_KEY = "notification_permission_asked"

/** Logcat tag for sync failures, so a "Sync failed" chip has a cause in the device log. */
private const val SYNC_LOG_TAG = "HomeRecordSync"

class HomeRecordApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // The daily 08:00 reminder check (AC-C50); KEEP leaves an already scheduled one alone.
        com.kurtz.homerecord.housemanual.reminders.ReminderCheckWorker.schedule(this)
    }
}
