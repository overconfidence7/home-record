package com.kurtz.homerecord.fieldlog.sync

import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.sync.ConflictEntry
import com.kurtz.homerecord.core.sync.ConflictOutcome
import com.kurtz.homerecord.core.sync.SyncPhase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

// AC-F52 (pending count, sync on request), AC-F53 (the conflict log is shown and can be cleared): FL-Sync's state.
class SyncLogViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val phase = MutableStateFlow(SyncPhase.Offline)
    private val pending = MutableStateFlow(4)
    private val log = MutableStateFlow(listOf(ConflictEntry("task", "t1", "Set the kitchen outlets", ConflictOutcome.NewerKept, listOf("title"), 200)))
    private var syncs = 0
    private lateinit var vm: SyncLogViewModel

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        vm = SyncLogViewModel(SyncLogDeps(phase, pending, log, syncNow = { syncs++ }, clear = { log.value = emptyList() }))
    }

    @After fun tearDown() {
        vm.viewModelScope.cancel()
        Dispatchers.resetMain()
    }

    @Test fun showsPhasePendingAndTheLog() {
        val s = vm.state.value
        assertThat(s.loading).isFalse()
        assertThat(s.phase).isEqualTo(SyncPhase.Offline)
        assertThat(s.pending).isEqualTo(4)
        assertThat(s.entries.single().label).isEqualTo("Set the kitchen outlets")
    }

    @Test fun reconnectingAndSyncingEmptiesThePendingCount() {
        vm.syncNow()
        assertThat(syncs).isEqualTo(1)
        phase.value = SyncPhase.Syncing
        pending.value = 0
        phase.value = SyncPhase.Idle
        assertThat(vm.state.value.pending).isEqualTo(0)
        assertThat(vm.state.value.phase).isEqualTo(SyncPhase.Idle)
    }

    @Test fun clearEmptiesTheLog() {
        vm.clear()
        assertThat(vm.state.value.entries).isEmpty()
    }
}
