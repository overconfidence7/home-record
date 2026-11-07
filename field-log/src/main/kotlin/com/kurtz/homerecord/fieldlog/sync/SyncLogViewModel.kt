package com.kurtz.homerecord.fieldlog.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.sync.ConflictEntry
import com.kurtz.homerecord.core.sync.SyncPhase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import com.kurtz.homerecord.core.data.Reloadable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What FL-Sync needs: the engine's phase and pending count, the conflict log, and the two actions. */
class SyncLogDeps(
    val phase: Flow<SyncPhase>,
    val pending: Flow<Int>,
    val entries: Flow<List<ConflictEntry>>,
    val syncNow: () -> Unit,
    val clear: suspend () -> Unit,
)

class SyncLogViewModel(private val deps: SyncLogDeps) : ViewModel() {
    /** A failed read shows the error state with Retry instead of ending the ViewModel (AC-U4). */
    private val load = Reloadable("Sync log")

    val state: StateFlow<SyncLogUiState> = load.wrap(SyncLogUiState(loading = false, loadFailed = true)) {
        combine(deps.phase, deps.pending, deps.entries) { phase, pending, entries -> SyncLogUiState(phase, pending, entries, loading = false) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SyncLogUiState())

    fun syncNow() = deps.syncNow()

    fun retry() = load.retry()

    /** Empties the log only; the records stay as they are. */
    fun clear() {
        viewModelScope.launch { deps.clear() }
    }
}
