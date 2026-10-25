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
    val zone: ZoneId = ZoneId.systemDefault(),
    val now: () -> Long = System::currentTimeMillis,
    val ticks: Flow<Unit> = flow { while (true) { emit(Unit); kotlinx.coroutines.delay(30_000) } },
)

class TodayViewModel(private val d: TodayDeps) : ViewModel() {
    private val _state = MutableStateFlow(d.base)
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(d.punches, d.members, d.jobs, d.assignments, d.ticks) { p, m, j, a, _ -> arrayOf(p, m, j, a) }
                .collect { rows ->
                    @Suppress("UNCHECKED_CAST")
                    val now = d.now()
                    _state.value = TodayMapper.map(
                        _state.value, d.memberId, rows[0] as List<PunchEntity>, rows[1] as List<MemberEntity>,
                        rows[2] as List<JobEntity>, rows[3] as List<AssignmentEntity>,
                        Instant.ofEpochMilli(now).atZone(d.zone).toLocalDate().toString(), now,
                    )
                }
        }
    }

    /** Sync status comes from the engine, not the database. */
    fun setSync(phase: com.kurtz.homerecord.core.sync.SyncPhase, pending: Int) {
        _state.value = _state.value.copy(syncPhase = phase, pendingChanges = pending)
    }
}
