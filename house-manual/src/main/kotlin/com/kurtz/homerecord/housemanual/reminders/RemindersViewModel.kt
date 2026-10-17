package com.kurtz.homerecord.housemanual.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.ClimateZone
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.MaintenanceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** HM-Reminders (AC-82): tasks sorted by nextDue with Done and Snooze; also hosts the climate-zone picker (AC-80). */
@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModel(
    private val homes: HomeRepository,
    private val tasks: MaintenanceRepository,
    private val highlightId: String? = null,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    val state: StateFlow<RemindersUiState> = homes.observeHome().flatMapLatest { home ->
        if (home == null) flowOf(RemindersUiState(loaded = true))
        else tasks.observeTasks(home.id).map { list ->
            val now = today().toString()
            RemindersUiState(
                loaded = true,
                items = list.sortedWith(compareBy({ it.nextDue }, { it.title.lowercase() }))
                    .map { ReminderItem(it, overdue = it.nextDue < now, dueToday = it.nextDue == now) },
                climateZone = ClimateZone.fromName(home.climateZone),
                highlightId = highlightId,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    private val _tasksCreated = Channel<Unit>(Channel.BUFFERED)

    /** Fires when picking a zone created tasks, so the route can ask for notification permission (AC-84). */
    val tasksCreated = _tasksCreated.receiveAsFlow()

    fun onEvent(event: RemindersEvent) {
        when (event) {
            is RemindersEvent.Done -> viewModelScope.launch { tasks.markDone(event.taskId) }
            is RemindersEvent.Snooze -> viewModelScope.launch { tasks.snooze(event.taskId) }
            is RemindersEvent.PickZone -> viewModelScope.launch {
                val home = homes.getHome() ?: return@launch
                if (tasks.applyClimateZone(home.id, event.zone).isNotEmpty()) _tasksCreated.send(Unit)
            }
        }
    }
}
