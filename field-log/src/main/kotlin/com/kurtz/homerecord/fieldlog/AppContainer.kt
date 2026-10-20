package com.kurtz.homerecord.fieldlog

import android.app.Application
import android.content.Context
import com.kurtz.homerecord.core.auth.AuthRepository
import com.kurtz.homerecord.core.auth.CredentialManagerGoogleSource
import com.kurtz.homerecord.core.auth.EncryptedTokenStore
import com.kurtz.homerecord.core.auth.RetrofitAuthApi
import com.kurtz.homerecord.core.auth.SessionInterceptor
import com.kurtz.homerecord.core.company.CompanyRepository
import com.kurtz.homerecord.core.company.PrefsCompanyCache
import com.kurtz.homerecord.core.company.RetrofitCompanyApi
import androidx.room.Room
import com.kurtz.homerecord.core.auth.AuthState
import com.kurtz.homerecord.core.company.CompanyState
import com.kurtz.homerecord.core.data.HomeRecordDatabase
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient

/** Manual DI: one container per process, created in [HomeRecordApplication.onCreate]. */
class AppContainer(app: Application) {
    /** Set by MainActivity while it is resumed so Credential Manager can show its sheet. */
    @Volatile var activityContext: Context? = null

    private val tokens = EncryptedTokenStore(app)
    private val authApi = RetrofitAuthApi.create(BuildConfig.API_BASE_URL)
    val auth = AuthRepository(authApi, tokens, CredentialManagerGoogleSource({ activityContext }, BuildConfig.GOOGLE_WEB_CLIENT_ID))

    /** Client for authenticated calls (sync, T010): adds the bearer token and handles refresh and 401. */
    val http: OkHttpClient = OkHttpClient.Builder().addInterceptor(SessionInterceptor(auth, tokens)).build()

    val companyApi = RetrofitCompanyApi.create(BuildConfig.API_BASE_URL, http)
    val company = CompanyRepository(companyApi, PrefsCompanyCache(app)).also { repo ->
        // Sign out drops cached company data (AC-F4).
        auth.addSignOutListener(repo::clear)
    }

    private val db = Room.databaseBuilder(app, HomeRecordDatabase::class.java, "homerecord.db").build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val syncEngine = SyncEngine(
        RetrofitSyncApi.create(BuildConfig.API_BASE_URL, http),
        RoomRowStore(db),
        db.outboxDao(),
        db.syncStateDao(),
        db.syncBaseDao(),
        DeviceIdStore.get(app),
    )

    val syncCoordinator = SyncCoordinator(
        scope,
        { tenant -> syncEngine.sync(tenant) },
        auth.state.map { it is AuthState.SignedIn },
        company.state.map { s -> (s as? CompanyState.Active)?.let { TenantRef(TenantKind.Company, it.membership.companyId) } },
        AndroidConnectivityMonitor(app),
    )

    /** Repositories call this after each write to a synced row; it queues the row and asks for a sync. */
    suspend fun recordChange(entity: String, id: String) {
        val tenant = company.current?.let { TenantRef(TenantKind.Company, it.companyId) } ?: return
        syncEngine.recordChange(tenant, entity, id)
        syncCoordinator.requestSync()
    }

    init {
        syncCoordinator.start()
    }

    /** Invite code from a tapped join link, held until the person has signed in and reaches FL-Welcome. */
    val pendingJoinCode = MutableStateFlow<String?>(null)

    fun inviteLink(code: String): String = "${BuildConfig.API_BASE_URL.trimEnd('/')}/join/$code"
}

class HomeRecordApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
