package com.kurtz.homerecord.fieldlog.jobs

import com.kurtz.homerecord.core.data.JobEntity

/** The JobEdit form as typed. Validation and mapping to [JobEntity] live here so they are unit tested (AC-F5). */
data class JobDraft(
    val name: String = "",
    val clientName: String = "",
    val address: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val budgetHoursText: String = "",
    val notes: String = "",
) {
    val budgetHours: Int? get() = budgetHoursText.trim().toIntOrNull()?.takeIf { it in 0..MAX_BUDGET_HOURS }
    val budgetInvalid: Boolean get() = budgetHoursText.isNotBlank() && budgetHours == null
    val datesInvalid: Boolean get() = startDate != null && endDate != null && endDate < startDate
    val canSave: Boolean get() = name.isNotBlank() && !budgetInvalid && !datesInvalid

    /** Status follows the dates: a future start is Upcoming, a past end is Done, anything else Active. */
    fun statusFor(today: String): String = when {
        startDate != null && startDate > today -> "UPCOMING"
        endDate != null && endDate < today -> "DONE"
        else -> "ACTIVE"
    }

    fun toEntity(existing: JobEntity?, id: String, companyId: String, clientId: String?, today: String, now: Long): JobEntity =
        (existing ?: JobEntity(id = id, companyId = companyId, name = name.trim(), createdAt = now, updatedAt = now)).copy(
            name = name.trim(),
            clientId = clientId,
            address = address.trim().ifBlank { null },
            // A changed address invalidates old coordinates; they are resolved again when online.
            lat = if (existing?.address == address.trim().ifBlank { null }) existing?.lat else null,
            lng = if (existing?.address == address.trim().ifBlank { null }) existing?.lng else null,
            status = if (existing?.status == "ARCHIVED") "ARCHIVED" else statusFor(today),
            startDate = startDate,
            endDate = endDate,
            budgetHours = budgetHours,
            notes = notes.trim().ifBlank { null },
            updatedAt = now,
        )

    companion object {
        const val MAX_BUDGET_HOURS = 100_000

        fun from(job: JobEntity, clientName: String?) = JobDraft(
            name = job.name,
            clientName = clientName.orEmpty(),
            address = job.address.orEmpty(),
            startDate = job.startDate,
            endDate = job.endDate,
            budgetHoursText = job.budgetHours?.toString().orEmpty(),
            notes = job.notes.orEmpty(),
        )
    }
}
