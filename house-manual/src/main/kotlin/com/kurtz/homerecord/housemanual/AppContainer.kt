package com.kurtz.homerecord.housemanual

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
class AppContainer(app: Application) {
    /** Set by MainActivity while it is resumed so Credential Manager can show its sheet. */
    @Volatile var activityContext: Context? = null

    internal val db = Room.databaseBuilder(app, HomeRecordDatabase::class.java, "homerecord.db").build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val tokens = EncryptedTokenStore(app)
    private val authApi = RetrofitAuthApi.create(BuildConfig.API_BASE_URL)
    val auth = AuthRepository(authApi, tokens, CredentialManagerGoogleSource({ activityContext }, BuildConfig.GOOGLE_WEB_CLIENT_ID), restoreNow = false)

    /** Client for authenticated calls (sync): adds the bearer token and handles refresh and 401. */
    val http: Call.Factory = LazyCallFactory { OkHttpClient.Builder().addInterceptor(SessionInterceptor(auth, tokens)).build() }

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
    }

    val homes = HomeRepository(db) { entity, id -> recordHomeChange(entity, id) }

    val items = ItemRepository(db) { entity, id -> recordHomeChange(entity, id) }

    val search = com.kurtz.homerecord.housemodel.search.SearchRepository(db)

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
    val textRecognizer: TextRecognizer = MlKitTextRecognizer(app)

    val layers = PrefsLayerStore(app)

    /** Pro state (AC-H18); without a RevenueCat key everyone stays on the free tier. */
    val billing: com.kurtz.homerecord.core.billing.ProBilling =
        if (BuildConfig.REVENUECAT_API_KEY.isEmpty()) com.kurtz.homerecord.core.billing.UnavailableBilling
        else com.kurtz.homerecord.housemanual.billing.RevenueCatBilling(app, BuildConfig.REVENUECAT_API_KEY) { activityContext }

    init {
        // Keystore-backed prefs are slow to open; keep them off the main thread during cold start.
        scope.launch(Dispatchers.IO) { auth.restore() }
        syncCoordinator.start()
        (billing as? com.kurtz.homerecord.housemanual.billing.RevenueCatBilling)?.let { rc -> scope.launch { rc.refresh() } }
        // Queued warranty lookups run when connectivity returns (AC-C34).
        scope.launch { connectivity.online.collect { if (it) warrantyLookup.runPending() } }
    }
}

/** Logcat tag for sync failures, so a "Sync failed" chip has a cause in the device log. */
private const val SYNC_LOG_TAG = "HomeRecordSync"

class HomeRecordApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
