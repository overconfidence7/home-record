package com.kurtz.homerecord.housemanual.share

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.auth.AuthState
import com.kurtz.homerecord.housemanual.AppContainer
import com.kurtz.homerecord.housemanual.R
import kotlinx.coroutines.flow.map

/** Wires HM-Share for [homeId]: each new link opens the share sheet; Sign in opens HM-Account. */
@Composable
fun ShareRoute(container: AppContainer, homeId: String, homeName: String, onBack: () -> Unit, onSignIn: () -> Unit) {
    val vm = viewModel<ShareViewModel>(
        key = "share:$homeId",
        factory = viewModelFactory {
            initializer { ShareViewModel(homeId, container.homeShare, container.auth.state.map { it is AuthState.SignedIn }) }
        },
    )
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val resources = androidx.compose.ui.platform.LocalResources.current
    val chooserTitle = stringResource(R.string.share_sheet_title)
    val subject = stringResource(R.string.share_sheet_subject, homeName)
    LaunchedEffect(vm) {
        vm.links.collect { link ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, resources.getString(R.string.share_sheet_text, homeName, link))
            }
            context.startActivity(Intent.createChooser(send, chooserTitle))
        }
    }
    ShareScreen(state, onEvent = { event ->
        when (event) {
            ShareEvent.Back -> onBack()
            ShareEvent.SignIn -> onSignIn()
            else -> vm.onEvent(event)
        }
    })
}

/**
 * Wires HM-Join for the invite [token] (`homerecord://invite/<token>`): sign in if needed, accept, pull the shared home
 * down on a phone that has none. [onClose] after Close, Back, or once the home is this phone's.
 */
@Composable
fun JoinRoute(container: AppContainer, token: String, onClose: () -> Unit) {
    val vm = viewModel<JoinViewModel>(
        key = "join:$token",
        factory = viewModelFactory {
            initializer {
                JoinViewModel(token, container.auth.state, { t ->
                    container.homeShare.accept(t).also { r ->
                        if (r is com.kurtz.homerecord.core.share.AcceptResult.Joined) container.homeAccess.remember(r.home.homeId, r.home.role)
                    }
                }, { container.cloudRestore.restore(it) }, container::localHomeId)
            }
        },
    )
    val state by vm.state.collectAsState()
    LaunchedEffect(state.phase) { if (state.phase == JoinPhase.Done) onClose() }
    BackHandler(onBack = onClose)
    JoinScreen(state, onEvent = { e -> if (e == JoinEvent.Close) onClose() else vm.onEvent(e) }) { modifier ->
        com.kurtz.homerecord.coreui.auth.AccountRoute(container.auth, modifier)
    }
}
