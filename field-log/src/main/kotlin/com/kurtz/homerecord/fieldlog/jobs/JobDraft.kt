package com.kurtz.homerecord.fieldlog.jobs

import com.kurtz.homerecord.core.data.JobEntity
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The JobEdit form as typed. Validation and mapping to [JobEntity] live here so they are unit tested (AC-F5). */
data class JobDraft(
    val name: String = "",
    val clientName: String = "",
    val address: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val budgetHoursText: String = "",
    val notes: String = "",
    /** Room names to work on; they become unplaced rooms on the job's home (AC-F5, AC-F48). */
    val rooms: List<String> = emptyList(),
    /** Ordered phase names, e.g. Demo, Rough-in, Finish (AC-F5). */
    val phases: List<String> = emptyList(),
    /** Office addresses that get the daily log and reports for this job (AC-F5). */
    val officeEmails: List<String> = emptyList(),
    /** Member ids assigned to the job (AC-F5). */
    val crew: List<String> = emptyList(),
) {
    fun withCrewToggled(memberId: String) = copy(crew = if (memberId in crew) crew - memberId else crew + memberId)


    val budgetHours: Int? get() = budgetHoursText.trim().toIntOrNull()?.takeIf { it in 0..MAX_BUDGET_HOURS }
    val budgetInvalid: Boolean get() = budgetHoursText.isNotBlank() && budgetHours == null
    val datesInvalid: Boolean get() = startDate != null && endDate != null && endDate < startDate
    fun withRoom(name: String): JobDraft {
        val n = name.trim()
        return if (n.isEmpty() || rooms.any { it.equals(n, ignoreCase = true) }) this else copy(rooms = rooms + n)
    }

    fun withoutRoom(name: String) = copy(rooms = rooms.filterNot { it.equals(name, ignoreCase = true) })

    fun withPhase(name: String): JobDraft {
        val n = name.trim()
        return if (n.isEmpty() || phases.any { it.equals(n, ignoreCase = true) }) this else copy(phases = phases + n)
    }

    fun withoutPhase(name: String) = copy(phases = phases.filterNot { it.equals(name, ignoreCase = true) })

    fun withOfficeEmail(email: String): JobDraft {
        val e = email.trim()
        return if (!isEmail(e) || officeEmails.any { it.equals(e, ignoreCase = true) }) this else copy(officeEmails = officeEmails + e)
    }

    fun withoutOfficeEmail(email: String) = copy(officeEmails = officeEmails.filterNot { it.equals(email, ignoreCase = true) })

    val canSave: Boolean get() = name.isNotBlank() && !budgetInvalid && !datesInvalid

    /** Status follows the dates: a future start is Upcoming, a past end is Done, anything else Active. */
    fun statusFor(today: String): String = when {
        startDate != null && startDate > today -> "UPCOMING"
        endDate != null && endDate < today -> "DONE"
        else -> "ACTIVE"
    }

    fun toEntity(existing: JobEntity?, id: String, companyId: String, clientId: String?, today: String, now: Long, homeId: String? = existing?.homeId): JobEntity =
        (existing ?: JobEntity(id = id, companyId = companyId, name = name.trim(), createdAt = now, updatedAt = now)).copy(
            name = name.trim(),
            clientId = clientId,
            homeId = homeId,
            address = address.trim().ifBlank { null },
            // A changed address invalidates old coordinates; they are resolved again when online.
            lat = if (existing?.address == address.trim().ifBlank { null }) existing?.lat else null,
            lng = if (existing?.address == address.trim().ifBlank { null }) existing?.lng else null,
            status = if (existing?.status == "ARCHIVED") "ARCHIVED" else statusFor(today),
            startDate = startDate,
            endDate = endDate,
            budgetHours = budgetHours,
            notes = notes.trim().ifBlank { null },
            phasesJson = Json.encodeToString(phases),
            officeEmailsJson = Json.encodeToString(officeEmails),
            updatedAt = now,
        )

    companion object {
        const val MAX_BUDGET_HOURS = 100_000

        fun from(job: JobEntity, clientName: String?, rooms: List<String> = emptyList(), crew: List<String> = emptyList()) = JobDraft(
            name = job.name,
            clientName = clientName.orEmpty(),
            address = job.address.orEmpty(),
            startDate = job.startDate,
            endDate = job.endDate,
            budgetHoursText = job.budgetHours?.toString().orEmpty(),
            notes = job.notes.orEmpty(),
            rooms = rooms,
            phases = decodeList(job.phasesJson),
            officeEmails = decodeList(job.officeEmailsJson),
            crew = crew,
        )

        fun isEmail(v: String) = v.length <= 254 && EMAIL.matches(v)

        private val EMAIL = Regex("^[^@\\s]+@[^@\\s.]+(\\.[^@\\s.]+)+$")

        private fun decodeList(raw: String): List<String> =
            try { Json.decodeFromString<List<String>>(raw) } catch (_: SerializationException) { emptyList() }
    }
}
