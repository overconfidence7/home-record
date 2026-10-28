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
import com.kurtz.homerecord.core.sync.endedTenants
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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

    val mediaApi = com.kurtz.homerecord.core.media.RetrofitMediaTransferApi.create(BuildConfig.API_BASE_URL, http)
    val mediaUploader by lazy {
        com.kurtz.homerecord.core.media.MediaUploader(db.mediaUploadDao(), mediaApi, onUploaded = { row, presigned ->
            // Remember where the file lives in R2 so other phones, and this one after Free up, can download it (AC-F20).
            uploadedKeys.record(row.entity, row.entityId, presigned.key.ifEmpty { row.key.orEmpty() })
        })
    }
    private val uploadedKeys by lazy { com.kurtz.homerecord.core.media.UploadedKeys(db, onChange = ::recordChange) }

    val mediaCache by lazy {
        com.kurtz.homerecord.core.media.MediaCache(
            java.io.File(app.cacheDir, "downloads"),
            com.kurtz.homerecord.core.media.PrefsMediaCacheSettings(app.getSharedPreferences("media", Context.MODE_PRIVATE)),
            mediaApi,
        )
    }

    /** Queue a file for upload and ask WorkManager to run the queue once the network is up (AC-F54). */
    suspend fun uploadMedia(tenant: TenantRef, entity: String, entityId: String, filename: String, path: String, mime: String) {
        mediaUploader.enqueue(tenant, entity, entityId, filename, path, mime)
        com.kurtz.homerecord.core.media.MediaUploadWorker.schedule(app)
    }

    val syncEngine by lazy {
        SyncEngine(
        RetrofitSyncApi.create(BuildConfig.API_BASE_URL, http),
        RoomRowStore(db),
        db.outboxDao(),
        db.syncCursorDao(),
        db.syncBaseDao(),
        DeviceIdStore.get(app),
        )
    }

    private val companyTenant = company.state.map { s -> (s as? CompanyState.Active)?.let { TenantRef(TenantKind.Company, it.membership.companyId) } }

    val syncCoordinator by lazy {
        SyncCoordinator(
        scope,
        { tenant -> syncEngine.sync(tenant) },
        auth.state.map { it is AuthState.SignedIn },
        companyTenant,
        AndroidConnectivityMonitor(app),
        )
    }

    private val localWipe by lazy {
        com.kurtz.homerecord.core.data.LocalDataWipe(
            clearTables = { db.clearAllTables() },
            dirs = listOf(
                java.io.File(app.filesDir, "media"), java.io.File(app.filesDir, "offline-files"),
                java.io.File(app.cacheDir, "downloads"), java.io.File(app.cacheDir, "exports"),
            ),
            clearPrefs = WIPED_PREFS.map { name -> { app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit(); Unit } },
            exclusive = { block -> syncEngine.exclusive { block() } },
        )
    }

    /**
     * Sign-out the person asked for (More, Welcome, the no-account prompt): ends the session, then removes the company's
     * data from this phone and refreshes the clock widget (AC-F4). A 401 only ends the session, so unsent work survives
     * an expired token.
     */
    suspend fun signOutAndWipe() {
        auth.signOut()
        localWipe.run()
        runCatching { com.kurtz.homerecord.fieldlog.widget.ClockWidget().updateAll(app) }
    }

    fun signOut() {
        scope.launch(Dispatchers.IO) { signOutAndWipe() }
    }

    init {
        // A company left or signed out of starts from scratch next time (cr:sync-global-cursor).
        scope.launch { companyTenant.endedTenants().collect { syncEngine.forgetTenant(it) } }
    }

    /** Repositories call this after each write to a synced row; it queues the row and asks for a sync. */
    suspend fun recordChange(entity: String, id: String) {
        if (entity == "punch") runCatching { com.kurtz.homerecord.fieldlog.widget.ClockWidget().updateAll(app) }
        val tenant = company.current?.let { TenantRef(TenantKind.Company, it.companyId) } ?: return
        syncEngine.recordChange(tenant, entity, id)
        syncCoordinator.requestSync()
    }

    val punches by lazy { PunchRepository(db, onChange = ::recordChange) }

    val jobPhotos by lazy {
        com.kurtz.homerecord.core.photo.JobPhotos(
            db, queueUpload = ::uploadMedia, companyId = { company.current?.companyId }, onChange = ::recordChange,
        )
    }

    val jobNotes by lazy {
        com.kurtz.homerecord.core.photo.JobNotes(
            db, transcriber = com.kurtz.homerecord.fieldlog.capture.NoTranscriber, queueUpload = ::uploadMedia,
            companyId = { company.current?.companyId }, onChange = ::recordChange,
        )
    }

    val photoAnnotator by lazy {
        com.kurtz.homerecord.core.photo.PhotoAnnotator(
            db, com.kurtz.homerecord.fieldlog.photos.BitmapAnnotationRenderer, queueUpload = ::uploadMedia,
            companyId = { company.current?.companyId }, onChange = ::recordChange,
        )
    }

    val jobFiles by lazy { com.kurtz.homerecord.core.photo.JobFiles(db, onChange = ::recordChange) }

    /**
     * What this person may do, live: the role from the cached membership (a role change from the office lands on the
     * next /me refresh) and, for a Lead, the jobs they are on. Least privilege while no company is active (AC-F3).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val roleGates: kotlinx.coroutines.flow.Flow<com.kurtz.homerecord.core.company.RoleGates> by lazy {
        combine(
            company.state.map { (it as? CompanyState.Active)?.membership?.role ?: com.kurtz.homerecord.core.company.CompanyRole.Crew },
            auth.state.map { (it as? AuthState.SignedIn)?.token?.let(com.kurtz.homerecord.core.auth.JwtSubject::of) },
        ) { role, memberId -> role to memberId }
            .distinctUntilChanged()
            .flatMapLatest { (role, memberId) ->
                com.kurtz.homerecord.core.company.RoleGates.observe(role, memberId, db.jobCrewDao(), db.assignmentDao())
            }
    }

    fun filesDeps(jobId: String, authorId: String?) = com.kurtz.homerecord.fieldlog.files.FilesDeps(
        jobId, db.jobFileDao(), jobFiles, authorId,
        uploads = db.mediaUploadDao().observeAllFor("job_file").map { rows ->
            rows.associate { r ->
                r.entityId to when (r.state) {
                    "uploading" -> com.kurtz.homerecord.core.photo.UploadStatus.Uploading
                    "failed" -> com.kurtz.homerecord.core.photo.UploadStatus.Failed
                    "done" -> com.kurtz.homerecord.core.photo.UploadStatus.Uploaded
                    else -> com.kurtz.homerecord.core.photo.UploadStatus.Waiting
                }
            }
        },
        setOffline = jobFileTransfers::setAvailableOffline,
        canManage = roleGates.map { it.canManageFiles(jobId) },
        queueUpload = { f ->
            val path = f.path
            company.current?.companyId?.let { c ->
                if (path != null) uploadMedia(TenantRef(TenantKind.Company, c), "job_file", f.id, path.substringAfterLast('/'), path, f.mime)
            }
        },
    )

    val jobFileTransfers by lazy {
        com.kurtz.homerecord.core.photo.JobFileTransfers(
            db, mediaApi, mediaCache, java.io.File(app.filesDir, "offline-files"),
            tenant = { company.current?.companyId?.let { TenantRef(TenantKind.Company, it) } }, jobFiles = jobFiles,
        )
    }

    fun fileViewerDeps(fileId: String) = com.kurtz.homerecord.fieldlog.files.FileViewerDeps(
        fileId, db.jobFileDao(), ensureOnPhone = jobFileTransfers::ensureOnPhone,
    )

    val photoEdits by lazy { com.kurtz.homerecord.core.photo.PhotoEdits(db, onChange = ::recordChange) }

    fun photoDetailDeps(photoId: String) = com.kurtz.homerecord.fieldlog.photos.PhotoDetailDeps(
        photoId, db.photoDao(), db.jobDao(), db.roomDao(), photoEdits, roleGates,
    )

    fun annotateDeps(photoId: String) = com.kurtz.homerecord.fieldlog.photos.AnnotateDeps(
        photoId, db.photoDao(), photoAnnotator,
        aspectOf = { path ->
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(path, o)
                if (o.outWidth > 0 && o.outHeight > 0) o.outWidth.toFloat() / o.outHeight else null
            }
        },
    )

    fun captureDeps(context: Context, jobId: String): com.kurtz.homerecord.fieldlog.capture.CaptureDeps {
        val prefs = context.getSharedPreferences("capture", Context.MODE_PRIVATE)
        return com.kurtz.homerecord.fieldlog.capture.CaptureDeps(
            jobId = jobId, jobs = db.jobDao(), rooms = db.roomDao(), photos = jobPhotos,
            location = com.kurtz.homerecord.core.location.FusedLocationProvider(context),
            lastTags = { prefs.getString("room_$jobId", null) to prefs.getString("phase_$jobId", null) },
            rememberTags = { room, phase -> prefs.edit().putString("room_$jobId", room).putString("phase_$jobId", phase).apply() },
            notes = jobNotes, recorder = com.kurtz.homerecord.fieldlog.capture.AndroidVoiceRecorder(context),
            newMemoPath = { java.io.File(java.io.File(context.filesDir, "media").also { it.mkdirs() }, "${java.util.UUID.randomUUID()}.m4a").absolutePath },
            hasCameraPermission = { context.checkSelfPermission(android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED },
            appScope = scope,
        )
    }

    val approvals by lazy { com.kurtz.homerecord.core.punch.TimesheetApprovals(db, onChange = ::recordChange) }
    val crewClock by lazy {
        val loc = com.kurtz.homerecord.core.location.FusedLocationProvider(app)
        com.kurtz.homerecord.core.punch.CrewClock(punches, com.kurtz.homerecord.core.punch.ClockController(punches, loc), loc)
    }
    val clock by lazy { com.kurtz.homerecord.core.punch.ClockController(punches, com.kurtz.homerecord.core.location.FusedLocationProvider(app)) }

    /** What FL-Clock needs; the clock rules come with the cached membership, so every role has them offline too. */
    fun clockDeps(context: Context, membership: com.kurtz.homerecord.core.company.CompanyMembership, memberId: String): com.kurtz.homerecord.fieldlog.clock.ClockDeps {
        val prefs = context.getSharedPreferences("clock", Context.MODE_PRIVATE)
        val location = com.kurtz.homerecord.core.location.FusedLocationProvider(context)
        return com.kurtz.homerecord.fieldlog.clock.ClockDeps(
            companyId = membership.companyId, memberId = memberId,
            controller = com.kurtz.homerecord.core.punch.ClockController(punches, location), punches = punches,
            jobs = db.jobDao(), location = location,
            settings = company.settings,
            permissionAsked = { prefs.getBoolean("location_asked", false) },
            markPermissionAsked = { prefs.edit().putBoolean("location_asked", true).apply() },
            role = membership.role,
            crewClock = com.kurtz.homerecord.core.punch.CrewClock(punches, com.kurtz.homerecord.core.punch.ClockController(punches, location), location),
            members = db.memberDao().observeFor(membership.companyId),
            approvals = db.timesheetApprovalDao().observeFor(memberId),
            gates = roleGates,
        )
    }

    fun reportsDeps(membership: com.kurtz.homerecord.core.company.CompanyMembership): com.kurtz.homerecord.fieldlog.reports.ReportsDeps {
        val sharer = com.kurtz.homerecord.fieldlog.reports.TimeExportSharer(app)
        return com.kurtz.homerecord.fieldlog.reports.ReportsDeps(
            members = db.memberDao().observeFor(membership.companyId),
            punches = db.punchDao().observeFor(membership.companyId),
            jobs = db.jobDao().observeFor(membership.companyId),
            clients = db.clientDao().observeFor(membership.companyId),
            settings = company.settings,
            share = { sharer.share(sharer.write(it)) },
        )
    }

    fun timesheetsDeps(membership: com.kurtz.homerecord.core.company.CompanyMembership, memberId: String) =
        com.kurtz.homerecord.fieldlog.timesheets.TimesheetsDeps(
            callerId = memberId,
            callerName = { db.memberDao().get(memberId)?.name ?: membership.companyName },
            punchRepo = punches, approvals = approvals,
            members = db.memberDao().observeFor(membership.companyId),
            punches = db.punchDao().observeFor(membership.companyId),
            jobs = db.jobDao().observeFor(membership.companyId),
            approvalRows = db.timesheetApprovalDao().observeAll(),
            settings = company.settings,
        )

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
        com.kurtz.homerecord.core.media.MediaUploadWorker.provider = { mediaUploader }
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

    private companion object {
        /** This app's own preferences; `sync` (device id) and the token store are not company data. */
        val WIPED_PREFS = listOf("capture", "clock", "media", "blueprint_layers")
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
