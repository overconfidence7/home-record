package com.kurtz.homerecord.housemanual.importing

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.auth.AuthRepository
import com.kurtz.homerecord.core.bundle.HandoffClaimApi
import com.kurtz.homerecord.coreui.auth.SignInScreen
import com.kurtz.homerecord.coreui.auth.SignInViewModel
import java.io.File

/**
 * Wires HM-Claim for the handoff link [token] (AC-H17, AC-B40): sign in if needed, claim, download into [cacheDir],
 * then [onReady] with the downloaded file so the import preview opens. [onClose] after Close or Back.
 */
@Composable
fun ClaimRoute(auth: AuthRepository, api: HandoffClaimApi, cacheDir: File, token: String, onReady: (Uri) -> Unit, onClose: () -> Unit) {
    val vm = viewModel<ClaimViewModel>(
        key = "claim:$token",
        factory = viewModelFactory {
            initializer { ClaimViewModel(token, auth.state, api, File(cacheDir, "handoff/claimed.homerecord")) }
        },
    )
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.phase) {
        if (state.phase == ClaimPhase.Ready) onReady(Uri.fromFile(vm.dest))
    }
    BackHandler(onBack = onClose)
    ClaimScreen(
        state,
        onEvent = { e -> if (e == ClaimEvent.Close) onClose() else vm.onEvent(e) },
        signIn = { modifier ->
            val signIn = viewModel<SignInViewModel>(
                key = "claim-sign-in",
                factory = viewModelFactory { initializer { SignInViewModel(auth, createSavedStateHandle()) } },
            )
            SignInScreen(signIn.state.collectAsState().value, signIn::onEvent, modifier)
        },
    )
}
