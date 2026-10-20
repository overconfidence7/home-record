package com.kurtz.homerecord.core.sync

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoordinatorTest {
    private val home = TenantRef(TenantKind.Home, "h1")

    private class Net : ConnectivityMonitor {
        val state = MutableStateFlow(true)
        override val online: Flow<Boolean> = state
    }

    private class Fixture(scope: TestScope) {
        val signedIn = MutableStateFlow(false)
        val tenant = MutableStateFlow<TenantRef?>(null)
        val net = Net()
        val calls = mutableListOf<TenantRef>()
        val coordinator = SyncCoordinator(
            scope.backgroundScope,
            { t -> calls += t; ApiResult.Ok(Unit) },
            signedIn,
            tenant,
            net,
            intervalMs = 30_000,
            writeDebounceMs = 1_000,
        ).also { it.start() }
    }

    @Test
    fun `does nothing while signed out or without a tenant`() = runTest {
        val f = Fixture(this)
        f.tenant.value = home
        f.coordinator.setForeground(true)
        f.coordinator.requestSync()
        advanceTimeBy(60_000)
        assertThat(f.calls).isEmpty()
    }

    @Test
    fun `syncs once when sign-in and tenant are both present`() = runTest {
        val f = Fixture(this)
        f.tenant.value = home
        f.signedIn.value = true
        runCurrent()
        assertThat(f.calls).containsExactly(home)
    }

    @Test
    fun `local writes sync after a short debounce`() = runTest {
        val f = Fixture(this)
        f.tenant.value = home
        f.signedIn.value = true
        runCurrent()
        f.coordinator.requestSync()
        f.coordinator.requestSync()
        advanceTimeBy(900)
        assertThat(f.calls).hasSize(1)
        advanceTimeBy(200)
        assertThat(f.calls).hasSize(2)
    }

    @Test
    fun `polls every 30 seconds only while in the foreground`() = runTest {
        val f = Fixture(this)
        f.tenant.value = home
        f.signedIn.value = true
        runCurrent()
        advanceTimeBy(65_000)
        assertThat(f.calls).hasSize(1)
        f.coordinator.setForeground(true)
        advanceTimeBy(65_000)
        assertThat(f.calls).hasSize(3)
        f.coordinator.setForeground(false)
        advanceTimeBy(65_000)
        assertThat(f.calls).hasSize(3)
    }

    @Test
    fun `syncs again when the network returns`() = runTest {
        val f = Fixture(this)
        f.tenant.value = home
        f.signedIn.value = true
        runCurrent()
        f.net.state.value = false
        runCurrent()
        assertThat(f.calls).hasSize(1)
        f.net.state.value = true
        runCurrent()
        assertThat(f.calls).hasSize(2)
    }

    @Test
    fun `signing out stops syncing`() = runTest {
        val f = Fixture(this)
        f.tenant.value = home
        f.signedIn.value = true
        f.coordinator.setForeground(true)
        runCurrent()
        f.signedIn.value = false
        runCurrent()
        advanceTimeBy(120_000)
        assertThat(f.calls).hasSize(1)
    }
}
