package com.kurtz.homerecord.fieldlog

import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kurtz.homerecord.core.auth.AuthState
import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.company.CompanyState
import com.kurtz.homerecord.coreui.auth.AccountRoute
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.fieldlog.company.CompanyRoute
import com.kurtz.homerecord.fieldlog.company.TeamRoute
import com.kurtz.homerecord.fieldlog.company.WelcomeRoute
import com.kurtz.homerecord.fieldlog.company.failureText
import com.kurtz.homerecord.fieldlog.jobs.JobsRoute
import com.kurtz.homerecord.fieldlog.more.MoreRoute
import com.kurtz.homerecord.fieldlog.today.TodayScreen
import com.kurtz.homerecord.fieldlog.today.TodayUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.kurtz.homerecord.coreui.shell.AppTab
import com.kurtz.homerecord.coreui.shell.HomeRecordApp
import com.kurtz.homerecord.coreui.shell.PlaceholderDetail
import com.kurtz.homerecord.coreui.shell.PlaceholderKey
import com.kurtz.homerecord.coreui.shell.PlaceholderRoot

private data class TabDef(val id: String, val label: Int, val icon: ImageVector, val selected: ImageVector)

private fun tab(id: String, label: Int, icon: ImageVector, selected: ImageVector) = TabDef(id, label, icon, selected)

