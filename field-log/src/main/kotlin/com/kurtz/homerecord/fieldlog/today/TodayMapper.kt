package com.kurtz.homerecord.fieldlog.today

import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.data.AssignmentEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.model.JobStatus
import com.kurtz.homerecord.core.model.JobTask
import com.kurtz.homerecord.core.model.MyTasksDue
import com.kurtz.homerecord.core.model.TaskDue
import java.time.LocalDate
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
        tasks: List<JobTask> = emptyList(),
        photos: List<PhotoEntity> = emptyList(),
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
        val live = liveJobs.map { TodayJob(it.id, it.name, it.address) }
        val myJobs = live.filter { it.id in myJobIds }
        return base.copy(
            hasJobs = liveJobs.isNotEmpty(),
            clock = TodayClock(status, mine?.jobName, now),
            jobs = myJobs,
            tasks = myTasks(tasks, memberId, jobs, today),
            photos = recentPhotos(photos, jobs, today),
            capture = TodayCapture.choose(mine?.jobId, myJobs, live),
            onSite = if (base.role == CompanyRole.Owner || base.role == CompanyRole.Office) onSite else emptyList(),
        )
    }

    /** The newest [RECENT_PHOTOS] photos on the company's jobs, newest first; an archived or deleted job's are left out. */
    fun recentPhotos(photos: List<PhotoEntity>, jobs: List<JobEntity>, today: String): List<TodayPhoto> {
        val names = jobs.filter { it.deletedAt == null && it.effectiveStatus(today) != JobStatus.ARCHIVED }.associate { it.id to it.name }
        return photos.asSequence()
            .filter { it.deletedAt == null && it.jobId in names }
            .sortedByDescending { it.takenAt }
            .take(RECENT_PHOTOS)
            .mapNotNull { p -> p.jobId?.let { job -> TodayPhoto(p.id, job, if (p.path.isEmpty()) "" else p.thumbPath ?: p.path, names[job]) } }
            .toList()
    }

    /** How many photos the Recent photos row shows; the query asks for a few more so archived jobs' can drop out. */
    const val RECENT_PHOTOS = 12

    /** My open tasks overdue or due today, overdue first (AC-F34); an archived job's tasks are left out. */
    fun myTasks(tasks: List<JobTask>, memberId: String, jobs: List<JobEntity>, today: String): List<TodayTask> {
        val day = runCatching { LocalDate.parse(today) }.getOrNull() ?: return emptyList()
        val names = jobs.filter { it.deletedAt == null && it.effectiveStatus(today) != JobStatus.ARCHIVED }.associate { it.id to it.name }
        val due = MyTasksDue.build(tasks.filter { it.jobId in names }, memberId, day)
        return due.all.map { TodayTask(it.id, it.jobId, it.title, names[it.jobId], it.due, TaskDue.of(it.due, day)) }
    }
}
