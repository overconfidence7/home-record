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
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient

/** Manual DI: one container per process, created in [HomeRecordApplication.onCreate]. */
class AppContainer(app: Application) {
    /** Set by MainActivity while it is resumed so Credential Manager can show its sheet. */
    @Volatile var activityContext: Context? = null

    private val db = Room.databaseBuilder(app, HomeRecordDatabase::class.java, "homerecord.db").build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val tokens = EncryptedTokenStore(app)
    private val authApi = RetrofitAuthApi.create(BuildConfig.API_BASE_URL)
    val auth = AuthRepository(authApi, tokens, CredentialManagerGoogleSource({ activityContext }, BuildConfig.GOOGLE_WEB_CLIENT_ID))

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

    val layers = PrefsLayerStore(app)

    init {
        syncCoordinator.start()
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
