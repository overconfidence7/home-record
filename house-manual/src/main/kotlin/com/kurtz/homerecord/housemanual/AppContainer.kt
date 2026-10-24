package com.kurtz.homerecord.housemanual

import android.app.Application
import android.content.Context
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
import com.kurtz.homerecord.core.sync.RetrofitSyncApi
import com.kurtz.homerecord.core.sync.RoomRowStore
import com.kurtz.homerecord.core.sync.SyncCoordinator
import com.kurtz.homerecord.core.sync.SyncEngine
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
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
    val http: OkHttpClient = OkHttpClient.Builder().addInterceptor(SessionInterceptor(auth, tokens)).build()

    val syncEngine = SyncEngine(
        RetrofitSyncApi.create(BuildConfig.API_BASE_URL, http),
        RoomRowStore(db),
        db.outboxDao(),
        db.syncStateDao(),
        db.syncBaseDao(),
        DeviceIdStore.get(app),
    )

    private val homeTenant = db.homeDao().observeAll().map { it.firstOrNull()?.let { h -> TenantRef(TenantKind.Home, h.id) } }

    val syncCoordinator = SyncCoordinator(
        scope,
        { tenant -> syncEngine.sync(tenant) },
        auth.state.map { it is AuthState.SignedIn },
        homeTenant,
        AndroidConnectivityMonitor(app),
    )

    /** Every write goes to the outbox against the device's home, signed in or not; the coordinator sends it. */
    val homes = HomeRepository(db) { entity, id ->
        val homeId = if (entity == "home") id else db.homeDao().observeAll().first().firstOrNull()?.id
        if (homeId != null) {
            syncEngine.recordChange(TenantRef(TenantKind.Home, homeId), entity, id)
            syncCoordinator.requestSync()
        }
    }

    val items = ItemRepository(db) { entity, id ->
        val homeId = db.homeDao().observeAll().first().firstOrNull()?.id
        if (homeId != null) {
            syncEngine.recordChange(TenantRef(TenantKind.Home, homeId), entity, id)
            syncCoordinator.requestSync()
        }
    }

    val upkeep = UpkeepRepository(db) { entity, id ->
        val homeId = db.homeDao().observeAll().first().firstOrNull()?.id
        if (homeId != null) {
            syncEngine.recordChange(TenantRef(TenantKind.Home, homeId), entity, id)
            syncCoordinator.requestSync()
        }
    }

    val warrantyLookup = WarrantyLookupRepository(db, RetrofitWarrantyLookupApi.create(BuildConfig.API_BASE_URL, http)) { entity, id ->
        val homeId = db.homeDao().observeAll().first().firstOrNull()?.id
        if (homeId != null) {
            syncEngine.recordChange(TenantRef(TenantKind.Home, homeId), entity, id)
            syncCoordinator.requestSync()
        }
    }

    /** The automatic warranty attempt after an item is first saved (AC-C34); runs past the form closing. */
    fun autoLookupWarranty(itemId: String) {
        scope.launch { warrantyLookup.autoLookup(itemId) }
    }

    val documents = DocumentRepository(db, AndroidDocumentFiles(app)) { entity, id ->
        val homeId = db.homeDao().observeAll().first().firstOrNull()?.id
        if (homeId != null) {
            syncEngine.recordChange(TenantRef(TenantKind.Home, homeId), entity, id)
            syncCoordinator.requestSync()
        }
    }

    val media: MediaFiles = AndroidMediaFiles(app)
    val textRecognizer: TextRecognizer = MlKitTextRecognizer(app)

    val layers = PrefsLayerStore(app)

    init {
        // Keystore-backed prefs are slow to open; keep them off the main thread during cold start.
        scope.launch(Dispatchers.IO) { auth.restore() }
        syncCoordinator.start()
        // Queued warranty lookups run when connectivity returns (AC-C34).
        scope.launch { AndroidConnectivityMonitor(app).online.collect { if (it) warrantyLookup.runPending() } }
    }
}

class HomeRecordApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
