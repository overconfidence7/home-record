package com.kurtz.homerecord.fieldlog.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.AssignmentEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.model.JobTask
import com.kurtz.homerecord.fieldlog.tasks.TaskUndo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    /** Tasks assigned to me on the company's jobs; the mapper keeps those overdue or due today (AC-F34). */
    val tasks: Flow<List<JobTask>> = flowOf(emptyList()),
    /** Ticks a task done or open again; false when nothing changed (JobTasks.setDone). */
    val setTaskDone: suspend (id: String, done: Boolean) -> Boolean = { _, _ -> false },
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
            val tasksAndTicks = combine(d.tasks, d.ticks) { t, _ -> t }
            combine(d.punches, d.members, d.jobs, d.assignments, tasksAndTicks) { p, m, j, a, t -> arrayOf(p, m, j, a, t) }
                .collect { rows ->
                    @Suppress("UNCHECKED_CAST")
                    val now = d.now()
                    _state.update { prev ->
                        TodayMapper.map(
                            prev, d.memberId, rows[0] as List<PunchEntity>, rows[1] as List<MemberEntity>,
                            rows[2] as List<JobEntity>, rows[3] as List<AssignmentEntity>,
                            Instant.ofEpochMilli(now).atZone(d.zone).toLocalDate().toString(), now,
                            rows[4] as List<JobTask>,
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

    /** Ticks one of my tasks done from Today; the snackbar then offers Undo (AC-F34). */
    fun completeTask(id: String) {
        val title = _state.value.tasks.firstOrNull { it.id == id }?.title ?: return
        viewModelScope.launch {
            if (d.setTaskDone(id, true)) _state.update { it.copy(taskUndo = TaskUndo(id, title)) }
        }
    }

    fun undoTask() {
        val undo = _state.value.taskUndo ?: return
        _state.update { it.copy(taskUndo = null) }
        viewModelScope.launch { d.setTaskDone(undo.id, false) }
    }

    fun dismissTaskUndo() = _state.update { it.copy(taskUndo = null) }

    /** Sync status comes from the engine, not the database. */
    fun setSync(
        phase: com.kurtz.homerecord.core.sync.SyncPhase,
        pending: Int,
        refused: List<com.kurtz.homerecord.core.sync.RefusedChange> = emptyList(),
    ) {
        _state.update { it.copy(syncPhase = phase, pendingChanges = pending, refusedChanges = refused) }
    }
}
