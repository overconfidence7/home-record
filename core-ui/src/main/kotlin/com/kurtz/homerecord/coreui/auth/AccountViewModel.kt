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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.kurtz.homerecord.core.company.ApiResult

/** The account panel: sign-out asks first; the open dialog survives rotation and process death. */
class AccountViewModel(
    private val repo: AuthRepository,
    private val saved: SavedStateHandle = SavedStateHandle(),
    /** The signed-in email (`GET /me`); a failure leaves the panel without it. */
    private val loadEmail: (suspend () -> ApiResult<String>)? = null,
) : ViewModel() {
    private val confirm = saved.getStateFlow(CONFIRM, false)
    private val email = MutableStateFlow<String?>(null)

    val state: StateFlow<AccountUiState> = combine(repo.state, confirm, email, ::toState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, toState(repo.state.value, confirm.value, null))

    init {
        // Each new session asks once who it is; signing out forgets the email.
        viewModelScope.launch {
            repo.state.map { (it as? AuthState.SignedIn)?.token != null }.distinctUntilChanged().collectLatest { signedIn ->
                email.value = null
                val load = loadEmail
                if (signedIn && load != null) email.value = (load() as? ApiResult.Ok)?.value?.takeIf { it.isNotBlank() }
            }
        }
    }

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

    private fun toState(auth: AuthState, confirmOpen: Boolean, email: String?) = when (auth) {
        AuthState.Restoring -> AccountUiState(AccountPhase.Restoring)
        AuthState.SignedOut -> AccountUiState(AccountPhase.SignedOut)
        is AuthState.SignedIn -> AccountUiState(AccountPhase.SignedIn, confirmSignOut = confirmOpen, email = email)
    }

    private companion object {
        const val CONFIRM = "confirmSignOut"
    }
}