private val tabDefs = listOf(
        tab("today", R.string.tab_today, Icons.Outlined.Today, Icons.Rounded.Today),
        tab("jobs", R.string.tab_jobs, Icons.Outlined.Work, Icons.Rounded.Work),
        tab("clock", R.string.tab_clock, Icons.Outlined.Schedule, Icons.Rounded.Schedule),
        tab("schedule", R.string.tab_schedule, Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
        tab("more", R.string.tab_more, Icons.Outlined.MoreHoriz, Icons.Rounded.MoreHoriz),
)

/** Signed out, then no company, then the tabs: the three states of FL-Welcome and the app proper (AC-F1, AC-F4). */
@Composable
fun FieldLogApp(container: AppContainer) {
    val auth by container.auth.state.collectAsState()
    val company by container.company.state.collectAsState()
    val signedIn = auth is AuthState.SignedIn
    // Refreshed on every return to the app, so a role or clock-rule change the office made reaches this phone (AC-F18).
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(signedIn, lifecycle) {
        if (signedIn) lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { container.company.refresh() }
    }
    val state = company
    when {
        auth is AuthState.Restoring -> Scaffold { LoadingState(Modifier.padding(it)) }
        !signedIn -> Scaffold { AccountRoute(container.auth, Modifier.padding(it)) }
        state is CompanyState.Active -> {
            LaunchedEffect(Unit) { container.pendingJoinCode.value = null }
            FieldLogTabs(container, state.membership)
        }
        state is CompanyState.NoCompany -> Scaffold {
            val pending by container.pendingJoinCode.collectAsState()
            WelcomeRoute(container.company, pending, onSignOut = container::signOut, modifier = Modifier.padding(it))
        }
        state is CompanyState.Failed -> Scaffold {
            val scope = rememberCoroutineScope()
            ErrorState(failureText(state.failure), { scope.launch { container.company.refresh() } }, Modifier.padding(it))
        }
        else -> Scaffold { LoadingState(Modifier.padding(it)) }
    }
}

@Composable
private fun FieldLogTabs(container: AppContainer, membership: CompanyMembership) {
    val scope = rememberCoroutineScope()
    val tabs = tabDefs.map { AppTab(it.id, stringResource(it.label), it.icon, it.selected, PlaceholderKey(it.id)) }
    val titles = tabDefs.associate { it.id to stringResource(it.label) }
    PushOpener(container, membership)
    NotificationPermissionAsk(container)
    HomeRecordApp(tabs = tabs, tabRequests = container.tabRequests.flow, fullScreen = ::isFullScreen, entries = { nav ->
        entry<PlaceholderKey> { key ->
            val title = titles[key.title] ?: key.title
            when {
                key.depth == 0 && key.title == "today" ->
                {
                    val phase by container.syncEngine.phase.collectAsState()
                    val pending by container.syncEngine.pendingCount.collectAsState(0)
                    val refused by container.syncEngine.refused.collectAsState(emptyList())
                    val token = (container.auth.state.collectAsState().value as? AuthState.SignedIn)?.token
                    val memberId = token?.let(com.kurtz.homerecord.core.auth.JwtSubject::of)
                    val navigation = com.kurtz.homerecord.fieldlog.today.TodayNavigation(
                        onInviteCrew = { nav.push(TeamKey) },
                        onOpenSync = { nav.push(SyncLogKey) },
                        onOpenClock = { nav.selectTab("clock") },
                        onOpenJob = { nav.push(JobKey(it)) },
                        onAddJob = { nav.push(JobEditKey(null)) },
                        // The task's job opens on Tasks with the task over it: the form for those who manage the job's tasks,
                        // the task read only for Crew (AC-F34, R09g).
                        onOpenTask = { jobId, taskId ->
                            nav.push(JobKey(jobId, tab = com.kurtz.homerecord.fieldlog.jobs.JobTab.TASKS.name))
                            nav.push(TaskFormKey(jobId, taskId))
                        },
                        onCapture = { nav.push(CaptureKey(it)) },
                        onOpenPhoto = { nav.push(PhotoKey(it)) },
                    )
                    if (memberId == null) {
                        val context = LocalContext.current
                        TodayScreen(
                            TodayUiState(membership.companyName, membership.role, phase, pending, refused),
                            onEvent = { com.kurtz.homerecord.fieldlog.today.todayEvent(it, navigation, context) },
                        )
                    } else {
                        com.kurtz.homerecord.fieldlog.today.TodayRoute(
                            deps = { container.todayDeps(membership, memberId) },
                            syncPhase = phase, pending = pending, refused = refused, navigation = navigation,
                        )
                    }
                }
                key.depth == 0 && key.title == "more" -> MoreRoute(
                    deps = { container.moreDeps(membership) },
                    onOpenTeam = { nav.push(TeamKey) },
                    onOpenTimesheets = { nav.push(TimesheetsKey()) },
                    onOpenReports = { nav.push(ReportsKey) },
                    onOpenCompany = { nav.push(CompanyKey) },
                    onOpenSync = { nav.push(SyncLogKey) },
                    onSignOut = container::signOut,
                )
                key.depth == 0 && key.title == "jobs" -> JobsRoute(
                    membership.companyId, container.db.jobDao(), container.db.clientDao(), container.db.assignmentDao(), container.roleGates,
                    onEditJob = { if (it == null) nav.push(JobEditKey(null)) else nav.push(JobKey(it)) },
                )
                key.depth == 0 && key.title == "clock" -> {
                    val token = (container.auth.state.collectAsState().value as? AuthState.SignedIn)?.token
                    val memberId = token?.let(com.kurtz.homerecord.core.auth.JwtSubject::of)
                    if (memberId == null) ErrorState(stringResource(R.string.clock_no_account), { container.signOut() }, Modifier)
                    else com.kurtz.homerecord.fieldlog.clock.ClockRoute({ ctx -> container.clockDeps(ctx, membership, memberId) })
                }
                key.depth == 0 -> PlaceholderRoot(title, onOpenDetail = { nav.push(key.copy(depth = 1)) })
                else -> PlaceholderDetail(title, onBack = nav::pop, onOpenDetail = { nav.push(key.copy(depth = key.depth + 1)) })
            }
        }
        entry<TeamKey> {
            TeamRoute(container.companyApi, membership, container::inviteLink, onBack = nav::pop)
        }
        entry<JobKey> { key ->
            com.kurtz.homerecord.fieldlog.jobs.JobRoute(
                membership.companyId, key.jobId, container.db,
                com.kurtz.homerecord.fieldlog.jobs.JobHouseDeps(
                    container.homes, container.layers, container.jobHomes,
                    onEditPlan = { homeId, title, floorId -> nav.push(JobPlanKey(homeId, title, floorId)) },
                    onOpenRoom = { nav.push(JobRoomKey(it)) },
                    onEditShape = { homeId, title, floorId, room -> nav.push(JobPlanKey(homeId, title, floorId, room.id, editShape = true)) },
                    onOpenFloors = { homeId, title -> nav.push(JobFloorsKey(homeId, title)) },
                    on3D = { homeId, title, floorId -> nav.push(JobHouse3DKey(homeId, title, floorId)) },
                    overlays = container.overlays,
                    onBehindWall = { homeId, title -> nav.push(JobBehindWallKey(homeId, title)) },
                    onOpenOverlay = { homeId, title, o -> openJobOverlay(scope, container, nav, homeId, title, null, o) },
                ),
                onEdit = { nav.push(JobEditKey(key.jobId)) }, onBack = nav::pop,
                onCapture = { nav.push(CaptureKey(key.jobId)) },
                onOpenPhoto = { nav.push(PhotoKey(it)) },
                onOpenFile = { nav.push(FileKey(it)) },
                downloads = container.mediaDownloads,
                logs = container.dailyLogs,
                onEditWeather = { day -> nav.push(LogWeatherKey(key.jobId, day.toString())) },
                logSender = container.logSender,
                onPreviewLog = { day -> nav.push(LogPdfKey(key.jobId, day.toString())) },
                files = container.filesDeps(
                    key.jobId,
                    (container.auth.state.collectAsState().value as? AuthState.SignedIn)?.token?.let(com.kurtz.homerecord.core.auth.JwtSubject::of),
                ),
                gates = container.roleGates,
                initialTab = com.kurtz.homerecord.fieldlog.jobs.JobTab.entries.firstOrNull { it.name == key.tab } ?: com.kurtz.homerecord.fieldlog.jobs.JobTab.OVERVIEW,
                initialLogDay = key.day?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() },
                tasks = container.tasksDeps(
                    membership.companyId, key.jobId,
                    (container.auth.state.collectAsState().value as? AuthState.SignedIn)?.token?.let(com.kurtz.homerecord.core.auth.JwtSubject::of),
                ),
                onOpenTask = { nav.push(TaskFormKey(key.jobId, it)) },
                changes = container.changesDeps(key.jobId),
                onOpenChange = { nav.push(ChangeOrderKey(key.jobId, it)) },
            )
        }
        entry<PhotoKey> { key ->
            com.kurtz.homerecord.fieldlog.photos.PhotoDetailRoute(container.photoDetailDeps(key.photoId), onAnnotate = { nav.push(AnnotateKey(key.photoId)) }, onBack = nav::pop)
        }
        entry<FileKey> { key ->
            com.kurtz.homerecord.fieldlog.files.FileViewerRoute(
                container.fileViewerDeps(
                    key.fileId,
                    (container.auth.state.collectAsState().value as? AuthState.SignedIn)?.token?.let(com.kurtz.homerecord.core.auth.JwtSubject::of),
                ),
                onBack = nav::pop,
                onMarkUp = { nav.push(MarkupKey(it)) }, onOpenVersion = { nav.push(FileKey(it)) },
                onNewTask = { jobId, pin -> nav.push(TaskFormKey(jobId, fileId = pin.fileId, page = pin.page, x = pin.x, y = pin.y)) },
                onOpenTask = { jobId, taskId -> nav.push(TaskFormKey(jobId, taskId)) },
            )
        }
        entry<MarkupKey> { key ->
            val author = (container.auth.state.collectAsState().value as? AuthState.SignedIn)?.token?.let(com.kurtz.homerecord.core.auth.JwtSubject::of)
            com.kurtz.homerecord.fieldlog.files.MarkupRoute(
                container.markupDeps(key.fileId, author), onClose = nav::pop,
                // The new version replaces the one marked up: close the editor and that viewer, open the new version.
                onSaved = { id -> nav.pop(); nav.pop(); nav.push(FileKey(id)) },
            )
        }
        entry<LogWeatherKey> { key ->
            com.kurtz.homerecord.fieldlog.log.LogWeatherRoute(
                com.kurtz.homerecord.fieldlog.log.LogWeatherDeps(key.jobId, java.time.LocalDate.parse(key.day), container.dailyLogs, container.roleGates),
                onClose = nav::pop,
            )
        }
        entry<LogPdfKey> { key ->
            com.kurtz.homerecord.fieldlog.log.LogPreviewRoute(
                com.kurtz.homerecord.fieldlog.log.LogPreviewDeps(
                    key.jobId, java.time.LocalDate.parse(key.day), container.dailyLogs, container.logSender,
                    open = { com.kurtz.homerecord.coreui.pages.PageSources.open(it.path, "application/pdf") },
                ),
                onBack = nav::pop,
            )
        }
        entry<TaskFormKey> { key ->
            com.kurtz.homerecord.fieldlog.tasks.TaskFormRoute(container.taskFormDeps(
                    membership.companyId, key.jobId, key.taskId,
                    startFilePin = key.fileId?.let { com.kurtz.homerecord.core.model.TaskFilePin.of(it, key.page, key.x, key.y) },
                    me = (container.auth.state.collectAsState().value as? AuthState.SignedIn)?.token?.let(com.kurtz.homerecord.core.auth.JwtSubject::of),
                ), onClose = nav::pop)
        }
        entry<ChangeOrderKey> { key ->
            com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormRoute(
                container.changeOrderFormDeps(key.jobId, key.changeOrderId), onClose = nav::pop,
                onTakePhoto = { nav.push(CaptureKey(key.jobId)) },
                onSign = { id ->
                    nav.pop()
                    nav.push(ChangeOrderSignKey(key.jobId, id))
                },
                onOpenOrder = { id ->
                    nav.pop()
                    nav.push(ChangeOrderKey(key.jobId, id))
                },
            )
        }
        entry<ChangeOrderSignKey> { key ->
            com.kurtz.homerecord.fieldlog.changes.ChangeOrderSignRoute(
                container.changeOrderSignDeps(key.jobId, key.changeOrderId), onBack = nav::pop,
                onSigned = { id ->
                    nav.pop()
                    nav.push(ChangeOrderKey(key.jobId, id))
                },
            )
        }
        entry<AnnotateKey> { key ->
            com.kurtz.homerecord.fieldlog.photos.AnnotateRoute(container.annotateDeps(key.photoId), onBack = nav::pop)
        }
        entry<CaptureKey> { key ->
            com.kurtz.homerecord.fieldlog.capture.CaptureRoute(key.jobId, { ctx -> container.captureDeps(ctx, key.jobId) }, onBack = nav::pop)
        }
        entry<JobPlanKey> { key ->
            com.kurtz.homerecord.housemodel.ui.PlanEditorRoute(
                container.homes, key.homeId, key.title, onBack = nav::pop, onOpenFloors = { nav.push(JobFloorsKey(key.homeId, key.title)) },
                floorId = key.floorId, roomId = key.roomId, editShape = key.editShape,
                overlays = container.overlays, media = container.media, overlayStage = com.kurtz.homerecord.housemodel.walls.WallStage.BehindTheWall,
                overlayId = key.overlayId,
            )
        }
        entry<JobHouse3DKey> { key ->
            val sceneScope = androidx.compose.runtime.rememberCoroutineScope()
            com.kurtz.homerecord.housemodel.ui.House3DRoute(
                container.homes, container.wallPhotos, key.homeId, key.title, key.floorId, onBack = nav::pop,
                onEditPlan = { nav.push(JobPlanKey(key.homeId, key.title, it)) },
                defaultStage = com.kurtz.homerecord.housemodel.walls.WallStage.BehindTheWall,
                roomId = key.roomId,
                overlays = container.overlays,
                layerStore = container.layers,
                // A wall overlay opens its wall with it picked; a plan overlay the plan of its room's floor (AC-C23).
                onOpenOverlay = { o -> openJobOverlay(sceneScope, container, nav, key.homeId, key.title, key.floorId, o) },
                // Field Log has no item page yet (T062): an item marker opens the room it stands in.
                onOpenItem = { _, roomId -> nav.push(JobRoomKey(roomId)) },
            )
        }
        entry<JobRoomKey> { key ->
            val roomScope = androidx.compose.runtime.rememberCoroutineScope()
            com.kurtz.homerecord.housemodel.ui.RoomRoute(
                container.homes, container.wallPhotos, container.media, key.roomId, onBack = nav::pop,
                onOutline = { nav.push(JobWallOutlineKey(key.roomId, it.wallId, it.stage.name, it.photoPath)) },
                onView3D = { nav.push(JobHouse3DKey(it.homeId, it.homeName, it.floorId, it.roomId)) },
                overlays = container.overlays,
                onOpenWall = { nav.push(JobWallKey(key.roomId, it.wallId, it.stage.name, it.overlayId)) },
                onOpenPlanOverlay = { overlayId ->
                    roomScope.launch {
                        container.homes.roomLocation(key.roomId)?.let { nav.push(JobPlanKey(it.homeId, it.homeName, it.floorId, overlayId = overlayId)) }
                    }
                },
            )
        }
        entry<JobBehindWallKey> { key ->
            com.kurtz.homerecord.housemodel.ui.BehindWallRoute(
                container.homes, container.wallPhotos, container.media, key.homeId, key.title, onBack = nav::pop,
                onOutline = { roomId, it -> nav.push(JobWallOutlineKey(roomId, it.wallId, it.stage.name, it.photoPath)) },
                onOpenRoom = { nav.push(JobRoomKey(it)) },
                onEditPlan = { nav.push(JobPlanKey(key.homeId, key.title)) },
            )
        }
        entry<JobWallKey> { key ->
            com.kurtz.homerecord.housemodel.ui.WallOverlaysRoute(
                container.homes, container.wallPhotos, container.overlays, container.wallTextures, key.roomId, key.wallId,
                com.kurtz.homerecord.housemodel.walls.WallStage.fromStored(key.stage), key.overlayId, onBack = nav::pop, media = container.media,
            )
        }
        entry<JobWallOutlineKey> { key ->
            com.kurtz.homerecord.housemodel.ui.WallOutlineRoute(
                container.wallPhotos, container.wallTextures, container.media, key.roomId, key.wallId,
                com.kurtz.homerecord.housemodel.walls.WallStage.fromStored(key.stage), key.photoPath, onClose = nav::pop,
            )
        }
        entry<JobFloorsKey> { key ->
            com.kurtz.homerecord.housemodel.ui.FloorsRoute(container.homes, key.homeId, key.title, onBack = nav::pop)
        }
        entry<JobEditKey> { key ->
            com.kurtz.homerecord.fieldlog.jobs.JobEditRoute(
                membership.companyId, key.jobId, container.db.jobDao(), container.db.clientDao(), container.db.homeDao(), container.db.floorDao(), container.db.roomDao(),
                container.db.jobCrewDao(), container.db.memberDao(), { container.refreshMembers(membership.companyId) },
                container::recordChange, onClose = nav::pop,
                onSaved = { container.geocodePending(membership.companyId) },
            )
        }
        entry<TimesheetsKey> { key ->
            val token = (container.auth.state.collectAsState().value as? AuthState.SignedIn)?.token
            val memberId = token?.let(com.kurtz.homerecord.core.auth.JwtSubject::of)
            if (memberId == null) ErrorState(stringResource(R.string.clock_no_account), { container.signOut() }, Modifier)
            else com.kurtz.homerecord.fieldlog.timesheets.TimesheetsRoute({ container.timesheetsDeps(membership, memberId, key.week?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }) }, onBack = nav::pop)
        }
        entry<ReportsKey> {
            com.kurtz.homerecord.fieldlog.reports.ReportsRoute(
                { container.reportsDeps(membership) }, onBack = nav::pop,
                onOpenLog = { jobId, day -> nav.push(JobKey(jobId, com.kurtz.homerecord.fieldlog.jobs.JobTab.LOG.name, day.toString())) },
            )
        }
        entry<SyncLogKey> {
            com.kurtz.homerecord.fieldlog.sync.SyncLogRoute({ container.syncLogDeps() }, onBack = nav::pop)
        }
        entry<CompanyKey> {
            CompanyRoute(container.companyApi, membership, onSaved = { scope.launch { container.company.refresh() } }, onBack = nav::pop)
        }
    })
}

