package com.kurtz.homerecord.fieldlog.jobs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.ClientDao
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.JobDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class JobEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val draft: JobDraft = JobDraft(),
    val initial: JobDraft = JobDraft(),
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    val dirty: Boolean get() = draft != initial
    val canSave: Boolean get() = !loading && !saving && draft.canSave && dirty
}

sealed interface JobEditEvent {
    data class Name(val v: String) : JobEditEvent
    data class Client(val v: String) : JobEditEvent
    data class Address(val v: String) : JobEditEvent
    data class Start(val v: String?) : JobEditEvent
    data class End(val v: String?) : JobEditEvent
    data class Budget(val v: String) : JobEditEvent
    data class Notes(val v: String) : JobEditEvent
    data object Save : JobEditEvent
}

/** FL-JobEdit: create or change a job offline; the change is queued for sync (AC-F5). Unsaved text survives process death. */
class JobEditViewModel(
    private val companyId: String,
    private val jobId: String?,
    private val jobs: JobDao,
    private val clients: ClientDao,
    private val recordChange: suspend (entity: String, id: String) -> Unit,
    private val saved: SavedStateHandle,
    private val todayProvider: () -> String = { LocalDate.now().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow(JobEditUiState())
    val state: StateFlow<JobEditUiState> = _state.asStateFlow()
    private val newId = saved.get<String>(NEW_ID) ?: UUID.randomUUID().toString().also { saved[NEW_ID] = it }

    init {
        viewModelScope.launch {
            val existing = jobId?.let { jobs.get(it) }
            val clientName = existing?.clientId?.let { clients.get(it)?.name }
            val initial = existing?.let { JobDraft.from(it, clientName) } ?: JobDraft()
            val draft = saved.get<String>(DRAFT)?.let(::decode) ?: initial
            _state.value = JobEditUiState(loading = false, isNew = existing == null, draft = draft, initial = initial)
        }
    }

    fun onEvent(event: JobEditEvent) {
        when (event) {
            is JobEditEvent.Name -> edit { it.copy(name = event.v) }
            is JobEditEvent.Client -> edit { it.copy(clientName = event.v) }
            is JobEditEvent.Address -> edit { it.copy(address = event.v) }
            is JobEditEvent.Start -> edit { it.copy(startDate = event.v) }
            is JobEditEvent.End -> edit { it.copy(endDate = event.v) }
            is JobEditEvent.Budget -> edit { it.copy(budgetHoursText = event.v) }
            is JobEditEvent.Notes -> edit { it.copy(notes = event.v) }
            JobEditEvent.Save -> save()
        }
    }

    private fun edit(change: (JobDraft) -> JobDraft) {
        _state.update { it.copy(draft = change(it.draft)) }
        saved[DRAFT] = encode(_state.value.draft)
    }

    private fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val now = clock()
            val id = jobId ?: newId
            val typed = s.draft.clientName.trim()
            val clientId = if (typed.isEmpty()) {
                null
            } else {
                val match = clients.listFor(companyId).firstOrNull { it.name.equals(typed, ignoreCase = true) }
                match?.id ?: UUID.randomUUID().toString().also { cid ->
                    clients.upsert(ClientEntity(id = cid, companyId = companyId, name = typed, createdAt = now, updatedAt = now))
                    recordChange("client", cid)
                }
            }
            val entity = s.draft.toEntity(jobs.get(id), id, companyId, clientId, todayProvider(), now)
            jobs.upsert(entity)
            recordChange("job", id)
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    // Fields are joined with the unit separator; none of them can type it on a phone keyboard.
    private fun encode(d: JobDraft) = listOf(d.name, d.clientName, d.address, d.startDate.orEmpty(), d.endDate.orEmpty(), d.budgetHoursText, d.notes)
        .joinToString(SEP)

    private fun decode(raw: String): JobDraft? {
        val p = raw.split(SEP)
        if (p.size != 7) return null
        return JobDraft(p[0], p[1], p[2], p[3].ifEmpty { null }, p[4].ifEmpty { null }, p[5], p[6])
    }

    private companion object {
        const val DRAFT = "draft"
        const val NEW_ID = "newId"
        const val SEP = "\u001F"
    }
}
