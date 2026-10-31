package com.kurtz.homerecord.coreui.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.auth.AuthRepository
import com.kurtz.homerecord.core.auth.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Email and step survive process death through [SavedStateHandle]; the code is never saved.
 * The ViewModel outlives a sign-in (it is scoped to the activity), so a successful sign-in resets it to an
 * empty email step: the next sign-out must not reopen on the last person's code step.
 */
class SignInViewModel(private val repo: AuthRepository, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val _state = MutableStateFlow(
        SignInUiState(
            step = saved.get<String>(STEP)?.let(SignInStep::valueOf) ?: SignInStep.Email,
            email = saved.get<String>(EMAIL).orEmpty(),
        ),
    )
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun onEvent(event: SignInEvent) {
        when (event) {
            is SignInEvent.EmailChanged -> update { copy(email = event.value, error = null) }
            is SignInEvent.CodeChanged -> update { copy(code = event.value.filter(Char::isDigit).take(6), error = null) }
            SignInEvent.ChangeEmail -> update { copy(step = SignInStep.Email, code = "", error = null) }
            SignInEvent.SendCode -> run { if (emailValid) send() }
            SignInEvent.VerifyCode -> run { if (codeValid) verify() }
            SignInEvent.GoogleSignIn -> launchCall { finish(repo.signInWithGoogle()) }
        }
    }

    private fun run(block: SignInUiState.() -> Unit) = _state.value.takeIf { !it.busy }?.block()

    private fun send() = launchCall {
        val result = repo.requestEmailCode(_state.value.email)
        update { copy(busy = false, step = if (result == AuthResult.Success) SignInStep.Code else step, error = result.takeIf { it != AuthResult.Success }) }
    }

    private fun verify() = launchCall { finish(repo.verifyEmailCode(_state.value.email, _state.value.code)) }

    private fun finish(result: AuthResult) =
        if (result == AuthResult.Success) {
            update { SignInUiState(signedIn = true) }
        } else {
            update { copy(busy = false, signedIn = false, error = result) }
        }

    private fun launchCall(block: suspend () -> Unit) {
        if (_state.value.busy) return
        update { copy(busy = true, error = null) }
        viewModelScope.launch { block() }
    }

    private fun update(f: SignInUiState.() -> SignInUiState) {
        _state.update(f)
        saved[STEP] = _state.value.step.name
        saved[EMAIL] = _state.value.email
    }

    private companion object {
        const val STEP = "step"
        const val EMAIL = "email"
    }
}