/** Opens the screen a tapped push notification is about, once this company's tabs are composed (T047b2). */
@Composable
private fun PushOpener(container: AppContainer, membership: CompanyMembership) {
    LaunchedEffect(membership.companyId) {
        container.pendingPush.filterNotNull().collect { message ->
            val gates = container.roleGates.first()
            val request = com.kurtz.homerecord.fieldlog.push.PushDestination.of(message, membership.companyId, gates)
            container.pendingPush.value = null
            // the row the push is about may not be on this phone yet (R09)
            container.syncCoordinator.requestSync()
            container.tabRequests.request(request.tabId, request.keys)
        }
    }
}

/** Asks for the notification permission the first time the person reaches the tabs (Android 13+, push set up). */
@Composable
private fun NotificationPermissionAsk(container: AppContainer) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val ask = withContext(Dispatchers.IO) {
            container.shouldAskNotifications().also { if (it) container.markNotificationsAsked() }
        }
        if (ask) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}

/** A tapped overlay (3D or blueprint): a wall overlay opens its wall with it picked, a plan overlay the job plan of its floor (AC-C23). */
private fun openJobOverlay(
    scope: kotlinx.coroutines.CoroutineScope,
    container: AppContainer,
    nav: com.kurtz.homerecord.coreui.shell.ShellNav,
    homeId: String,
    title: String,
    floorId: String?,
    o: com.kurtz.homerecord.housemodel.overlays.Overlay,
) {
    val wallId = o.wallId
    val roomId = o.roomId
    if (wallId != null && roomId != null) {
        nav.push(JobWallKey(roomId, wallId, o.stage.name, o.id))
    } else {
        scope.launch {
            val floor = roomId?.let { container.homes.roomLocation(it)?.floorId } ?: floorId
            nav.push(JobPlanKey(homeId, title, floor, overlayId = o.id))
        }
    }
}
