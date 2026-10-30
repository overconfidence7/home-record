package com.kurtz.homerecord.coreui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.auth.AuthRepository
import com.kurtz.homerecord.core.auth.AuthState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The account panel: sign-out asks first; the open dialog survives rotation and process death. */
class AccountViewModel(
    private val repo: AuthRepository,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val confirm = saved.getStateFlow(CONFIRM, false)

    val state: StateFlow<AccountUiState> = combine(repo.state, confirm, ::toState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, toState(repo.state.value, confirm.value))

    fun onEvent(event: AccountEvent) {
        when (event) {
            AccountEvent.SignOut -> saved[CONFIRM] = true
            AccountEvent.DismissSignOut -> saved[CONFIRM] = false
            AccountEvent.ConfirmSignOut -> {
                saved[CONFIRM] = false
                repo.signOut()
            }
        }
    }

    private fun toState(auth: AuthState, confirmOpen: Boolean) = when (auth) {
        AuthState.Restoring -> AccountUiState(AccountPhase.Restoring)
        AuthState.SignedOut -> AccountUiState(AccountPhase.SignedOut)
        is AuthState.SignedIn -> AccountUiState(AccountPhase.SignedIn, confirmSignOut = confirmOpen)
    }

    private companion object {
        const val CONFIRM = "confirmSignOut"
    }
}
