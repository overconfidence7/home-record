package com.kurtz.homerecord.fieldlog.company

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.company.OvertimeRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CompanyViewModelTest {
    private val api = FakeCompanyApi()
    private var savedName: String? = null

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = CompanyViewModel(api, "c1") { savedName = it }

    @Test fun loadsProfileIntoTheForm() {
        val s = vm().state.value
        assertThat(s.status).isEqualTo(TeamStatus.Ready)
        assertThat(s.name).isEqualTo("Kurtz Electric")
        assertThat(s.punchDistanceText).isEqualTo("500")
        assertThat(s.dirty).isFalse()
        assertThat(s.canSave).isFalse()
    }

    @Test fun loadFailureCanRetry() {
        api.profile = ApiResult.Err(ApiFailure.Offline)
        val vm = vm()
        assertThat(vm.state.value.status).isEqualTo(TeamStatus.Failed)
        api.profile = FakeCompanyApi().profile
        vm.onEvent(CompanyEvent.Retry)
        assertThat(vm.state.value.status).isEqualTo(TeamStatus.Ready)
    }

    @Test fun editingMakesItDirtyAndSavable() {
        val vm = vm()
        vm.onEvent(CompanyEvent.RequireGps(true))
        assertThat(vm.state.value.dirty).isTrue()
        assertThat(vm.state.value.canSave).isTrue()
        vm.onEvent(CompanyEvent.RequireGps(false))
        assertThat(vm.state.value.dirty).isFalse()
    }

    @Test fun blankNameBlocksSave() {
        val vm = vm()
        vm.onEvent(CompanyEvent.Name(" "))
        assertThat(vm.state.value.canSave).isFalse()
    }

    @Test fun punchDistanceMustBeInRange() {
        val vm = vm()
        vm.onEvent(CompanyEvent.PunchDistance("20"))
        assertThat(vm.state.value.distanceInvalid).isTrue()
        assertThat(vm.state.value.canSave).isFalse()
        vm.onEvent(CompanyEvent.PunchDistance("300"))
        assertThat(vm.state.value.punchDistanceFt).isEqualTo(300)
        assertThat(vm.state.value.canSave).isTrue()
        vm.onEvent(CompanyEvent.PunchDistance("12ab"))
        assertThat(vm.state.value.punchDistanceText).isEqualTo("12")
    }

    @Test fun saveSendsEverythingAndReportsSaved() {
        val vm = vm()
        vm.onEvent(CompanyEvent.Name("Kurtz Electric LLC"))
        vm.onEvent(CompanyEvent.Overtime(OvertimeRule.Weekly40))
        vm.onEvent(CompanyEvent.PunchDistance("250"))
        vm.onEvent(CompanyEvent.PhotoOnPunch(true))
        vm.onEvent(CompanyEvent.Save)
        val sent = api.lastUpdate!!
        assertThat(sent.name).isEqualTo("Kurtz Electric LLC")
        assertThat(sent.settings!!.overtimeRule).isEqualTo(OvertimeRule.Weekly40)
        assertThat(sent.settings!!.punchDistanceFt).isEqualTo(250)
        assertThat(sent.settings!!.photoOnPunch).isTrue()
        assertThat(vm.state.value.saved).isTrue()
        assertThat(savedName).isEqualTo("Kurtz Electric LLC")
    }

    @Test fun failedSaveKeepsTheFormAndShowsWhy() {
        api.action = ApiResult.Err(ApiFailure.Offline)
        val vm = vm()
        vm.onEvent(CompanyEvent.Name("New name"))
        vm.onEvent(CompanyEvent.Save)
        assertThat(vm.state.value.saved).isFalse()
        assertThat(vm.state.value.saveFailure).isEqualTo(ApiFailure.Offline)
        assertThat(vm.state.value.name).isEqualTo("New name")
    }
}
