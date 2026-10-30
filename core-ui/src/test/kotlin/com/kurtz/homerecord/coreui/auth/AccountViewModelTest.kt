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

// AC-H15: Sign out asks first, and only the confirmed choice ends the session.
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val api = object : AuthApi {
        override suspend fun startEmailCode(email: String) = AuthResult.Success
        override suspend fun verifyEmailCode(email: String, code: String) = TokenOrError("tok")
        override suspend fun signInWithGoogle(idToken: String) = TokenOrError("g")
    }
    private val store = object : TokenStore {
        var t: String? = "tok"
        override fun read() = t
        override fun write(token: String) { t = token }
        override fun clear() { t = null }
    }
    private val google = object : GoogleCredentialSource { override suspend fun idToken(): String? = null }
    private val repo = AuthRepository(api, store, google)

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun signOutAsksAndCancelKeepsTheSession() {
        val vm = AccountViewModel(repo)
        assertThat(vm.state.value).isEqualTo(AccountUiState(AccountPhase.SignedIn))
        vm.onEvent(AccountEvent.SignOut)
        assertThat(vm.state.value.confirmSignOut).isTrue()
        assertThat(store.t).isEqualTo("tok")
        vm.onEvent(AccountEvent.DismissSignOut)
        assertThat(vm.state.value).isEqualTo(AccountUiState(AccountPhase.SignedIn))
        assertThat(store.t).isEqualTo("tok")
    }

    @Test fun confirmSignsOutAndClosesTheDialog() {
        val vm = AccountViewModel(repo)
        vm.onEvent(AccountEvent.SignOut)
        vm.onEvent(AccountEvent.ConfirmSignOut)
        assertThat(vm.state.value).isEqualTo(AccountUiState(AccountPhase.SignedOut))
        assertThat(store.t).isNull()
    }

    @Test fun openDialogSurvivesProcessDeath() {
        val saved = SavedStateHandle()
        AccountViewModel(repo, saved).onEvent(AccountEvent.SignOut)
        assertThat(AccountViewModel(repo, saved).state.value.confirmSignOut).isTrue()
    }
}
