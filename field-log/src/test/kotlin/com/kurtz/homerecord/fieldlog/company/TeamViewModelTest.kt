package com.kurtz.homerecord.fieldlog.company

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.TeamMember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TeamViewModelTest {
    private val owner = TeamMember("u1", "Nate Kurtz", "nate@k.com", CompanyRole.Owner, null)
    private val office = TeamMember("u2", "Dana Whitfield", "dana@k.com", CompanyRole.Office, null)
    private val sam = TeamMember("u3", null, "sam.rivera@gmail.com", CompanyRole.Crew, 3200)
    private val api = FakeCompanyApi().apply { members = ApiResult.Ok(listOf(sam, owner, office)) }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(role: CompanyRole = CompanyRole.Owner) =
        TeamViewModel(api, CompanyMembership("c1", "Kurtz Electric", role, null, null)) { "https://x.test/join/$it" }

    @Test fun loadsMembersSortedByRoleWithInvite() {
        val s = vm().state.value
        assertThat(s.status).isEqualTo(TeamStatus.Ready)
        assertThat(s.members.map { it.userId }).containsExactly("u1", "u2", "u3").inOrder()
        assertThat(s.inviteCode).isEqualTo("K7M2QX9R")
        assertThat(s.inviteLink).isEqualTo("https://x.test/join/K7M2QX9R")
    }

    @Test fun failedLoadShowsFailure() {
        api.members = ApiResult.Err(ApiFailure.Offline)
        val s = vm().state.value
        assertThat(s.status).isEqualTo(TeamStatus.Failed)
        assertThat(s.failure).isEqualTo(ApiFailure.Offline)
    }

    @Test fun failedBackgroundRefreshKeepsLastList() {
        val vm = vm()
        api.members = ApiResult.Err(ApiFailure.Offline)
        vm.onEvent(TeamEvent.Refresh)
        assertThat(vm.state.value.status).isEqualTo(TeamStatus.Ready)
        assertThat(vm.state.value.members).hasSize(3)
    }

    @Test fun ownerCanChangeRoleOfCrewAndOffice() {
        val s = vm().state.value
        assertThat(s.rolesFor(sam)).containsExactly(CompanyRole.Office, CompanyRole.Lead, CompanyRole.Sub)
        assertThat(s.rolesFor(office)).containsExactly(CompanyRole.Lead, CompanyRole.Crew, CompanyRole.Sub)
        assertThat(s.rolesFor(owner)).isEmpty()
        assertThat(s.canRemove(owner)).isFalse()
        assertThat(s.canSetRate(sam)).isTrue()
    }

    @Test fun officeCannotManageOfficeOwnerOrRates() {
        val s = vm(CompanyRole.Office).state.value
        assertThat(s.rolesFor(sam)).containsExactly(CompanyRole.Lead, CompanyRole.Sub)
        assertThat(s.rolesFor(office)).isEmpty()
        assertThat(s.canRemove(office)).isFalse()
        assertThat(s.canRemove(sam)).isTrue()
        assertThat(s.canSetRate(sam)).isFalse()
    }

    @Test fun setRoleCallsApiAndClosesSheet() {
        val vm = vm()
        vm.onEvent(TeamEvent.OpenActions(sam))
        vm.onEvent(TeamEvent.SetRole(sam, CompanyRole.Lead))
        assertThat(api.calls).containsExactly("role:u3:lead")
        assertThat(vm.state.value.actionsFor).isNull()
        assertThat(vm.state.value.busy).isFalse()
    }

    @Test fun removeNeedsConfirmation() {
        val vm = vm()
        vm.onEvent(TeamEvent.AskRemove(sam))
        assertThat(api.calls).isEmpty()
        assertThat(vm.state.value.confirmRemove).isEqualTo(sam)
        vm.onEvent(TeamEvent.CancelRemove)
        assertThat(api.calls).isEmpty()
        vm.onEvent(TeamEvent.AskRemove(sam))
        vm.onEvent(TeamEvent.ConfirmRemove)
        assertThat(api.calls).containsExactly("remove:u3")
        assertThat(vm.state.value.confirmRemove).isNull()
    }

    @Test fun failedActionSurfacesErrorAndKeepsList() {
        api.action = ApiResult.Err(ApiFailure.Forbidden)
        val vm = vm()
        vm.onEvent(TeamEvent.SetRole(sam, CompanyRole.Lead))
        assertThat(vm.state.value.actionError).isEqualTo(ApiFailure.Forbidden)
        assertThat(vm.state.value.members).hasSize(3)
        vm.onEvent(TeamEvent.DismissError)
        assertThat(vm.state.value.actionError).isNull()
    }

    @Test fun newCodeReplacesInviteAndLink() {
        val vm = vm()
        vm.onEvent(TeamEvent.NewCode)
        assertThat(vm.state.value.inviteCode).isEqualTo("NEWCODE2")
        assertThat(vm.state.value.inviteLink).isEqualTo("https://x.test/join/NEWCODE2")
    }

    @Test fun savesRate() {
        val vm = vm()
        vm.onEvent(TeamEvent.AskRate(sam))
        vm.onEvent(TeamEvent.SaveRate(3500))
        assertThat(api.calls).containsExactly("rate:u3:3500")
        assertThat(vm.state.value.rateFor).isNull()
    }
}
