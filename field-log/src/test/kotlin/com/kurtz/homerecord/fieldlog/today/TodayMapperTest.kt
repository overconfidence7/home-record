package com.kurtz.homerecord.fieldlog.today

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.data.AssignmentEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.punch.ClockStatus
import org.junit.Test

class TodayMapperTest {
    private val now = 1_761_400_000_000L
    private val hour = 3_600_000L
    private val members = listOf(member("me", "Maria Soto"), member("dev", "Dev Patel"))
    private val jobs = listOf(job("j1", "Kitchen Remodel"), job("j2", "Deck Rebuild"), job("j3", "Old Garage", status = "ARCHIVED"))

    private fun member(id: String, name: String) = MemberEntity(id = id, companyId = "c", name = name, createdAt = 0, updatedAt = 0)
    private fun job(id: String, name: String, status: String = "ACTIVE") = JobEntity(id = id, companyId = "c", name = name, status = status, createdAt = 0, updatedAt = 0)
    private fun assign(job: String, member: String, date: String) = AssignmentEntity("$job-$member-$date", job, member, date, 0, 0)
    private fun punch(member: String, kind: String, at: Long, job: String? = "j1", lat: Double? = null, lng: Double? = null) =
        PunchEntity(id = "$member$kind$at", companyId = "c", memberId = member, jobId = job, kind = kind, at = at, lat = lat, lng = lng, createdAt = at, updatedAt = at)

    private fun map(role: CompanyRole, punches: List<PunchEntity>, assignments: List<AssignmentEntity> = emptyList()) =
        TodayMapper.map(TodayUiState("Kurtz Electric", role), "me", punches, members, jobs, assignments, "2026-10-25", now)

    // AC-F16
    @Test fun ownerSeesOpenShiftsWithPinAndCrewDoesNot() {
        val punches = listOf(punch("dev", PunchKind.IN, now - 2 * hour, "j2", 40.0, -75.0), punch("me", PunchKind.IN, now - hour))
        val owner = map(CompanyRole.Owner, punches)
        assertThat(owner.onSite.map { it.name }).containsExactly("Dev Patel", "Maria Soto").inOrder()
        assertThat(owner.onSite.first().hasPin).isTrue()
        assertThat(owner.onSite.first().jobName).isEqualTo("Deck Rebuild")
        assertThat(map(CompanyRole.Crew, punches).onSite).isEmpty()
    }

    // AC-F16
    @Test fun clockCardFollowsMyOwnShiftAndIgnoresOthers() {
        assertThat(map(CompanyRole.Crew, listOf(punch("dev", PunchKind.IN, now - hour))).clock?.status).isEqualTo(ClockStatus.Out)
        val working = map(CompanyRole.Crew, listOf(punch("me", PunchKind.IN, now - hour))).clock
        assertThat(working?.status).isEqualTo(ClockStatus.Working("j1", now - hour))
        assertThat(working?.jobName).isEqualTo("Kitchen Remodel")
        val onBreak = map(CompanyRole.Crew, listOf(punch("me", PunchKind.IN, now - hour), punch("me", PunchKind.BREAK_START, now - 600_000L))).clock
        assertThat(onBreak?.status).isInstanceOf(ClockStatus.OnBreak::class.java)
    }

    @Test fun myJobsTodayAreAssignedActiveJobsOnly() {
        val assignments = listOf(assign("j1", "me", "2026-10-25"), assign("j2", "me", "2026-10-26"), assign("j3", "me", "2026-10-25"), assign("j2", "dev", "2026-10-25"))
        val state = map(CompanyRole.Crew, emptyList(), assignments)
        assertThat(state.jobs.map { it.id }).containsExactly("j1")
        assertThat(state.hasJobs).isTrue()
    }

    // AC-F8: a job saved as Upcoming shows on Today once its start date comes, without a re-save
    @Test fun anUpcomingJobWhoseStartDateCameIsTodaysJob() {
        val saved = listOf(
            JobEntity(id = "j4", companyId = "c", name = "Basement finish", status = "UPCOMING", startDate = "2026-10-25", createdAt = 0, updatedAt = 0),
            JobEntity(id = "j5", companyId = "c", name = "Porch rebuild", status = "UPCOMING", startDate = "2026-11-09", createdAt = 0, updatedAt = 0),
        )
        val assignments = listOf(assign("j4", "me", "2026-10-25"), assign("j5", "me", "2026-10-25"))
        val state = TodayMapper.map(TodayUiState("Kurtz Electric", CompanyRole.Crew), "me", emptyList(), members, saved, assignments, "2026-10-25", now)
        assertThat(state.jobs.map { it.id }).containsExactly("j4")
        assertThat(state.hasJobs).isTrue()
    }

    @Test fun noActiveJobsKeepsTheFirstRunEmptyState() {
        val state = TodayMapper.map(TodayUiState("Kurtz Electric", CompanyRole.Owner), "me", emptyList(), members, emptyList(), emptyList(), "2026-10-25", now)
        assertThat(state.hasJobs).isFalse()
    }

