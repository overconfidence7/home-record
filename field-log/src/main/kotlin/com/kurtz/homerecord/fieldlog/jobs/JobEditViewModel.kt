package com.kurtz.homerecord.fieldlog.jobs

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.ClientDao
import com.kurtz.homerecord.core.data.FloorDao
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeDao
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.JobCrewDao
import com.kurtz.homerecord.core.data.JobCrewEntity
import com.kurtz.homerecord.core.data.JobDao
import com.kurtz.homerecord.core.data.MemberDao
import com.kurtz.homerecord.core.data.RoomDao
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.data.Sources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** A team member who can be put on the job. */
data class CrewOption(val id: String, val name: String)

data class JobEditUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val draft: JobDraft = JobDraft(),
    val initial: JobDraft = JobDraft(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val savedAddress: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val members: List<CrewOption> = emptyList(),
) {
    /** Coordinates for the map link; only while the address still matches the one that was resolved (AC-F5). */
    val mapPoint: Pair<Double, Double>? get() {
        val la = lat ?: return null
        val ln = lng ?: return null
        return if (draft.address.trim() == savedAddress) la to ln else null
    }
    val dirty: Boolean get() = draft != initial
    val canSave: Boolean get() = !loading && !saving && draft.canSave && dirty
}

sealed interface JobEditEvent {
    data class Name(val v: String) : JobEditEvent
    data class Client(val v: String) : JobEditEvent
    data class ClientPhone(val v: String) : JobEditEvent
    data class ClientEmail(val v: String) : JobEditEvent
    data class Address(val v: String) : JobEditEvent
    data class Start(val v: String?) : JobEditEvent
    data class End(val v: String?) : JobEditEvent
    data class Budget(val v: String) : JobEditEvent
    data class Notes(val v: String) : JobEditEvent
    data class AddRoom(val v: String) : JobEditEvent
    data class RemoveRoom(val v: String) : JobEditEvent
    data class AddPhase(val v: String) : JobEditEvent
    data class RemovePhase(val v: String) : JobEditEvent
    data class AddOfficeEmail(val v: String) : JobEditEvent
    data class RemoveOfficeEmail(val v: String) : JobEditEvent
    data class ToggleCrew(val memberId: String) : JobEditEvent
    data object Save : JobEditEvent
}

