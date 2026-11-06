package com.kurtz.homerecord.housemanual.reminders

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.housemodel.reminders.ReminderDraft
import com.kurtz.homerecord.housemodel.reminders.ReminderRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * HM-Reminders (AC-H12). Opening the tab brings warranty reminders in step with the stored warranties; the open form
 * lives in [SavedStateHandle] so a half-typed reminder survives process death.
 */
class RemindersViewModel(
    private val repo: ReminderRepository,
    private val homeId: String,
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val today: () -> LocalDate = LocalDate::now,
    /** False for a viewer on a shared home: opening the tab must not write warranty reminders (AC-H16). */
    private val canEdit: Boolean = true,
) : ViewModel() {
    private val ui = MutableStateFlow(Ui(form = restore(), showErrors = saved[ERRORS] ?: false))
    private var saving = false

    val state: StateFlow<RemindersUiState> = combine(repo.observe(homeId), repo.observeZone(homeId), ui) { rows, zone, u ->
        RemindersUiState(
            loading = false, rows = rows, zone = zone, today = today(), form = u.form, showFormErrors = u.showErrors,
            snoozeMenuFor = u.snoozeFor?.takeIf { id -> rows.any { it.id == id } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState())

    init {
        if (canEdit) viewModelScope.launch { repo.syncAllWarranties(homeId) }
    }

    fun onEvent(e: RemindersEvent) {
        when (e) {
            is RemindersEvent.Done -> viewModelScope.launch { repo.done(e.id) }
            is RemindersEvent.OpenSnooze -> ui.update { it.copy(snoozeFor = e.id) }
            RemindersEvent.CloseSnooze -> ui.update { it.copy(snoozeFor = null) }
            is RemindersEvent.Snooze -> {
                ui.update { it.copy(snoozeFor = null) }
                viewModelScope.launch { repo.snooze(e.id, e.length) }
            }
            is RemindersEvent.PickZone -> viewModelScope.launch { repo.setClimateZone(homeId, e.zone) }
            RemindersEvent.OpenForm -> setForm(ReminderDraft(), false)
            is RemindersEvent.ChangeForm -> if (ui.value.form != null) setForm(e.draft, ui.value.showErrors)
            RemindersEvent.SaveForm -> {
                val draft = ui.value.form ?: return
                if (!draft.valid) { setForm(draft, true); return }
                if (saving) return
                saving = true
                viewModelScope.launch {
                    try {
                        if (repo.add(homeId, draft) == null) setForm(draft, true) else setForm(null, false)
                    } finally { saving = false }
                }
            }
            RemindersEvent.CloseForm -> setForm(null, false)
            is RemindersEvent.OpenItem -> Unit // the route's job
        }
    }

    private fun setForm(draft: ReminderDraft?, errors: Boolean) {
        ui.update { it.copy(form = draft, showErrors = errors) }
        saved[ERRORS] = errors
        if (draft == null) saved.remove<List<String>>(FORM)
        else saved[FORM] = arrayListOf(draft.title, draft.dueOn?.toString().orEmpty(), draft.intervalMonths?.toString().orEmpty())
    }

    private fun restore(): ReminderDraft? = saved.get<List<String>>(FORM)?.takeIf { it.size == 3 }?.let {
        ReminderDraft(title = it[0], dueOn = it[1].ifEmpty { null }?.let { d -> runCatching { LocalDate.parse(d) }.getOrNull() }, intervalMonths = it[2].toIntOrNull())
    }

    private data class Ui(val form: ReminderDraft? = null, val showErrors: Boolean = false, val snoozeFor: String? = null)

    private companion object {
        const val FORM = "reminder_form"
        const val ERRORS = "reminder_form_errors"
    }
}
