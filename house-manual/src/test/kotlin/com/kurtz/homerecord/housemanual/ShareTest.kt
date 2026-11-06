package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.auth.AuthState
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.share.AcceptResult
import com.kurtz.homerecord.core.share.HomePeople
import com.kurtz.homerecord.core.share.HomePerson
import com.kurtz.homerecord.core.share.HomeRole
import com.kurtz.homerecord.core.share.HomeShareApi
import com.kurtz.homerecord.core.share.InviteFailure
import com.kurtz.homerecord.core.share.JoinedHome
import com.kurtz.homerecord.core.share.NewInvite
import com.kurtz.homerecord.core.share.OpenInvite
import com.kurtz.homerecord.core.sync.CloudRestoreResult
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.share.JoinEvent
import com.kurtz.homerecord.housemanual.share.JoinPhase
import com.kurtz.homerecord.housemanual.share.JoinViewModel
import com.kurtz.homerecord.housemanual.share.ShareEvent
import com.kurtz.homerecord.housemanual.share.SharePhase
import com.kurtz.homerecord.housemanual.share.ShareScreen
import com.kurtz.homerecord.housemanual.share.ShareUiState
import com.kurtz.homerecord.housemanual.share.ShareViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

// AC-H16: HM-Share invites family and viewers by link, lists people, removes them; an invite link signs in, joins and
// pulls the shared home down on a phone that has none.
@AcceptanceCriterion("AC-H16")
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ShareTest {
    @get:Rule val compose = createComposeRule()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private val owner = HomePerson("u1", "Dana Whitfield", "dana@x.com", HomeRole.Owner, you = true)
    private val editor = HomePerson("u2", "Sam Whitfield", "sam@x.com", HomeRole.Editor, you = false)

    private class FakeApi(var people: ApiResult<HomePeople>) : HomeShareApi {
        val calls = mutableListOf<String>()
        var accept: AcceptResult = AcceptResult.Joined(JoinedHome("h1", "Maple St", HomeRole.Editor))
        var inviteResult: ApiResult<NewInvite>? = null
        override suspend fun people(homeId: String) = people.also { calls += "people" }
        override suspend fun invite(homeId: String, role: HomeRole): ApiResult<NewInvite> {
            calls += "invite:$role"
            return inviteResult ?: ApiResult.Ok(NewInvite("i1", role, "https://x.test/invite/tok", 1))
        }
        override suspend fun revoke(homeId: String, inviteId: String): ApiResult<Unit> { calls += "revoke:$inviteId"; return ApiResult.Ok(Unit) }
        override suspend fun remove(homeId: String, userId: String): ApiResult<Unit> { calls += "remove:$userId"; return ApiResult.Ok(Unit) }
        override suspend fun accept(token: String): AcceptResult { calls += "accept"; return accept }
    }

    @Test fun signedOutAsksToSignInThenLoadsPeople() { // AC-H16
        val signedIn = MutableStateFlow(false)
        val api = FakeApi(ApiResult.Ok(HomePeople(listOf(owner, editor), emptyList())))
        val vm = ShareViewModel("h1", api, signedIn)
        assertThat(vm.state.value.phase).isEqualTo(SharePhase.SignedOut)
        signedIn.value = true
        assertThat(vm.state.value.phase).isEqualTo(SharePhase.Loaded)
        assertThat(vm.state.value.isOwner).isTrue()
        assertThat(vm.state.value.people).hasSize(2)
    }

    @Test fun inviteHandsTheLinkToTheShareSheetAndReloads() = runTest { // AC-H16
        val api = FakeApi(ApiResult.Ok(HomePeople(listOf(owner), emptyList())))
        val vm = ShareViewModel("h1", api, MutableStateFlow(true))
        vm.onEvent(ShareEvent.Invite(HomeRole.Viewer))
        assertThat(vm.links.first()).isEqualTo("https://x.test/invite/tok")
        assertThat(api.calls).containsExactly("people", "invite:Viewer", "people").inOrder()
        assertThat(vm.state.value.creating).isNull()
    }

    @Test fun onlyTheOwnerInvites() { // AC-H16
        val api = FakeApi(ApiResult.Ok(HomePeople(listOf(owner.copy(you = false), editor.copy(you = true)), emptyList())))
        val vm = ShareViewModel("h1", api, MutableStateFlow(true))
        assertThat(vm.state.value.isOwner).isFalse()
        vm.onEvent(ShareEvent.Invite(HomeRole.Editor))
        assertThat(api.calls).doesNotContain("invite:Editor")
    }

    @Test fun removeAsksFirstAndRevokeGoesStraightThrough() { // AC-H16
        val api = FakeApi(ApiResult.Ok(HomePeople(listOf(owner, editor), listOf(OpenInvite("i7", HomeRole.Viewer, 1, 2)))))
        val vm = ShareViewModel("h1", api, MutableStateFlow(true))
        vm.onEvent(ShareEvent.Remove(editor))
        assertThat(vm.state.value.confirmRemove).isEqualTo(editor)
        assertThat(api.calls).doesNotContain("remove:u2")
        vm.onEvent(ShareEvent.DismissRemove)
        assertThat(api.calls).doesNotContain("remove:u2")
        vm.onEvent(ShareEvent.Remove(editor))
        vm.onEvent(ShareEvent.ConfirmRemove)
        assertThat(api.calls).contains("remove:u2")
        vm.onEvent(ShareEvent.Revoke("i7"))
        assertThat(api.calls).contains("revoke:i7")
    }

    @Test fun aHomeNotUploadedYetAndOfflineAreTold() { // AC-H16
        val api = FakeApi(ApiResult.Err(ApiFailure.NotFound))
        val vm = ShareViewModel("h1", api, MutableStateFlow(true))
        assertThat(vm.state.value.phase).isEqualTo(SharePhase.NotUploaded)
        api.people = ApiResult.Err(ApiFailure.Offline)
        vm.onEvent(ShareEvent.Retry)
        assertThat(vm.state.value.phase).isEqualTo(SharePhase.Failed)
        assertThat(vm.state.value.offline).isTrue()
    }

    @Test fun joiningOnAPhoneWithNoHomePullsTheSharedHome() { // AC-H16
        val auth = MutableStateFlow<AuthState>(AuthState.SignedOut)
        val api = FakeApi(ApiResult.Ok(HomePeople(emptyList(), emptyList())))
        val restored = mutableListOf<String>()
        val vm = JoinViewModel("tok", auth, api::accept, { restored += it; CloudRestoreResult.Restored(it) }, { null })
        assertThat(vm.state.value.phase).isEqualTo(JoinPhase.SignIn)
        auth.value = AuthState.SignedIn("t")
        assertThat(vm.state.value.phase).isEqualTo(JoinPhase.Done)
        assertThat(restored).containsExactly("h1")
        assertThat(vm.state.value.homeName).isEqualTo("Maple St")
    }

    @Test fun joiningOnAPhoneWithAnotherHomeKeepsItAndSaysSo() { // AC-H16
        val api = FakeApi(ApiResult.Ok(HomePeople(emptyList(), emptyList())))
        val restored = mutableListOf<String>()
        val vm = JoinViewModel("tok", MutableStateFlow(AuthState.SignedIn("t")), api::accept, { restored += it; CloudRestoreResult.Restored(it) }, { "h-own" })
        assertThat(vm.state.value.phase).isEqualTo(JoinPhase.OtherHome)
        assertThat(restored).isEmpty()
        val same = JoinViewModel("tok", MutableStateFlow(AuthState.SignedIn("t")), api::accept, { CloudRestoreResult.Restored(it) }, { "h1" })
        assertThat(same.state.value.phase).isEqualTo(JoinPhase.Done)
    }

    @Test fun aUsedLinkCannotBeRetriedButOfflineAndAFailedPullCan() { // AC-H16
        val api = FakeApi(ApiResult.Ok(HomePeople(emptyList(), emptyList())))
        api.accept = AcceptResult.Failed(InviteFailure.Used)
        val used = JoinViewModel("tok", MutableStateFlow(AuthState.SignedIn("t")), api::accept, { CloudRestoreResult.Restored(it) }, { null })
        assertThat(used.state.value.phase).isEqualTo(JoinPhase.Failed)
        assertThat(used.state.value.canRetry).isFalse()

        api.accept = AcceptResult.Failed(InviteFailure.Offline)
        var pull: CloudRestoreResult = CloudRestoreResult.Failed(ApiFailure.Offline)
        val vm = JoinViewModel("tok", MutableStateFlow(AuthState.SignedIn("t")), api::accept, { pull }, { null })
        assertThat(vm.state.value.canRetry).isTrue()
        api.accept = AcceptResult.Joined(JoinedHome("h1", "Maple St", HomeRole.Viewer))
        vm.onEvent(JoinEvent.Retry)
        assertThat(vm.state.value.phase).isEqualTo(JoinPhase.Failed)
        assertThat(vm.state.value.restoreFailed).isTrue()
        pull = CloudRestoreResult.Restored("h1")
        vm.onEvent(JoinEvent.Retry)
        assertThat(vm.state.value.phase).isEqualTo(JoinPhase.Done)
    }

    @Test fun ownerSeesInviteRowsRemoveAndLinks() { // AC-H16
        val events = mutableListOf<ShareEvent>()
        compose.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                CompositionLocalProvider(LocalTagPrefix provides "hm") {
                    ShareScreen(
                        ShareUiState(SharePhase.Loaded, listOf(owner, editor), listOf(OpenInvite("i7", HomeRole.Viewer, 1, 1_794_209_600_000))),
                        { events += it },
                    )
                }
            }
        }
        compose.onNodeWithTag("hm_share_invite_editor").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
        compose.onNodeWithTag("hm_share_invite_viewer").assertIsDisplayed().performClick()
        compose.onNodeWithTag("hm_share_remove_1").assertIsDisplayed().performClick()
        compose.onNodeWithTag("hm_share_remove_0").assertDoesNotExist()
        compose.onNodeWithTag("hm_share_revoke_0").performScrollTo().performClick()
        assertThat(events).containsExactly(
            ShareEvent.Invite(HomeRole.Editor), ShareEvent.Invite(HomeRole.Viewer), ShareEvent.Remove(editor), ShareEvent.Revoke("i7"),
        ).inOrder()
    }

    @Test fun familySeesTheListWithoutOwnerControls() { // AC-H16
        compose.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                ShareScreen(ShareUiState(SharePhase.Loaded, listOf(owner.copy(you = false), editor.copy(you = true))), {})
            }
        }
        compose.onNodeWithTag("hm_share_not_owner").assertIsDisplayed()
        compose.onNodeWithTag("hm_share_invite_editor").assertDoesNotExist()
        compose.onNodeWithTag("hm_share_remove_0").assertDoesNotExist()
        compose.onNodeWithTag("hm_share_remove_1").assertDoesNotExist()
    }
}
