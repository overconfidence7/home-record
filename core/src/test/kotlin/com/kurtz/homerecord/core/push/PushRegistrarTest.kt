package com.kurtz.homerecord.core.push

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

// AC-B30: the phone's FCM token is registered per member and follows sign in, token refresh and sign out.
class PushRegistrarTest {
    private class FakeApi : PushTokenApi {
        val calls = mutableListOf<String>()
        var offline = false
        override suspend fun register(token: String, app: String): ApiResult<Unit> {
            calls += "put $token $app"
            return if (offline) ApiResult.Err(ApiFailure.Offline) else ApiResult.Ok(Unit)
        }
        override suspend fun unregister(token: String): ApiResult<Unit> {
            calls += "delete $token"
            return ApiResult.Ok(Unit)
        }
    }

    private class MemoryStore : PushRegistrationStore {
        var saved: PushRegistration? = null
        override fun read() = saved
        override fun write(registration: PushRegistration?) { saved = registration }
    }

    private val api = FakeApi()
    private val store = MemoryStore()
    private var token: String? = "tok-1"
    private val registrar = PushRegistrar(api, { token }, store, "field-log")

    @Test
    fun signInRegistersTheTokenForFieldLog() = runTest {
        registrar.onSession("u-ana")
        assertThat(api.calls).containsExactly("put tok-1 field-log")
        assertThat(store.saved).isEqualTo(PushRegistration("tok-1", "u-ana"))
    }

    @Test
    fun sameTokenAndUserIsNotSentAgain() = runTest {
        registrar.onSession("u-ana")
        registrar.onSession("u-ana")
        assertThat(api.calls).hasSize(1)
    }

    @Test
    fun anotherAccountOnTheSamePhoneRegistersAgain() = runTest {
        registrar.onSession("u-ana")
        registrar.onSession("u-ben")
        assertThat(api.calls).containsExactly("put tok-1 field-log", "put tok-1 field-log")
        assertThat(store.saved?.userId).isEqualTo("u-ben")
    }

    @Test
    fun failedRegisterIsRetriedOnTheNextSession() = runTest {
        api.offline = true
        registrar.onSession("u-ana")
        assertThat(store.saved).isNull()
        api.offline = false
        registrar.onSession("u-ana")
        assertThat(store.saved).isEqualTo(PushRegistration("tok-1", "u-ana"))
    }

    @Test
    fun refreshedTokenIsRegisteredWhileSignedIn() = runTest {
        registrar.onSession("u-ana")
        registrar.onNewToken("tok-2")
        assertThat(api.calls.last()).isEqualTo("put tok-2 field-log")
        assertThat(store.saved).isEqualTo(PushRegistration("tok-2", "u-ana"))
    }

    @Test
    fun refreshedTokenWhileSignedOutWaitsForSignIn() = runTest {
        registrar.onNewToken("tok-2")
        assertThat(api.calls).isEmpty()
    }

    @Test
    fun explicitSignOutRemovesTheToken() = runTest {
        registrar.onSession("u-ana")
        registrar.unregister()
        assertThat(api.calls.last()).isEqualTo("delete tok-1")
        assertThat(store.saved).isNull()
        registrar.onNewToken("tok-2")
        assertThat(api.calls.last()).isEqualTo("delete tok-1")
    }

    @Test
    fun endedSessionForgetsLocallyWithoutCallingTheServer() = runTest {
        registrar.onSession("u-ana")
        registrar.onSession(null)
        assertThat(api.calls).containsExactly("put tok-1 field-log")
        assertThat(store.saved).isNull()
    }

    @Test
    fun noFirebaseConfigMeansNothingIsSent() = runTest {
        token = null
        registrar.onSession("u-ana")
        assertThat(api.calls).isEmpty()
    }
}

// AC-B30: when the registrar runs, from the session and the network.
class PushSessionsTest {
    private fun jwt(sub: String): String {
        val enc = java.util.Base64.getUrlEncoder().withoutPadding()
        return "h." + enc.encodeToString("""{"sub":"$sub","v":1}""".toByteArray()) + ".s"
    }

    @Test
    fun registersOnceOnlineAndAgainAfterAReconnect() = runTest {
        val auth = kotlinx.coroutines.flow.MutableStateFlow<com.kurtz.homerecord.core.auth.AuthState>(com.kurtz.homerecord.core.auth.AuthState.Restoring)
        val online = kotlinx.coroutines.flow.MutableStateFlow(false)
        val seen = mutableListOf<String?>()
        val job = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { pushSessions(auth, online).collect { seen += it } }
        auth.value = com.kurtz.homerecord.core.auth.AuthState.SignedIn(jwt("u-ana"))
        assertThat(seen).isEmpty()
        online.value = true
        auth.value = com.kurtz.homerecord.core.auth.AuthState.SignedIn(jwt("u-ana") + "x")
        online.value = false
        online.value = true
        auth.value = com.kurtz.homerecord.core.auth.AuthState.SignedOut
        assertThat(seen).containsExactly("u-ana", "u-ana", null).inOrder()
        job.cancel()
    }

    @Test
    fun coldStartRestoreIsNotASignOut() = runTest {
        val auth = kotlinx.coroutines.flow.MutableStateFlow<com.kurtz.homerecord.core.auth.AuthState>(com.kurtz.homerecord.core.auth.AuthState.Restoring)
        val seen = mutableListOf<String?>()
        val job = launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            pushSessions(auth, kotlinx.coroutines.flow.MutableStateFlow(true)).collect { seen += it }
        }
        assertThat(seen).isEmpty()
        auth.value = com.kurtz.homerecord.core.auth.AuthState.SignedIn(jwt("u-ben"))
        assertThat(seen).containsExactly("u-ben")
        job.cancel()
    }
}
