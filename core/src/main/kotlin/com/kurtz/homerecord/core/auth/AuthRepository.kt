package com.kurtz.homerecord.core.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AuthState {
    /** The token store is still being read off the main thread (cold start). */
    data object Restoring : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val token: String) : AuthState
}

sealed interface AuthResult {
    data object Success : AuthResult
    data object InvalidCode : AuthResult
    data object CodeExpired : AuthResult
    data object TooManyAttempts : AuthResult
    data object Offline : AuthResult
    data object Failed : AuthResult
}

/** Network side of sign-in; the Retrofit implementation lands with the sync client. */
interface AuthApi {
    suspend fun startEmailCode(email: String): AuthResult
    suspend fun verifyEmailCode(email: String, code: String): TokenOrError
    suspend fun signInWithGoogle(idToken: String): TokenOrError
}

data class TokenOrError(val token: String?, val error: AuthResult = AuthResult.Failed)

/** Persists the session token; backed by EncryptedSharedPreferences on device. */
interface TokenStore {
    fun read(): String?
    fun write(token: String)
    fun clear()
}

/** Wraps Credential Manager: returns a Google ID token, or null if the user cancelled. */
interface GoogleCredentialSource {
    suspend fun idToken(): String?
}

/** Session lifecycle shared by both apps (AC-B1, AC-F4): sign-in persists across restarts, sign-out clears the token. */
class AuthRepository(
    private val api: AuthApi,
    private val store: TokenStore,
    private val google: GoogleCredentialSource,
    restoreNow: Boolean = true,
) {
    private val _state = MutableStateFlow<AuthState>(if (restoreNow) stored() else AuthState.Restoring)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private fun stored(): AuthState = store.read()?.let { AuthState.SignedIn(it) } ?: AuthState.SignedOut

    /** Reads the stored token; call off the main thread when built with restoreNow = false. */
    fun restore() {
        _state.compareAndSet(AuthState.Restoring, stored())
    }

    suspend fun requestEmailCode(email: String): AuthResult = api.startEmailCode(email.trim())

    suspend fun verifyEmailCode(email: String, code: String): AuthResult =
        complete(api.verifyEmailCode(email.trim(), code.trim()))

    suspend fun signInWithGoogle(): AuthResult {
        val idToken = google.idToken() ?: return AuthResult.Failed
        return complete(api.signInWithGoogle(idToken))
    }

    /** Called when the server sends X-Refreshed-Token. */
    fun onTokenRefreshed(token: String) {
        if (_state.value is AuthState.SignedIn) save(token)
    }

    /** Called on a 401: the session is gone. */
    fun onUnauthorized() = signOut()

    private val signOutListeners = mutableListOf<() -> Unit>()

    /** Lets other repositories drop their cached data when the session ends (AC-F4). */
    fun addSignOutListener(listener: () -> Unit) {
        signOutListeners += listener
    }

    fun signOut() {
        store.clear()
        signOutListeners.forEach { it() }
        _state.value = AuthState.SignedOut
    }

    private fun complete(r: TokenOrError): AuthResult {
        val token = r.token ?: return r.error
        save(token)
        return AuthResult.Success
    }

    private fun save(token: String) {
        store.write(token)
        _state.value = AuthState.SignedIn(token)
    }
}
