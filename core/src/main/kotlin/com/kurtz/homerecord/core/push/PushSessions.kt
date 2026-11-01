package com.kurtz.homerecord.core.push

import com.kurtz.homerecord.core.auth.AuthState
import com.kurtz.homerecord.core.auth.JwtSubject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.transform

private sealed interface Step {
    /** Signed in but offline: nothing can be sent yet. */
    data object Wait : Step

    data class Run(val userId: String?) : Step
}

/**
 * The values to hand [PushRegistrar.onSession]: the signed-in user once the phone is online (again after each
 * reconnect, so a register that failed offline is retried), and null on sign out. A refreshed session token for the
 * same user is not a new session, and the token store being read at cold start is not a sign out.
 */
fun pushSessions(auth: Flow<AuthState>, online: Flow<Boolean>): Flow<String?> =
    combine(auth, online) { state, isOnline ->
        when (state) {
            AuthState.Restoring -> null
            AuthState.SignedOut -> Step.Run(null)
            is AuthState.SignedIn -> JwtSubject.of(state.token)?.let { user -> if (isOnline) Step.Run(user) else Step.Wait }
        }
    }
        .filterNotNull()
        .distinctUntilChanged()
        .transform { step -> if (step is Step.Run) emit(step.userId) }
