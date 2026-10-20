package com.kurtz.homerecord.fieldlog

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
import com.kurtz.homerecord.fieldlog.more.MoreEvent
import com.kurtz.homerecord.fieldlog.more.MoreScreen
import com.kurtz.homerecord.fieldlog.more.MoreUiState
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
    LaunchedEffect(signedIn) { if (signedIn) container.company.refresh() }
    val state = company
    when {
        !signedIn -> Scaffold { AccountRoute(container.auth, Modifier.padding(it)) }
        state is CompanyState.Active -> {
            LaunchedEffect(Unit) { container.pendingJoinCode.value = null }
            FieldLogTabs(container, state.membership)
        }
        state is CompanyState.NoCompany -> Scaffold {
            val pending by container.pendingJoinCode.collectAsState()
            WelcomeRoute(container.company, pending, onSignOut = container.auth::signOut, modifier = Modifier.padding(it))
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
    HomeRecordApp(tabs = tabs, entries = { nav ->
        entry<PlaceholderKey> { key ->
            val title = titles[key.title] ?: key.title
            when {
                key.depth == 0 && key.title == "today" ->
                {
                    val phase by container.syncEngine.phase.collectAsState()
                    val pending by container.syncEngine.pendingCount.collectAsState(0)
                    TodayScreen(
                        TodayUiState(membership.companyName, membership.role, phase, pending),
                        onInviteCrew = { nav.push(TeamKey) },
                        onSyncNow = container.syncCoordinator::requestSync,
                    )
                }
                key.depth == 0 && key.title == "more" -> MoreScreen(
                    MoreUiState(membership.companyName, membership.role),
                    onEvent = { event ->
                        when (event) {
                            MoreEvent.OpenTeam -> nav.push(TeamKey)
                            MoreEvent.OpenCompany -> nav.push(CompanyKey)
                            MoreEvent.SignOut -> container.auth.signOut()
                        }
                    },
                )
                key.depth == 0 -> PlaceholderRoot(title, onOpenDetail = { nav.push(key.copy(depth = 1)) })
                else -> PlaceholderDetail(title, onBack = nav::pop, onOpenDetail = { nav.push(key.copy(depth = key.depth + 1)) })
            }
        }
        entry<TeamKey> {
            TeamRoute(container.companyApi, membership, container::inviteLink, onBack = nav::pop)
        }
        entry<CompanyKey> {
            CompanyRoute(container.companyApi, membership, onSaved = { scope.launch { container.company.refresh() } }, onBack = nav::pop)
        }
    })
}
