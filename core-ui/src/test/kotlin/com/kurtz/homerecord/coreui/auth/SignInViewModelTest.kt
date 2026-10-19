package com.kurtz.homerecord.coreui.auth

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.auth.AuthApi
import com.kurtz.homerecord.core.auth.AuthRepository
import com.kurtz.homerecord.core.auth.AuthResult
import com.kurtz.homerecord.core.auth.GoogleCredentialSource
import com.kurtz.homerecord.core.auth.TokenOrError
import com.kurtz.homerecord.core.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {
    private var start: AuthResult = AuthResult.Success
    private var verify = TokenOrError("tok")
    private val api = object : AuthApi {
        override suspend fun startEmailCode(email: String) = start
        override suspend fun verifyEmailCode(email: String, code: String) = verify
        override suspend fun signInWithGoogle(idToken: String) = TokenOrError("g")
    }
    private val store = object : TokenStore {
        var t: String? = null
        override fun read() = t
        override fun write(token: String) { t = token }
        override fun clear() { t = null }
    }
    private val google = object : GoogleCredentialSource { override suspend fun idToken(): String? = null }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = SignInViewModel(AuthRepository(api, store, google), saved)

    @Test fun invalidEmailDoesNotSend() {
        val vm = vm()
        vm.onEvent(SignInEvent.EmailChanged("nope"))
        vm.onEvent(SignInEvent.SendCode)
        assertThat(vm.state.value.step).isEqualTo(SignInStep.Email)
    }

    @Test fun emailThenCodeSignsIn() {
        val vm = vm()
        vm.onEvent(SignInEvent.EmailChanged("a@b.com"))
        vm.onEvent(SignInEvent.SendCode)
        assertThat(vm.state.value.step).isEqualTo(SignInStep.Code)
        vm.onEvent(SignInEvent.CodeChanged("12a3456789"))
        assertThat(vm.state.value.code).isEqualTo("123456")
        vm.onEvent(SignInEvent.VerifyCode)
        assertThat(vm.state.value.signedIn).isTrue()
        assertThat(store.t).isEqualTo("tok")
    }

    @Test fun wrongCodeShowsError() {
        verify = TokenOrError(null, AuthResult.InvalidCode)
        val vm = vm()
        vm.onEvent(SignInEvent.EmailChanged("a@b.com"))
        vm.onEvent(SignInEvent.SendCode)
        vm.onEvent(SignInEvent.CodeChanged("000000"))
        vm.onEvent(SignInEvent.VerifyCode)
        assertThat(vm.state.value.error).isEqualTo(AuthResult.InvalidCode)
        assertThat(vm.state.value.signedIn).isFalse()
    }

    @Test fun offlineSendStaysOnEmail() {
        start = AuthResult.Offline
        val vm = vm()
        vm.onEvent(SignInEvent.EmailChanged("a@b.com"))
        vm.onEvent(SignInEvent.SendCode)
        assertThat(vm.state.value.step).isEqualTo(SignInStep.Email)
        assertThat(vm.state.value.error).isEqualTo(AuthResult.Offline)
    }

    @Test fun cancelledGoogleShowsFailure() {
        val vm = vm()
        vm.onEvent(SignInEvent.GoogleSignIn)
        assertThat(vm.state.value.error).isEqualTo(AuthResult.Failed)
    }

    @Test fun stepAndEmailSurviveProcessDeath() {
        val saved = SavedStateHandle()
        val first = vm(saved)
        first.onEvent(SignInEvent.EmailChanged("a@b.com"))
        first.onEvent(SignInEvent.SendCode)
        val second = vm(saved)
        assertThat(second.state.value.step).isEqualTo(SignInStep.Code)
        assertThat(second.state.value.email).isEqualTo("a@b.com")
        assertThat(second.state.value.code).isEmpty()
    }
}