    private fun task(id: String, job: String, due: String?, assignee: String = "me", done: Boolean = false, order: Int = 0) =
        com.kurtz.homerecord.core.model.JobTask(
            id = id, jobId = job, title = "Task $id", assigneeId = assignee, due = due?.let(java.time.LocalDate::parse), done = done, sortOrder = order,
        )

    // AC-F34: My tasks shows my open tasks overdue (oldest first) then due today, with the job's name; not later,
    // undated, done, someone else's or on an archived job.
    @Test fun myTasksAreOverdueThenDueTodayWithTheJobName() {
        val tasks = listOf(
            task("today", "j1", "2026-10-25"),
            task("late2", "j2", "2026-10-24"),
            task("late1", "j1", "2026-10-20"),
            task("later", "j1", "2026-10-26"),
            task("undated", "j1", null),
            task("done", "j1", "2026-10-25", done = true),
            task("theirs", "j1", "2026-10-25", assignee = "dev"),
            task("archived", "j3", "2026-10-25"),
        )
        val state = TodayMapper.map(TodayUiState("Kurtz Electric", CompanyRole.Crew), "me", emptyList(), members, jobs, emptyList(), "2026-10-25", now, tasks)
        assertThat(state.tasks.map { it.id }).containsExactly("late1", "late2", "today").inOrder()
        assertThat(state.tasks.map { it.jobName }).containsExactly("Kitchen Remodel", "Deck Rebuild", "Kitchen Remodel").inOrder()
        assertThat(state.tasks.map { it.dueKind }).containsExactly(
            com.kurtz.homerecord.core.model.TaskDue.OVERDUE, com.kurtz.homerecord.core.model.TaskDue.OVERDUE, com.kurtz.homerecord.core.model.TaskDue.TODAY,
        ).inOrder()
    }

    private fun photo(id: String, job: String?, takenAt: Long, path: String = "/m/$id.jpg", thumb: String? = "/m/${id}_t.jpg", deleted: Long? = null) =
        com.kurtz.homerecord.core.data.PhotoEntity(
            id = id, homeId = "h", jobId = job, path = path, thumbPath = thumb, takenAt = takenAt, createdAt = takenAt, updatedAt = takenAt, deletedAt = deleted,
        )

    // AC-F24: Recent photos are the newest on live jobs, thumbnails first, waiting ones without a path, capped at twelve.
    @Test fun recentPhotosAreNewestOnLiveJobsOnly() {
        val photos = listOf(
            photo("old", "j1", 1), photo("new", "j2", 9, thumb = null), photo("archived", "j3", 8),
            photo("elsewhere", "j2", 7, path = ""), photo("gone", "j1", 10, deleted = 10), photo("loose", null, 11),
        )
        val recent = TodayMapper.recentPhotos(photos, jobs, "2026-10-25")
        assertThat(recent.map { it.id }).containsExactly("new", "elsewhere", "old").inOrder()
        assertThat(recent[0]).isEqualTo(TodayPhoto("new", "j2", "/m/new.jpg", "Deck Rebuild"))
        assertThat(recent[1].imagePath).isEmpty()
        assertThat(recent[2].imagePath).isEqualTo("/m/old_t.jpg")
        val many = (1..20).map { photo("p$it", "j1", it.toLong()) }
        assertThat(TodayMapper.recentPhotos(many, jobs, "2026-10-25")).hasSize(TodayMapper.RECENT_PHOTOS)
    }

    // AC-F24: clocked in on a job, the camera button shoots for it without asking.
    @Test fun theCameraButtonFollowsMyShift() {
        val state = map(CompanyRole.Crew, listOf(punch("me", PunchKind.IN, now - hour, "j2")))
        assertThat(state.capture.target).isEqualTo("j2")
        assertThat(map(CompanyRole.Crew, emptyList()).capture.options.map { it.id }).containsExactly("j1", "j2").inOrder()
    }

    // AC-F41: crew see their next day on Today ("Tomorrow: Deck Rebuild"); archived jobs and other people are left out
    @Test fun nextDayIsTomorrowOrTheNextScheduledDay() {
        fun a(id: String, job: String, member: String, date: String) =
            com.kurtz.homerecord.core.data.AssignmentEntity(id, job, member, date, createdAt = 0, updatedAt = 0)
        val today = "2026-11-04"
        val tomorrow = TodayMapper.nextDay("me", listOf(a("a1", "j2", "me", "2026-11-05"), a("a2", "j1", "dev", "2026-11-05")), jobs, today)
        assertThat(tomorrow).isEqualTo(TodayNextDay(java.time.LocalDate.of(2026, 11, 5), tomorrow = true, jobNames = listOf("Deck Rebuild")))
        val later = TodayMapper.nextDay("me", listOf(a("a1", "j3", "me", "2026-11-05"), a("a2", "j1", "me", "2026-11-07")), jobs, today)
        assertThat(later?.tomorrow).isFalse()
        assertThat(later?.jobNames).containsExactly("Kitchen Remodel")
        assertThat(TodayMapper.nextDay("me", listOf(a("a1", "j1", "me", today)), jobs, today)).isNull()
    }
}
