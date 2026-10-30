package com.kurtz.homerecord.coreui.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.auth.AuthRepository

/** Sign-in when signed out, [AccountScreen] when signed in. [syncStatus] shows under the account summary. */
@Composable
fun AccountRoute(repo: AuthRepository, modifier: Modifier = Modifier, syncStatus: @Composable () -> Unit = {}) {
    val account = viewModel<AccountViewModel>(factory = viewModelFactory { initializer { AccountViewModel(repo, createSavedStateHandle()) } })
    val state = account.state.collectAsState().value
    if (state.phase == AccountPhase.SignedOut) {
        val vm = viewModel<SignInViewModel>(factory = viewModelFactory { initializer { SignInViewModel(repo, createSavedStateHandle()) } })
        SignInScreen(vm.state.collectAsState().value, vm::onEvent, modifier)
    } else {
        AccountScreen(state, account::onEvent, modifier, syncStatus)
    }
}
