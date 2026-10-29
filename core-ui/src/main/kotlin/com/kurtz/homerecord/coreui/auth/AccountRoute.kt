package com.kurtz.homerecord.coreui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kurtz.homerecord.core.auth.AuthRepository
import com.kurtz.homerecord.core.auth.AuthState
import com.kurtz.homerecord.coreui.R
import com.kurtz.homerecord.coreui.components.LoadingState

/**
 * Sign-in when signed out, account summary with sign out when signed in. [syncStatus] shows under the summary while
 * signed in (House Manual's sync chip, refused changes included).
 */
@Composable
fun AccountRoute(repo: AuthRepository, modifier: Modifier = Modifier, syncStatus: @Composable () -> Unit = {}) {
    val auth = repo.state.collectAsState().value
    if (auth is AuthState.Restoring) {
        LoadingState(modifier)
    } else if (auth is AuthState.SignedIn) {
        Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.account_signed_in), style = MaterialTheme.typography.titleMedium)
            syncStatus()
            Text(stringResource(R.string.account_sign_out_note), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = repo::signOut, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.account_sign_out))
            }
        }
    } else {
        val vm = viewModel<SignInViewModel>(factory = viewModelFactory { initializer { SignInViewModel(repo, createSavedStateHandle()) } })
        SignInScreen(vm.state.collectAsState().value, vm::onEvent, modifier)
    }
}
