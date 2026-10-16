package com.kurtz.homerecord.fieldlog.jobedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.model.Job
import com.kurtz.homerecord.core.repo.JobRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [jobId] null = new job. Rooms are chosen at creation; an existing job edits its details and archive flag. */
class JobEditViewModel(
    private val jobs: JobRepository,
    private val jobId: String?,
    private val roomLabel: (QuickRoom) -> String,
) : ViewModel() {
    private val _state = MutableStateFlow(JobEditUiState(isEdit = jobId != null, loaded = jobId == null))
    val state: StateFlow<JobEditUiState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    private var existing: Job? = null

    init {
        if (jobId != null) viewModelScope.launch {
            val job = jobs.getJob(jobId)
            existing = job
            _state.update {
                if (job == null) it.copy(loaded = false) else it.copy(
                    loaded = true, name = job.name, address = job.address.orEmpty(),
                    clientName = job.clientName.orEmpty(), clientContact = job.clientContact.orEmpty(),
                    archived = job.archived,
                )
            }
        }
    }

    fun onEvent(event: JobEditEvent) {
        when (event) {
            is JobEditEvent.NameChanged -> _state.update { it.copy(name = event.value) }
            is JobEditEvent.AddressChanged -> _state.update { it.copy(address = event.value) }
            is JobEditEvent.ClientNameChanged -> _state.update { it.copy(clientName = event.value) }
            is JobEditEvent.ClientContactChanged -> _state.update { it.copy(clientContact = event.value) }
            is JobEditEvent.ArchivedChanged -> _state.update { it.copy(archived = event.value) }
            is JobEditEvent.ToggleRoom -> _state.update {
                it.copy(selected = if (event.room in it.selected) it.selected - event.room else it.selected + event.room)
            }
            is JobEditEvent.CustomInputChanged -> _state.update { it.copy(customInput = event.value) }
            JobEditEvent.AddCustomRoom -> _state.update {
                val name = it.customInput.trim()
                val exists = it.customRooms.any { r -> r.equals(name, ignoreCase = true) }
                if (name.isEmpty()) it
                else it.copy(customRooms = if (exists) it.customRooms else it.customRooms + name, customInput = "")
            }
            is JobEditEvent.RemoveCustomRoom -> _state.update { it.copy(customRooms = it.customRooms - event.name) }
            JobEditEvent.Save -> save()
        }
    }

    private fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val current = existing
            if (jobId == null || current == null) {
                val rooms = QuickRoom.entries.filter { it in s.selected }.map(roomLabel) + s.customRooms
                jobs.createJob(s.name, s.address, s.clientName, s.clientContact, rooms)
            } else {
                jobs.updateJob(
                    current.copy(
                        name = s.name, address = s.address, clientName = s.clientName,
                        clientContact = s.clientContact, archived = s.archived,
                    ),
                )
            }
            _done.send(Unit)
        }
    }
}
