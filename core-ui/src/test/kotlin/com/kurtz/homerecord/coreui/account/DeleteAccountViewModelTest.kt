package com.kurtz.homerecord.coreui.account

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

// T081d: Delete account asks first; only the confirmed choice deletes, once, and a failure says why.
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteAccountViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun deleteAsksAndDismissDeletesNothing() {
        var calls = 0
        val vm = DeleteAccountViewModel({ calls++; ApiResult.Ok(Unit) })
        vm.onEvent(DeleteAccountEvent.Delete)
        assertThat(vm.state.value.confirmOpen).isTrue()
        vm.onEvent(DeleteAccountEvent.Dismiss)
        assertThat(vm.state.value.confirmOpen).isFalse()
        assertThat(calls).isEqualTo(0)
    }

    @Test fun confirmDeletesOnceEvenWhenTappedTwice() {
        var calls = 0
        val gate = CompletableDeferred<ApiResult<Unit>>()
        val vm = DeleteAccountViewModel({ calls++; gate.await() })
        vm.onEvent(DeleteAccountEvent.Delete)
        vm.onEvent(DeleteAccountEvent.Confirm)
        vm.onEvent(DeleteAccountEvent.Confirm)
        assertThat(vm.state.value.deleting).isTrue()
        gate.complete(ApiResult.Ok(Unit))
        assertThat(calls).isEqualTo(1)
        assertThat(vm.state.value.deleted).isTrue()
        assertThat(vm.state.value.deleting).isFalse()
    }

    @Test fun offlineAndServerFailuresAreTold() {
        val offline = DeleteAccountViewModel({ ApiResult.Err(ApiFailure.Offline) })
        offline.onEvent(DeleteAccountEvent.Confirm)
        assertThat(offline.state.value.failed).isTrue()
        assertThat(offline.state.value.offline).isTrue()
        assertThat(offline.state.value.deleted).isFalse()
        val failed = DeleteAccountViewModel({ ApiResult.Err(ApiFailure.Failed) })
        failed.onEvent(DeleteAccountEvent.Confirm)
        assertThat(failed.state.value.failed).isTrue()
        assertThat(failed.state.value.offline).isFalse()
    }

    @Test fun theOpenDialogAndTheDeletedNoteSurviveProcessDeath() {
        val saved = SavedStateHandle()
        DeleteAccountViewModel({ ApiResult.Ok(Unit) }, saved).onEvent(DeleteAccountEvent.Delete)
        assertThat(DeleteAccountViewModel({ ApiResult.Ok(Unit) }, saved).state.value.confirmOpen).isTrue()
        DeleteAccountViewModel({ ApiResult.Ok(Unit) }, saved).onEvent(DeleteAccountEvent.Confirm)
        assertThat(DeleteAccountViewModel({ ApiResult.Ok(Unit) }, saved).state.value.deleted).isTrue()
    }
}
