package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.sync.CloudRestoreResult
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.account.CloudRestoreEvent
import com.kurtz.homerecord.housemanual.account.CloudRestorePhase
import com.kurtz.homerecord.housemanual.account.CloudRestoreScreen
import com.kurtz.homerecord.housemanual.account.CloudRestoreUiState
import com.kurtz.homerecord.housemanual.account.CloudRestoreViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-H15: onboarding "Sign in to get your home" pulls the account's home once signed in, or says there is none.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CloudRestoreTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    @AcceptanceCriterion("AC-H15")
    fun signingInRunsTheRestoreAndWaitsForTheHome() = runTest {
        val signedIn = MutableStateFlow(false)
        val answer = CompletableDeferred<CloudRestoreResult>()
        var calls = 0
        val vm = CloudRestoreViewModel({ calls++; answer.await() }, signedIn)
        assertThat(vm.state.value.phase).isEqualTo(CloudRestorePhase.SignedOut)
        assertThat(calls).isEqualTo(0)

        signedIn.value = true
        assertThat(calls).isEqualTo(1)
        assertThat(vm.state.value.phase).isEqualTo(CloudRestorePhase.Working)
        answer.complete(CloudRestoreResult.Restored("h1"))
        // The tabs replace this screen once the home row is in; the spinner stays until then.
        assertThat(vm.state.value.phase).isEqualTo(CloudRestorePhase.Working)
    }

    @Test fun noHomeAndFailureSaySoAndRetryTriesAgain() = runTest {
        val answers = ArrayDeque(listOf(CloudRestoreResult.Failed(ApiFailure.Offline), CloudRestoreResult.NoHome))
        val vm = CloudRestoreViewModel({ answers.removeFirst() }, MutableStateFlow(true))
        assertThat(vm.state.value).isEqualTo(CloudRestoreUiState(CloudRestorePhase.Failed, offline = true))

        vm.onEvent(CloudRestoreEvent.Retry)
        assertThat(vm.state.value).isEqualTo(CloudRestoreUiState(CloudRestorePhase.NoHome))
        // Retry means nothing once there is an answer that is not a failure.
        vm.onEvent(CloudRestoreEvent.Retry)
        assertThat(answers).isEmpty()
    }

    @Test fun errorOffersRetryAndSetUpWithFullSizeTargets() {
        val events = mutableListOf<CloudRestoreEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual) {
                CompositionLocalProvider(LocalTagPrefix provides "hm") {
                    CloudRestoreScreen(CloudRestoreUiState(CloudRestorePhase.Failed, offline = true), { events += it }) {}
                }
            }
        }
        rule.onNodeWithTag("hm_cloud_restore_error").assertIsDisplayed()
        rule.onNodeWithTag("hm_cloud_restore_retry").assertHeightIsAtLeast(48.dp).performClick()
        rule.onNodeWithTag("hm_cloud_restore_setup").assertHeightIsAtLeast(48.dp).performClick()
        assertThat(events).containsExactly(CloudRestoreEvent.Retry, CloudRestoreEvent.SetUp).inOrder()
    }
}
