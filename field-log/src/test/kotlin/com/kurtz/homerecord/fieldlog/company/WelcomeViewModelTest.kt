package com.kurtz.homerecord.fieldlog.company

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.company.CompanyRepository
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.CompanyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.kurtz.homerecord.core.company.CompanyApi
import com.kurtz.homerecord.core.company.CompanyCache

@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeViewModelTest {
    private var meResult: ApiResult<List<CompanyMembership>> = ApiResult.Ok(emptyList())
    private var joinResult: ApiResult<String> = ApiResult.Ok("c1")
    private val created = mutableListOf<List<String>>()
    private val api = object : CompanyApi by FakeCompanyApi() {
        override suspend fun me() = meResult
        override suspend fun create(name: String, trade: String, phone: String): ApiResult<String> {
            created += listOf(name, trade, phone)
            meResult = ApiResult.Ok(listOf(CompanyMembership("c1", name, CompanyRole.Owner, null, null)))
            return ApiResult.Ok("c1")
        }
        override suspend fun join(code: String) = joinResult
    }
    private val cache = object : CompanyCache {
        var m: CompanyMembership? = null
        override fun read() = m
        override fun write(membership: CompanyMembership?) { m = membership }
    }
    private val repo = CompanyRepository(api, cache)
    private var signedOut = false

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(saved: SavedStateHandle = SavedStateHandle(), code: String? = null) = WelcomeViewModel(repo, { signedOut = true }, saved, code)

    @Test fun createNeedsAName() {
        val vm = vm()
        assertThat(vm.state.value.canSubmit).isFalse()
        vm.onEvent(WelcomeEvent.NameChanged("  "))
        assertThat(vm.state.value.canSubmit).isFalse()
        vm.onEvent(WelcomeEvent.NameChanged("Kurtz Electric"))
        assertThat(vm.state.value.canSubmit).isTrue()
    }

    @Test fun createMakesTheUserOwner() {
        val vm = vm()
        vm.onEvent(WelcomeEvent.NameChanged("Kurtz Electric"))
        vm.onEvent(WelcomeEvent.TradeChanged("Electrical"))
        vm.onEvent(WelcomeEvent.PhoneChanged("555-0100"))
        vm.onEvent(WelcomeEvent.Submit)
        assertThat(created).containsExactly(listOf("Kurtz Electric", "Electrical", "555-0100"))
        assertThat((repo.state.value as CompanyState.Active).membership.role).isEqualTo(CompanyRole.Owner)
    }

    @Test fun joinNeedsEightCharactersAndNormalizesInput() {
        val vm = vm()
        vm.onEvent(WelcomeEvent.ModeChanged(WelcomeMode.Join))
        vm.onEvent(WelcomeEvent.CodeChanged("k7m2-qx"))
        assertThat(vm.state.value.canSubmit).isFalse()
        vm.onEvent(WelcomeEvent.CodeChanged("k7m2 qx9r"))
        assertThat(vm.state.value.code).isEqualTo("K7M2QX9R")
        assertThat(vm.state.value.canSubmit).isTrue()
    }

    @Test fun badCodeShowsErrorAndClearsItOnEdit() {
        joinResult = ApiResult.Err(ApiFailure.InvalidCode)
        val vm = vm(code = "ZZZZZZZZ")
        assertThat(vm.state.value.mode).isEqualTo(WelcomeMode.Join)
        vm.onEvent(WelcomeEvent.Submit)
        assertThat(vm.state.value.error).isEqualTo(ApiFailure.InvalidCode)
        assertThat(vm.state.value.busy).isFalse()
        vm.onEvent(WelcomeEvent.CodeChanged("ZZZZZZZY"))
        assertThat(vm.state.value.error).isNull()
    }

    @Test fun enteredTextSurvivesProcessDeath() {
        val saved = SavedStateHandle()
        val first = vm(saved)
        first.onEvent(WelcomeEvent.NameChanged("Kurtz Electric"))
        first.onEvent(WelcomeEvent.ModeChanged(WelcomeMode.Join))
        first.onEvent(WelcomeEvent.CodeChanged("K7M2QX9R"))
        val second = vm(saved)
        assertThat(second.state.value.name).isEqualTo("Kurtz Electric")
        assertThat(second.state.value.mode).isEqualTo(WelcomeMode.Join)
        assertThat(second.state.value.code).isEqualTo("K7M2QX9R")
    }

    @Test fun signOutIsForwarded() {
        vm().onEvent(WelcomeEvent.SignOut)
        assertThat(signedOut).isTrue()
    }
}
