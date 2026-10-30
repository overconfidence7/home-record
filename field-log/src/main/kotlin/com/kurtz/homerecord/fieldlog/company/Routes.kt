package com.kurtz.homerecord.fieldlog.company

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.company.CompanyApi
import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.company.CompanyRepository
import com.kurtz.homerecord.coreui.platform.Intents
import com.kurtz.homerecord.fieldlog.R
import kotlinx.coroutines.delay

/** How often FL-Team re-reads the member list while it is on screen, so a new joiner shows up inside 30 s (AC-F2). */
private const val TEAM_POLL_MS = 15_000L

@Composable
fun WelcomeRoute(repo: CompanyRepository, initialCode: String?, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val vm = viewModel<WelcomeViewModel>(
        factory = viewModelFactory { initializer { WelcomeViewModel(repo, onSignOut, createSavedStateHandle(), initialCode) } },
    )
    WelcomeScreen(vm.state.collectAsState().value, vm::onEvent, modifier)
}

@Composable
fun TeamRoute(api: CompanyApi, membership: CompanyMembership, inviteLinkFor: (String) -> String, onBack: () -> Unit) {
    val vm = viewModel<TeamViewModel>(factory = viewModelFactory { initializer { TeamViewModel(api, membership, inviteLinkFor) } })
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(vm, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(TEAM_POLL_MS)
                vm.onEvent(TeamEvent.Refresh)
            }
        }
    }
    val context = LocalContext.current
    val shareChooser = stringResource(R.string.team_invite_share)
    TeamScreen(
        state = vm.state.collectAsState().value,
        onEvent = vm::onEvent,
        onShareInvite = { code, link -> shareInvite(context, membership.companyName, code, link, shareChooser) },
        onBack = onBack,
    )
}

@Composable
fun CompanyRoute(api: CompanyApi, membership: CompanyMembership, onSaved: (String) -> Unit, onBack: () -> Unit) {
    val vm = viewModel<CompanyViewModel>(
        factory = viewModelFactory { initializer { CompanyViewModel(api, membership.companyId, createSavedStateHandle(), onSaved) } },
    )
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.saved) { if (state.saved) onBack() }
    CompanyScreen(state, vm::onEvent, onClose = onBack)
}

private fun shareInvite(context: Context, company: String, code: String, link: String, chooserTitle: String) {
    val text = context.getString(R.string.team_invite_message, company, code, link)
    Intents.shareText(context, text, chooserTitle)
}

