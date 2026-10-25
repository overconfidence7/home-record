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
import com.kurtz.homerecord.core.geo.AndroidGeocoder
import com.kurtz.homerecord.core.punch.PunchRepository
import com.kurtz.homerecord.fieldlog.jobs.JobGeocoder
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import okhttp3.OkHttpClient

/** Manual DI: one container per process, created in [HomeRecordApplication.onCreate]. */
class AppContainer(private val app: Application) {
    /** Tab ids asked for from outside the UI (the clock widget); held until the tabs are on screen. */
    val tabRequests = kotlinx.coroutines.flow.MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1)

    /** Set by MainActivity while it is resumed so Credential Manager can show its sheet. */
    @Volatile var activityContext: Context? = null

    private val tokens = EncryptedTokenStore(app)
    private val authApi = RetrofitAuthApi.create(BuildConfig.API_BASE_URL)
    val auth = AuthRepository(authApi, tokens, CredentialManagerGoogleSource({ activityContext }, BuildConfig.GOOGLE_WEB_CLIENT_ID), restoreNow = false)

    /** Client for authenticated calls (sync, T010): adds the bearer token and handles refresh and 401. */
    val http: OkHttpClient = OkHttpClient.Builder().addInterceptor(SessionInterceptor(auth, tokens)).build()

    val companyApi = RetrofitCompanyApi.create(BuildConfig.API_BASE_URL, http)
    val company = CompanyRepository(companyApi, PrefsCompanyCache(app)).also { repo ->
        // Sign out drops cached company data (AC-F4).
        auth.addSignOutListener(repo::clear)
    }

    val db = Room.databaseBuilder(app, HomeRecordDatabase::class.java, "homerecord.db").build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val syncEngine by lazy {
        SyncEngine(
        RetrofitSyncApi.create(BuildConfig.API_BASE_URL, http),
        RoomRowStore(db),
        db.outboxDao(),
        db.syncStateDao(),
        db.syncBaseDao(),
        DeviceIdStore.get(app),
        )
    }

    val syncCoordinator by lazy {
        SyncCoordinator(
        scope,
        { tenant -> syncEngine.sync(tenant) },
        auth.state.map { it is AuthState.SignedIn },
        company.state.map { s -> (s as? CompanyState.Active)?.let { TenantRef(TenantKind.Company, it.membership.companyId) } },
        AndroidConnectivityMonitor(app),
        )
    }

    /** Repositories call this after each write to a synced row; it queues the row and asks for a sync. */
    suspend fun recordChange(entity: String, id: String) {
        if (entity == "punch") runCatching { com.kurtz.homerecord.fieldlog.widget.ClockWidget().updateAll(app) }
        val tenant = company.current?.let { TenantRef(TenantKind.Company, it.companyId) } ?: return
        syncEngine.recordChange(tenant, entity, id)
        syncCoordinator.requestSync()
    }

    val punches by lazy { PunchRepository(db, onChange = ::recordChange) }

    val approvals by lazy { com.kurtz.homerecord.core.punch.TimesheetApprovals(db, onChange = ::recordChange) }
    val crewClock by lazy {
        val loc = com.kurtz.homerecord.core.location.FusedLocationProvider(app)
        com.kurtz.homerecord.core.punch.CrewClock(punches, com.kurtz.homerecord.core.punch.ClockController(punches, loc), loc)
    }
    val clock by lazy { com.kurtz.homerecord.core.punch.ClockController(punches, com.kurtz.homerecord.core.location.FusedLocationProvider(app)) }

    /** What FL-Clock needs; settings come from the company profile when this role can read it, else the defaults. */
    fun clockDeps(context: Context, membership: com.kurtz.homerecord.core.company.CompanyMembership, memberId: String): com.kurtz.homerecord.fieldlog.clock.ClockDeps {
        val prefs = context.getSharedPreferences("clock", Context.MODE_PRIVATE)
        val location = com.kurtz.homerecord.core.location.FusedLocationProvider(context)
        return com.kurtz.homerecord.fieldlog.clock.ClockDeps(
            companyId = membership.companyId, memberId = memberId,
            controller = com.kurtz.homerecord.core.punch.ClockController(punches, location), punches = punches,
            jobs = db.jobDao(), location = location,
            settings = {
                (companyApi.profile(membership.companyId) as? com.kurtz.homerecord.core.company.ApiResult.Ok)?.value?.settings
                    ?: com.kurtz.homerecord.core.company.CompanySettings()
            },
            permissionAsked = { prefs.getBoolean("location_asked", false) },
            markPermissionAsked = { prefs.edit().putBoolean("location_asked", true).apply() },
            role = membership.role,
            crewClock = com.kurtz.homerecord.core.punch.CrewClock(punches, com.kurtz.homerecord.core.punch.ClockController(punches, location), location),
            members = db.memberDao().observeFor(membership.companyId),
        )
    }

    fun todayDeps(membership: com.kurtz.homerecord.core.company.CompanyMembership, memberId: String) =
        com.kurtz.homerecord.fieldlog.today.TodayDeps(
            memberId = memberId,
            base = com.kurtz.homerecord.fieldlog.today.TodayUiState(membership.companyName, membership.role),
            punches = db.punchDao().observeFor(membership.companyId),
            members = db.memberDao().observeFor(membership.companyId),
            jobs = db.jobDao().observeFor(membership.companyId),
            assignments = db.assignmentDao().observeForCompany(membership.companyId),
            refreshMembers = { refreshMembers(membership.companyId) },
        )

    /** Caches the team list locally so crew can be picked offline; the list is not synced from here (the server owns members). */
    suspend fun refreshMembers(companyId: String) {
        val result = companyApi.members(companyId) as? com.kurtz.homerecord.core.company.ApiResult.Ok ?: return
        val now = System.currentTimeMillis()
        result.value.forEach { m ->
            db.memberDao().upsert(
                com.kurtz.homerecord.core.data.MemberEntity(
                    id = m.userId, companyId = companyId, userId = m.userId, name = m.displayName, role = m.role.wire,
                    rateCents = m.rateCents, createdAt = now, updatedAt = now,
                ),
            )
        }
    }

    val homes by lazy { com.kurtz.homerecord.housemodel.HomeRepository(db, onChange = ::recordChange) }

    val layers by lazy { com.kurtz.homerecord.housemodel.blueprint.PrefsLayerStore(app) }

    val jobHomes by lazy { com.kurtz.homerecord.fieldlog.jobs.JobHomeProvisioner(db.jobDao(), db.homeDao(), db.floorDao(), ::recordChange) }

    val jobGeocoder by lazy { JobGeocoder(db.jobDao(), AndroidGeocoder(app), ::recordChange) }

    init {
        // Keystore-backed prefs are slow to open; keep them off the main thread during cold start.
        scope.launch(Dispatchers.IO) {
            auth.restore()
            syncCoordinator.start()
        }
        // Addresses typed offline get coordinates once the network is back (AC-F5).
        scope.launch(Dispatchers.IO) {
            val connectivity = AndroidConnectivityMonitor(app)
            combine(connectivity.online, company.state) { online, c -> (c as? CompanyState.Active)?.takeIf { online } }
                .collect { active -> active?.let { jobGeocoder.resolvePending(it.membership.companyId) } }
        }
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
