package com.kurtz.homerecord.fieldlog.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.AssignmentEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PunchEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** Everything FL-Today reads, as flows, so tests can feed rows directly. */
class TodayDeps(
    val memberId: String,
    val base: TodayUiState,
    val punches: Flow<List<PunchEntity>>,
    val members: Flow<List<MemberEntity>>,
    val jobs: Flow<List<JobEntity>>,
    val assignments: Flow<List<AssignmentEntity>>,
    /** Refreshes the cached team list so names are current; failures are ignored (offline keeps the cache). */
    val refreshMembers: suspend () -> Unit = {},
    /** Asks the sync engine for a pass now; it runs on its own and reports through the sync chip. */
    val requestSync: () -> Unit = {},
    val zone: ZoneId = ZoneId.systemDefault(),
    val now: () -> Long = System::currentTimeMillis,
    val ticks: Flow<Unit> = flow { while (true) { emit(Unit); kotlinx.coroutines.delay(30_000) } },
)

class TodayViewModel(private val d: TodayDeps) : ViewModel() {
    private val _state = MutableStateFlow(d.base)
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { d.refreshMembers() }
        viewModelScope.launch {
            combine(d.punches, d.members, d.jobs, d.assignments, d.ticks) { p, m, j, a, _ -> arrayOf(p, m, j, a) }
                .collect { rows ->
                    @Suppress("UNCHECKED_CAST")
                    val now = d.now()
                    _state.update { prev ->
                        TodayMapper.map(
                            prev, d.memberId, rows[0] as List<PunchEntity>, rows[1] as List<MemberEntity>,
                            rows[2] as List<JobEntity>, rows[3] as List<AssignmentEntity>,
                            Instant.ofEpochMilli(now).atZone(d.zone).toLocalDate().toString(), now,
                        )
                    }
                }
        }
    }

    /** Pull to refresh: a sync pass plus a fresh team list; the indicator stays until the team list is back (AC-F16). */
    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        d.requestSync()
        viewModelScope.launch {
            try {
                d.refreshMembers()
            } catch (_: java.io.IOException) {
                // Offline keeps the cached team; the sync chip says why.
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    /** Sync status comes from the engine, not the database. */
    fun setSync(phase: com.kurtz.homerecord.core.sync.SyncPhase, pending: Int) {
        _state.update { it.copy(syncPhase = phase, pendingChanges = pending) }
    }
}
