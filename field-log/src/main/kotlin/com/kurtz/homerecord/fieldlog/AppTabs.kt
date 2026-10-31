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
    HomeRecordApp(tabs = tabs, tabRequests = container.tabRequests.flow, entries = { nav ->
        entry<PlaceholderKey> { key ->
            val title = titles[key.title] ?: key.title
            when {
                key.depth == 0 && key.title == "today" ->
                {
                    val phase by container.syncEngine.phase.collectAsState()
                    val pending by container.syncEngine.pendingCount.collectAsState(0)
                    val refused by container.syncEngine.refused.collectAsState(emptyList())
                    val clearRefused: () -> Unit = { scope.launch { container.syncEngine.clearRefused() } }
                    val token = (container.auth.state.collectAsState().value as? AuthState.SignedIn)?.token
                    val memberId = token?.let(com.kurtz.homerecord.core.auth.JwtSubject::of)
                    val navigation = com.kurtz.homerecord.fieldlog.today.TodayNavigation(
                        onInviteCrew = { nav.push(TeamKey) },
                        onSyncNow = container.syncCoordinator::requestSync,
                        onClearRefused = clearRefused,
                        onOpenClock = { nav.selectTab("clock") },
                        onOpenJob = { nav.push(JobKey(it)) },
                        onAddJob = { nav.push(JobEditKey(null)) },
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
                    onOpenTimesheets = { nav.push(TimesheetsKey) },
                    onOpenReports = { nav.push(ReportsKey) },
                    onOpenCompany = { nav.push(CompanyKey) },
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
                ),
                onEdit = { nav.push(JobEditKey(key.jobId)) }, onBack = nav::pop,
                onCapture = { nav.push(CaptureKey(key.jobId)) },
                onOpenPhoto = { nav.push(PhotoKey(it)) },
                onOpenFile = { nav.push(FileKey(it)) },
                downloads = container.mediaDownloads,
                logs = container.dailyLogs,
                onEditWeather = { day -> nav.push(LogWeatherKey(key.jobId, day.toString())) },
                files = container.filesDeps(
                    key.jobId,
                    (container.auth.state.collectAsState().value as? AuthState.SignedIn)?.token?.let(com.kurtz.homerecord.core.auth.JwtSubject::of),
                ),
                gates = container.roleGates,
            )
        }
        entry<PhotoKey> { key ->
            com.kurtz.homerecord.fieldlog.photos.PhotoDetailRoute(container.photoDetailDeps(key.photoId), onAnnotate = { nav.push(AnnotateKey(key.photoId)) }, onBack = nav::pop)
        }
        entry<FileKey> { key ->
            com.kurtz.homerecord.fieldlog.files.FileViewerRoute(
                container.fileViewerDeps(key.fileId), onBack = nav::pop,
                onMarkUp = { nav.push(MarkupKey(it)) }, onOpenVersion = { nav.push(FileKey(it)) },
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
            )
        }
        entry<JobRoomKey> { key ->
            com.kurtz.homerecord.housemodel.ui.RoomRoute(container.homes, key.roomId, onBack = nav::pop)
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
        entry<TimesheetsKey> {
            val token = (container.auth.state.collectAsState().value as? AuthState.SignedIn)?.token
            val memberId = token?.let(com.kurtz.homerecord.core.auth.JwtSubject::of)
            if (memberId == null) ErrorState(stringResource(R.string.clock_no_account), { container.signOut() }, Modifier)
            else com.kurtz.homerecord.fieldlog.timesheets.TimesheetsRoute({ container.timesheetsDeps(membership, memberId) }, onBack = nav::pop)
        }
        entry<ReportsKey> {
            com.kurtz.homerecord.fieldlog.reports.ReportsRoute({ container.reportsDeps(membership) }, onBack = nav::pop)
        }
        entry<CompanyKey> {
            CompanyRoute(container.companyApi, membership, onSaved = { scope.launch { container.company.refresh() } }, onBack = nav::pop)
        }
    })
}
