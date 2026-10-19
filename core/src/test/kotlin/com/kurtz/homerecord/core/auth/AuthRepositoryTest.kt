package com.kurtz.homerecord.core.auth

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakeStore(var token: String? = null) : TokenStore {
    override fun read() = token
    override fun write(token: String) { this.token = token }
    override fun clear() { token = null }
}

private class FakeApi : AuthApi {
    var verify = TokenOrError("tok-1")
    var google = TokenOrError("tok-g")
    override suspend fun startEmailCode(email: String) = AuthResult.Success
    override suspend fun verifyEmailCode(email: String, code: String) = verify
    override suspend fun signInWithGoogle(idToken: String) = google
}

private class FakeGoogle(var token: String? = "id-token") : GoogleCredentialSource {
    override suspend fun idToken() = token
}

class AuthRepositoryTest {
    private val api = FakeApi()
    private val store = FakeStore()
    private val google = FakeGoogle()
    private fun repo() = AuthRepository(api, store, google)

    @Test fun startsSignedOutWithoutToken() {
        assertThat(repo().state.value).isEqualTo(AuthState.SignedOut)
    }

    @Test fun signInPersistsAcrossRestart() = runTest {
        assertThat(repo().verifyEmailCode(" a@b.com ", " 123456 ")).isEqualTo(AuthResult.Success)
        assertThat(store.token).isEqualTo("tok-1")
        assertThat(repo().state.value).isEqualTo(AuthState.SignedIn("tok-1"))
    }

    @Test fun wrongCodeStaysSignedOut() = runTest {
        api.verify = TokenOrError(null, AuthResult.InvalidCode)
        val r = repo()
        assertThat(r.verifyEmailCode("a@b.com", "000000")).isEqualTo(AuthResult.InvalidCode)
        assertThat(r.state.value).isEqualTo(AuthState.SignedOut)
        assertThat(store.token).isNull()
    }

    @Test fun googleSignIn() = runTest {
        val r = repo()
        assertThat(r.signInWithGoogle()).isEqualTo(AuthResult.Success)
        assertThat(r.state.value).isEqualTo(AuthState.SignedIn("tok-g"))
    }

    @Test fun cancelledGoogleSignInFails() = runTest {
        google.token = null
        val r = repo()
        assertThat(r.signInWithGoogle()).isEqualTo(AuthResult.Failed)
        assertThat(r.state.value).isEqualTo(AuthState.SignedOut)
    }

    @Test fun refreshedTokenReplacesStoredOne() = runTest {
        val r = repo()
        r.verifyEmailCode("a@b.com", "123456")
        r.onTokenRefreshed("tok-2")
        assertThat(store.token).isEqualTo("tok-2")
        assertThat(r.state.value).isEqualTo(AuthState.SignedIn("tok-2"))
    }

    @Test fun refreshWhileSignedOutIsIgnored() {
        val r = repo()
        r.onTokenRefreshed("tok-2")
        assertThat(store.token).isNull()
    }

    @Test fun signOutAndUnauthorizedClearToken() = runTest {
        val r = repo()
        r.verifyEmailCode("a@b.com", "123456")
        r.onUnauthorized()
        assertThat(store.token).isNull()
        assertThat(r.state.value).isEqualTo(AuthState.SignedOut)
    }
}