/** FL-JobEdit: create or change a job offline; the change is queued for sync (AC-F5). Unsaved text survives process death. */
class JobEditViewModel(
    private val companyId: String,
    private val jobId: String?,
    private val jobs: JobDao,
    private val clients: ClientDao,
    private val homes: HomeDao,
    private val floors: FloorDao,
    private val rooms: RoomDao,
    private val crew: JobCrewDao,
    private val members: MemberDao,
    private val refreshMembers: suspend () -> Unit,
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
            val client = existing?.clientId?.let { clients.get(it) }
            val roomNames = existing?.homeId?.let { jobRooms(it).map { r -> r.name } }.orEmpty()
            val crewIds = jobId?.let { crew.listFor(it).map { c -> c.memberId } }.orEmpty()
            val initial = existing?.let { JobDraft.from(it, client, roomNames, crewIds) } ?: JobDraft()
            val draft = saved.get<String>(DRAFT)?.let(::decode) ?: initial
            _state.value = JobEditUiState(
                loading = false, isNew = existing == null, draft = draft, initial = initial,
                savedAddress = existing?.address.orEmpty().trim(), lat = existing?.lat, lng = existing?.lng,
                members = crewOptions(),
            )
            // The team list is fetched when online; the cached one is already showing.
            refreshMembers()
            _state.update { it.copy(members = crewOptions()) }
        }
    }

    fun onEvent(event: JobEditEvent) {
        when (event) {
            is JobEditEvent.Name -> edit { it.copy(name = event.v) }
            is JobEditEvent.Client -> edit { it.copy(clientName = event.v) }
            is JobEditEvent.ClientPhone -> edit { it.copy(clientPhone = event.v) }
            is JobEditEvent.ClientEmail -> edit { it.copy(clientEmail = event.v) }
            is JobEditEvent.Address -> edit { it.copy(address = event.v) }
            is JobEditEvent.Start -> edit { it.copy(startDate = event.v) }
            is JobEditEvent.End -> edit { it.copy(endDate = event.v) }
            is JobEditEvent.Budget -> edit { it.copy(budgetHoursText = event.v) }
            is JobEditEvent.Notes -> edit { it.copy(notes = event.v) }
            is JobEditEvent.AddRoom -> edit { it.withRoom(event.v) }
            is JobEditEvent.RemoveRoom -> edit { it.withoutRoom(event.v) }
            is JobEditEvent.AddPhase -> edit { it.withPhase(event.v) }
            is JobEditEvent.RemovePhase -> edit { it.withoutPhase(event.v) }
            is JobEditEvent.AddOfficeEmail -> edit { it.withOfficeEmail(event.v) }
            is JobEditEvent.RemoveOfficeEmail -> edit { it.withoutOfficeEmail(event.v) }
            is JobEditEvent.ToggleCrew -> edit { it.withCrewToggled(event.memberId) }
            JobEditEvent.Save -> save()
        }
    }

    private suspend fun crewOptions() = members.listFor(companyId).map { CrewOption(it.id, it.name) }.sortedBy { it.name.lowercase() }

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
            val before = jobs.get(id)
            val typed = s.draft.clientName.trim()
            val match = clients.listFor(companyId).firstOrNull { it.name.equals(typed, ignoreCase = true) }
            val client = s.draft.clientFor(match, sameClient = match != null && match.id == before?.clientId, UUID.randomUUID().toString(), companyId, now)
            if (client != null && client != match) {
                clients.upsert(client.copy(updatedAt = now))
                recordChange("client", client.id)
            }
            val clientId = client?.id
            val homeId = syncRooms(before, s.draft, now)
            val entity = s.draft.toEntity(before, id, companyId, clientId, todayProvider(), now, homeId)
            jobs.upsert(entity)
            recordChange("job", id)
            syncCrew(id, s.draft.crew, now)
            _state.update { it.copy(saving = false, saved = true) }
        }
    }

    private suspend fun syncCrew(id: String, wanted: List<String>, now: Long) {
        val current = crew.listFor(id)
        current.filter { it.memberId !in wanted }.forEach { crew.softDelete(it.id, now); recordChange("job_crew", it.id) }
        wanted.filter { m -> current.none { it.memberId == m } }.forEach { m ->
            val rowId = UUID.randomUUID().toString()
            crew.upsert(JobCrewEntity(id = rowId, jobId = id, memberId = m, createdAt = now, updatedAt = now))
            recordChange("job_crew", rowId)
        }
    }

    private suspend fun jobRooms(homeId: String): List<RoomEntity> =
        floors.listFor(homeId).flatMap { rooms.listFor(it.id) }

    /**
     * Rooms typed on the form live on the job's own home (created with its first room) as unplaced
     * rooms (empty polygon) until they are dragged onto the plan (AC-F48). Returns the job's homeId.
     */
    private suspend fun syncRooms(existing: com.kurtz.homerecord.core.data.JobEntity?, draft: JobDraft, now: Long): String? {
        var homeId = existing?.homeId
        val current = homeId?.let { jobRooms(it) }.orEmpty()
        val wanted = draft.rooms
        current.filter { r -> wanted.none { it.equals(r.name, ignoreCase = true) } }.forEach { rooms.softDelete(it.id, now); recordChange("room", it.id) }
        val toAdd = wanted.filter { n -> current.none { it.name.equals(n, ignoreCase = true) } }
        if (toAdd.isEmpty()) return homeId
        if (homeId == null) {
            homeId = UUID.randomUUID().toString()
            homes.upsert(HomeEntity(id = homeId, name = draft.name.trim(), address = draft.address.trim().ifBlank { null }, createdAt = now, updatedAt = now, source = Sources.FIELD_LOG))
            recordChange("home", homeId)
        }
        val floor = floors.listFor(homeId).firstOrNull() ?: FloorEntity(
            id = UUID.randomUUID().toString(), homeId = homeId, sortOrder = 0, name = "Main", createdAt = now, updatedAt = now, source = Sources.FIELD_LOG,
        ).also { floors.upsert(it); recordChange("floor", it.id) }
        toAdd.forEach { n ->
            val roomId = UUID.randomUUID().toString()
            rooms.upsert(RoomEntity(id = roomId, floorId = floor.id, name = n, createdAt = now, updatedAt = now, source = Sources.FIELD_LOG))
            recordChange("room", roomId)
        }
        return homeId
    }

    // Fields are joined with the unit separator; none of them can type it on a phone keyboard.
    private fun encode(d: JobDraft) = listOf(d.name, d.clientName, d.clientPhone, d.clientEmail, d.address, d.startDate.orEmpty(), d.endDate.orEmpty(), d.budgetHoursText, d.notes, d.rooms.joinToString(ROOM_SEP), d.phases.joinToString(ROOM_SEP), d.officeEmails.joinToString(ROOM_SEP), d.crew.joinToString(ROOM_SEP))
        .joinToString(SEP)

    private fun decode(raw: String): JobDraft? {
        val p = raw.split(SEP)
        if (p.size != 13) return null
        return JobDraft(p[0], p[1], p[2], p[3], p[4], p[5].ifEmpty { null }, p[6].ifEmpty { null }, p[7], p[8], p[9].items(), p[10].items(), p[11].items(), p[12].items())
    }

    private fun String.items(): List<String> = split(ROOM_SEP).filter { it.isNotEmpty() }

    private companion object {
        const val DRAFT = "draft"
        const val NEW_ID = "newId"
        const val SEP = "\u001F"
        const val ROOM_SEP = "\u001E"
    }
}
