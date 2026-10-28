package com.kurtz.homerecord.fieldlog.today

import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.data.AssignmentEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.model.JobStatus
import com.kurtz.homerecord.core.model.OnSiteBuilder
import com.kurtz.homerecord.core.model.effectiveStatus
import com.kurtz.homerecord.core.punch.ClockStatus

/** Turns synced rows into what FL-Today shows (AC-F16): my clock status, my jobs today, and for admins who is on site. */
object TodayMapper {
    fun map(
        base: TodayUiState,
        memberId: String,
        punches: List<PunchEntity>,
        members: List<MemberEntity>,
        jobs: List<JobEntity>,
        assignments: List<AssignmentEntity>,
        today: String,
        now: Long,
    ): TodayUiState {
        val liveJobs = jobs.filter { it.effectiveStatus(today) == JobStatus.ACTIVE }
        val jobNames = jobs.associate { it.id to it.name }
        val onSite = OnSiteBuilder.build(punches, members.associate { it.id to it.name }, jobNames, now)
        val mine = onSite.firstOrNull { it.memberId == memberId }
        val status = when {
            mine == null -> ClockStatus.Out
            mine.onBreak -> ClockStatus.OnBreak(mine.jobId, mine.since, mine.since)
            else -> ClockStatus.Working(mine.jobId, mine.since)
        }
        val myJobIds = assignments.filter { it.memberId == memberId && it.date == today }.map { it.jobId }.toSet()
        return base.copy(
            hasJobs = liveJobs.isNotEmpty(),
            clock = TodayClock(status, mine?.jobName, now),
            jobs = liveJobs.filter { it.id in myJobIds }.map { TodayJob(it.id, it.name, it.address) },
            onSite = if (base.role == CompanyRole.Owner || base.role == CompanyRole.Office) onSite else emptyList(),
        )
    }
}
