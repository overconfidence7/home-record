package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.auth.AccountPhase
import com.kurtz.homerecord.coreui.auth.AccountScreen
import com.kurtz.homerecord.coreui.auth.AccountUiState
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.account.AccountSyncScreen
import com.kurtz.homerecord.housemanual.account.CloudCopyEvent
import com.kurtz.homerecord.housemanual.account.CloudCopySection
import com.kurtz.homerecord.housemanual.account.CloudCopyUiState
import com.kurtz.homerecord.housemanual.account.CloudCopyViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

// AC-H15: HM-Account "Delete cloud copy" asks first, then deletes, signs out and says the home is still on this phone.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CloudCopyTest {
    @get:Rule val rule = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    @AcceptanceCriterion("AC-H15")
    fun asksFirstThenDeletesAndRemembersItWasDeleted() = runTest {
        val calls = mutableListOf<String>()
        val answer = CompletableDeferred<ApiResult<Unit>>()
        val saved = SavedStateHandle()
        val vm = CloudCopyViewModel("h1", { calls += it; answer.await() }, saved)

        vm.onEvent(CloudCopyEvent.Delete)
        assertThat(vm.state.value.confirmOpen).isTrue()
        vm.onEvent(CloudCopyEvent.Dismiss)
        assertThat(vm.state.value.confirmOpen).isFalse()
        assertThat(calls).isEmpty()

        vm.onEvent(CloudCopyEvent.Delete)
        vm.onEvent(CloudCopyEvent.Confirm)
        assertThat(vm.state.value.deleting).isTrue()
        vm.onEvent(CloudCopyEvent.Confirm)
        answer.complete(ApiResult.Ok(Unit))

        assertThat(calls).containsExactly("h1")
        assertThat(vm.state.value).isEqualTo(CloudCopyUiState(deleted = true))
        // Process death after the delete: the notice above the sign-in form stays.
        assertThat(CloudCopyViewModel("h1", { error("not called") }, saved).state.value.deleted).isTrue()
    }

    @Test
    fun theConfirmDialogSurvivesProcessDeath() {
        val saved = SavedStateHandle()
        CloudCopyViewModel("h1", { ApiResult.Ok(Unit) }, saved).onEvent(CloudCopyEvent.Delete)
        assertThat(CloudCopyViewModel("h1", { ApiResult.Ok(Unit) }, saved).state.value.confirmOpen).isTrue()
    }

    @Test
    fun aFailureSaysWhyAndCanBeTriedAgain() = runTest {
        var answer: ApiResult<Unit> = ApiResult.Err(ApiFailure.Offline)
        val vm = CloudCopyViewModel("h1", { answer })
        vm.onEvent(CloudCopyEvent.Confirm)
        assertThat(vm.state.value).isEqualTo(CloudCopyUiState(failed = true, offline = true))
        answer = ApiResult.Err(ApiFailure.Failed)
        vm.onEvent(CloudCopyEvent.Confirm)
        assertThat(vm.state.value).isEqualTo(CloudCopyUiState(failed = true, offline = false))
        answer = ApiResult.Ok(Unit)
        vm.onEvent(CloudCopyEvent.Confirm)
        assertThat(vm.state.value.deleted).isTrue()
    }

    @Test
    @AcceptanceCriterion("AC-H15")
    fun signedInAccountOffersDeleteCloudCopyAndAsksFirst() {
        val events = mutableListOf<CloudCopyEvent>()
        val holder = androidx.compose.runtime.mutableStateOf(CloudCopyUiState())
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                CompositionLocalProvider(LocalTagPrefix provides "hm") {
                    AccountSyncScreen({}) { modifier ->
                        AccountScreen(
                            AccountUiState(AccountPhase.SignedIn, email = "dana.ortiz@example.com"), {}, modifier,
                            footer = { CloudCopySection(holder.value) { events += it } },
                        )
                    }
                }
            }
        }
        rule.onNodeWithTag("hm_account_delete_cloud").performScrollTo().assertIsDisplayed().performClick()
        assertThat(events).containsExactly(CloudCopyEvent.Delete)

        holder.value = CloudCopyUiState(confirmOpen = true)
        rule.onNodeWithText("Delete cloud copy?").assertIsDisplayed()
        rule.onNodeWithTag("hm_account_delete_cloud_cancel").performClick()
        rule.onNodeWithTag("hm_account_delete_cloud_confirm").performClick()
        assertThat(events).containsExactly(CloudCopyEvent.Delete, CloudCopyEvent.Dismiss, CloudCopyEvent.Confirm).inOrder()

        holder.value = CloudCopyUiState(deleting = true)
        rule.onNodeWithTag("hm_account_delete_cloud").assertIsNotEnabled()
        holder.value = CloudCopyUiState(failed = true, offline = true)
        rule.onNodeWithTag("hm_account_delete_cloud_error").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Can't reach the server. Check your connection and try again.").assertIsDisplayed()
    }

    @Test
    fun afterTheDeleteTheSignInFormSaysTheHomeIsStillHere() {
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                AccountSyncScreen({}, cloudDeleted = true) { modifier -> androidx.compose.foundation.layout.Box(modifier) }
            }
        }
        rule.onNodeWithTag("hm_account_cloud_deleted").assertIsDisplayed()
    }
}
