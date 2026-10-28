package com.kurtz.homerecord.core.model

import com.kurtz.homerecord.core.data.JobEntity

/**
 * A job's status as of today (AC-F6, AC-F8). The stored status is only a snapshot from the last save,
 * so it is read through here: Archived stays archived, Done with no end date stays done (no date says
 * otherwise), anything else follows the dates on the day asked.
 */
object JobStatusRule {
    fun effective(stored: String, startDate: String?, endDate: String?, today: String): JobStatus = when {
        stored == JobStatus.ARCHIVED.name -> JobStatus.ARCHIVED
        stored == JobStatus.DONE.name && endDate == null && (startDate == null || startDate <= today) -> JobStatus.DONE
        startDate != null && startDate > today -> JobStatus.UPCOMING
        endDate != null && endDate < today -> JobStatus.DONE
        else -> JobStatus.ACTIVE
    }
}

fun JobEntity.effectiveStatus(today: String): JobStatus = JobStatusRule.effective(status, startDate, endDate, today)
