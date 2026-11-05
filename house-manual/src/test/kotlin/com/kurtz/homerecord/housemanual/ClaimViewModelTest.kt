package com.kurtz.homerecord.housemanual

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.auth.AuthState
import com.kurtz.homerecord.core.bundle.ClaimedHandoff
import com.kurtz.homerecord.core.bundle.HandoffClaimApi
import com.kurtz.homerecord.core.bundle.HandoffClaimFailure
import com.kurtz.homerecord.core.bundle.HandoffClaimResult
import com.kurtz.homerecord.core.bundle.HandoffDownloadResult
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.importing.ClaimEvent
import com.kurtz.homerecord.housemanual.importing.ClaimPhase
import com.kurtz.homerecord.housemanual.importing.ClaimViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

// AC-H17 (claim link): signed out shows sign-in, signing in claims and downloads, then the preview opens; failures
// say why and only offline or server errors offer Retry.
@AcceptanceCriterion("AC-H17")
@OptIn(ExperimentalCoroutinesApi::class)
class ClaimViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val dest = File("build/tmp/claim-test.homerecord")

    private class FakeApi(
        var claim: HandoffClaimResult = HandoffClaimResult.Claimed(ClaimedHandoff("Ortiz house", "QA Electric", 10)),
        var download: HandoffDownloadResult? = null,
    ) : HandoffClaimApi {
        var claims = 0
        var downloads = 0
        override suspend fun claim(token: String): HandoffClaimResult { claims++; return claim }
        override suspend fun download(token: String, dest: File): HandoffDownloadResult { downloads++; return download ?: HandoffDownloadResult.Saved(dest) }
    }

    @Test fun signedOutAsksToSignInThenClaimsAndDownloads() { // AC-H17
        val auth = MutableStateFlow<AuthState>(AuthState.Restoring)
        val api = FakeApi()
        val vm = ClaimViewModel("t", auth, api, dest)
        assertThat(vm.state.value.phase).isEqualTo(ClaimPhase.Checking)
        auth.value = AuthState.SignedOut
        assertThat(vm.state.value.phase).isEqualTo(ClaimPhase.SignIn)
        assertThat(api.claims).isEqualTo(0)
        auth.value = AuthState.SignedIn("tok")
        assertThat(vm.state.value.phase).isEqualTo(ClaimPhase.Ready)
        assertThat(vm.state.value.homeName).isEqualTo("Ortiz house")
        assertThat(vm.state.value.companyName).isEqualTo("QA Electric")
        assertThat(api.claims).isEqualTo(1)
        assertThat(api.downloads).isEqualTo(1)
    }

    @Test fun expiredOrTakenLinksCannotBeRetried() { // AC-H17, AC-B40
        for (f in listOf(HandoffClaimFailure.Expired, HandoffClaimFailure.ClaimedByOther, HandoffClaimFailure.NotFound)) {
            val api = FakeApi(claim = HandoffClaimResult.Failed(f))
            val vm = ClaimViewModel("t", MutableStateFlow(AuthState.SignedIn("tok")), api, dest)
            assertThat(vm.state.value.phase).isEqualTo(ClaimPhase.Failed)
            assertThat(vm.state.value.failure).isEqualTo(f)
            assertThat(vm.state.value.canRetry).isFalse()
            vm.onEvent(ClaimEvent.Retry)
            assertThat(api.claims).isEqualTo(1)
        }
    }

    @Test fun offlineDownloadRetriesTheClaimAndDownload() { // AC-H17
        val api = FakeApi(download = HandoffDownloadResult.Failed(HandoffClaimFailure.Offline))
        val vm = ClaimViewModel("t", MutableStateFlow(AuthState.SignedIn("tok")), api, dest)
        assertThat(vm.state.value.phase).isEqualTo(ClaimPhase.Failed)
        assertThat(vm.state.value.canRetry).isTrue()
        api.download = null
        vm.onEvent(ClaimEvent.Retry)
        assertThat(vm.state.value.phase).isEqualTo(ClaimPhase.Ready)
        assertThat(api.claims).isEqualTo(2)
    }

    @Test fun anEndedSessionGoesBackToSignIn() { // AC-H17
        val auth = MutableStateFlow<AuthState>(AuthState.SignedIn("old"))
        val api = FakeApi(claim = HandoffClaimResult.Failed(HandoffClaimFailure.SignedOut))
        val vm = ClaimViewModel("t", auth, api, dest)
        assertThat(vm.state.value.phase).isEqualTo(ClaimPhase.SignIn)
        api.claim = HandoffClaimResult.Claimed(ClaimedHandoff("Ortiz house", "QA Electric", 10))
        auth.value = AuthState.SignedOut
        auth.value = AuthState.SignedIn("new")
        assertThat(vm.state.value.phase).isEqualTo(ClaimPhase.Ready)
    }
}
