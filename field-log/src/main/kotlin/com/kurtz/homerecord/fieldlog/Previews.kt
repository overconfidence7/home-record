package com.kurtz.homerecord.fieldlog

import androidx.compose.ui.graphics.asImageBitmap
import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.company.OvertimeRule
import com.kurtz.homerecord.core.company.CompanyProfile
import com.kurtz.homerecord.core.company.TeamMember
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.company.CompanyScreen
import com.kurtz.homerecord.fieldlog.company.CompanyUiState
import com.kurtz.homerecord.fieldlog.company.TeamScreen
import com.kurtz.homerecord.fieldlog.company.TeamStatus
import com.kurtz.homerecord.fieldlog.company.TeamUiState
import com.kurtz.homerecord.fieldlog.company.WelcomeMode
import com.kurtz.homerecord.fieldlog.company.WelcomeScreen
import com.kurtz.homerecord.fieldlog.company.WelcomeUiState
import com.kurtz.homerecord.core.model.JobStatus
import com.kurtz.homerecord.core.model.JobSummary
import com.kurtz.homerecord.fieldlog.jobs.CrewOption
import com.kurtz.homerecord.fieldlog.jobs.JobDraft
import com.kurtz.homerecord.fieldlog.jobs.JobEditScreen
import com.kurtz.homerecord.fieldlog.jobs.JobEditUiState
import com.kurtz.homerecord.fieldlog.jobs.JobRow
import com.kurtz.homerecord.fieldlog.jobs.JobRowGroups
import com.kurtz.homerecord.fieldlog.jobs.JobsScreen
import com.kurtz.homerecord.fieldlog.jobs.JobsUiState
import com.kurtz.homerecord.fieldlog.jobs.RoomRemoval
import com.kurtz.homerecord.fieldlog.more.MoreScreen
import com.kurtz.homerecord.fieldlog.more.MoreUiState
import com.kurtz.homerecord.fieldlog.today.TodayScreen
import com.kurtz.homerecord.fieldlog.today.TodayUiState

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "200% font", fontScale = 2f, showBackground = true)
annotation class ThemePreviews

@Composable
private fun Frame(content: @Composable () -> Unit) = HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { Surface { content() } }

private val crew = listOf(
    TeamMember("u1", "Nate Kurtz", "nate@kurtzelectric.com", CompanyRole.Owner, null),
    TeamMember("u2", "Dana Whitfield", "dana@kurtzelectric.com", CompanyRole.Office, 4200),
    TeamMember("u3", "Marcus Ortiz", "marcus.ortiz@gmail.com", CompanyRole.Lead, 5500),
    TeamMember("u4", null, "sam.rivera@gmail.com", CompanyRole.Crew, 3200),
    TeamMember("u5", "Priya Nair", "priya@kurtzelectric.com", CompanyRole.Crew, null),
)

@ThemePreviews
@Composable
fun WelcomeCreatePreview() = Frame {
    WelcomeScreen(WelcomeUiState(name = "Kurtz Electric", trade = "Electrical", phone = "(555) 014-2290"), {})
}

@ThemePreviews
@Composable
fun WelcomeJoinErrorPreview() = Frame {
    WelcomeScreen(WelcomeUiState(mode = WelcomeMode.Join, code = "K7M2QX9R", error = ApiFailure.InvalidCode), {})
}

@ThemePreviews
@Composable
fun TeamOwnerPreview() = Frame {
    TeamScreen(
        TeamUiState(
            status = TeamStatus.Ready,
            callerRole = CompanyRole.Owner,
            companyName = "Kurtz Electric",
            members = crew,
            inviteCode = "K7M2QX9R",
            inviteLink = "https://example.test/join/K7M2QX9R",
        ),
        onEvent = {},
        onShareInvite = { _, _ -> },
        onBack = {},
    )
}

@ThemePreviews
@Composable
fun TeamOfficePreview() = Frame {
    TeamScreen(
        TeamUiState(
            status = TeamStatus.Ready,
            callerRole = CompanyRole.Office,
            companyName = "Kurtz Electric",
            members = crew.map { it.copy(rateCents = null) },
            inviteCode = "K7M2QX9R",
            actionError = ApiFailure.Offline,
        ),
        onEvent = {},
        onShareInvite = { _, _ -> },
        onBack = {},
    )
}

@ThemePreviews
@Composable
fun TeamErrorPreview() = Frame {
    TeamScreen(TeamUiState(status = TeamStatus.Failed, failure = ApiFailure.Offline, companyName = "Kurtz Electric"), {}, { _, _ -> }, {})
}

@ThemePreviews
@Composable
fun CompanyFormPreview() = Frame {
    val profile = CompanyProfile(
        "c1", "Kurtz Electric", "Electrical", "(555) 014-2290", "office@kurtzelectric.com", "418 Alder St, Harlan",
        CompanySettings(OvertimeRule.Daily8Weekly40, breakRule = true, requireGps = true, punchDistanceFt = 500),
    )
    CompanyScreen(
        CompanyUiState(
            status = TeamStatus.Ready, loaded = profile, name = profile.name, trade = "Electrical", phone = "(555) 014-2290",
            email = "office@kurtzelectric.com", address = "418 Alder St, Harlan", overtimeRule = OvertimeRule.Daily8Weekly40,
            breakRule = true, requireGps = true, punchDistanceText = "500",
        ),
        {}, {},
    )
}

@ThemePreviews
@Composable
fun TodayAdminPreview() = Frame { TodayScreen(TodayUiState("Kurtz Electric", CompanyRole.Owner), onEvent = {}) }

@ThemePreviews
@Composable
fun TodayCrewPreview() = Frame { TodayScreen(TodayUiState("Kurtz Electric", CompanyRole.Crew), onEvent = {}) }

private const val TODAY_NOW = 1_761_400_000_000L

private fun todayOnSite() = listOf(
    com.kurtz.homerecord.core.model.OnSiteEntry("m1", "Maria Soto", "j1", "Kitchen Remodel", TODAY_NOW - 3 * 3600_000L, false, 40.0, -75.0),
    com.kurtz.homerecord.core.model.OnSiteEntry("m2", "Dev Patel", "j2", "Deck Rebuild", TODAY_NOW - 90 * 60_000L, true, null, null),
)

private val todayJobs = listOf(
    com.kurtz.homerecord.fieldlog.today.TodayJob("j1", "Kitchen Remodel", "412 Maple Ave"),
    com.kurtz.homerecord.fieldlog.today.TodayJob("j2", "Deck Rebuild", "88 Orchard Ln"),
)

@ThemePreviews
@Composable
fun TodayAdminOnSitePreview() = Frame {
    TodayScreen(
        TodayUiState(
            "Kurtz Electric", CompanyRole.Owner, hasJobs = true, jobs = todayJobs, onSite = todayOnSite(),
            clock = com.kurtz.homerecord.fieldlog.today.TodayClock(
                com.kurtz.homerecord.core.punch.ClockStatus.Working("j1", TODAY_NOW - 3 * 3600_000L), "Kitchen Remodel", TODAY_NOW,
            ),
        ),
        onEvent = {},
    )
}

@ThemePreviews
@Composable
fun TodayCrewOutPreview() = Frame {
    TodayScreen(
        TodayUiState(
            "Kurtz Electric", CompanyRole.Crew, hasJobs = true, jobs = todayJobs.take(1),
            clock = com.kurtz.homerecord.fieldlog.today.TodayClock(com.kurtz.homerecord.core.punch.ClockStatus.Out, null, TODAY_NOW),
        ),
        onEvent = {},
    )
}

/** Two changes the server refused: the chip says so and a tap lists them (cr:sync-poison-op). */
@ThemePreviews
@Composable
fun TodaySyncRefusedPreview() = Frame {
    TodayScreen(
        TodayUiState(
            "Kurtz Electric", CompanyRole.Crew, hasJobs = true, jobs = todayJobs.take(1),
            clock = com.kurtz.homerecord.fieldlog.today.TodayClock(com.kurtz.homerecord.core.punch.ClockStatus.Out, null, TODAY_NOW),
            refusedChanges = listOf(
                com.kurtz.homerecord.core.sync.RefusedChange("opening", "o1", TODAY_NOW - 2 * 3600_000L),
                com.kurtz.homerecord.core.sync.RefusedChange("stair", "s1", TODAY_NOW - 3 * 3600_000L),
            ),
        ),
        onEvent = {},
    )
}

@ThemePreviews
@Composable
fun TodayCrewNothingScheduledPreview() = Frame {
    TodayScreen(
        TodayUiState(
            "Kurtz Electric", CompanyRole.Crew, hasJobs = true,
            clock = com.kurtz.homerecord.fieldlog.today.TodayClock(com.kurtz.homerecord.core.punch.ClockStatus.Out, null, TODAY_NOW),
        ),
        onEvent = {},
    )
}

@ThemePreviews
@Composable
fun MoreOwnerPreview() = Frame { MoreScreen(MoreUiState("Kurtz Electric", CompanyRole.Owner, com.kurtz.homerecord.core.photo.FileStorage(24_500_000, 112_000_000)), {}) }

@ThemePreviews
@Composable
fun MoreCrewPreview() = Frame { MoreScreen(MoreUiState("Kurtz Electric", CompanyRole.Crew), {}) }

private fun job(id: String, name: String, status: JobStatus, client: String?, address: String?, next: String? = null, start: String? = null) =
    JobRow(JobSummary(id, name, status, startDate = start), client, address, next)

private val sampleJobs = JobRowGroups(
    active = listOf(
        job("j1", "Panel upgrade 200A", JobStatus.ACTIVE, "Rivera family", "418 Maple St, Dayton", "2026-10-23"),
        job("j2", "Kitchen remodel rough-in", JobStatus.ACTIVE, "Tom and Lena Park", "92 Birch Ln, Kettering", "2026-10-26"),
        job("j3", "Garage subpanel", JobStatus.ACTIVE, null, null),
    ),
    upcoming = listOf(job("j4", "Basement finish", JobStatus.UPCOMING, "Okafor", "7 Elm Ct, Oakwood", null, "2026-11-09")),
    done = listOf(job("j5", "EV charger install", JobStatus.DONE, "Priya Nair", "55 Lake Dr, Beavercreek")),
    archived = emptyList(),
)

@ThemePreviews
@Composable
fun JobsListPreview() = Frame { JobsScreen(JobsUiState(loading = false, groups = sampleJobs, today = "2026-10-22"), {}) }

/** Crew and Lead see the jobs but no New job button (AC-F3). */
@ThemePreviews
@Composable
fun JobsCrewPreview() = Frame { JobsScreen(JobsUiState(loading = false, groups = sampleJobs, today = "2026-10-22", canAddJob = false), {}) }

@ThemePreviews
@Composable
fun JobsEmptyPreview() = Frame { JobsScreen(JobsUiState(loading = false), {}) }

@ThemePreviews
@Composable
fun JobsNoMatchesPreview() = Frame {
    JobsScreen(JobsUiState(loading = false, query = "garage door", noMatches = true), {})
}

@ThemePreviews
@Composable
fun JobEditPreview() = Frame {
    val draft = JobDraft(
        name = "Panel upgrade 200A", clientName = "Rivera family", clientPhone = "(937) 555-0142", clientEmail = "ana.rivera@example.com",
        address = "418 Maple St, Dayton", startDate = "2026-10-23", endDate = "2026-10-30", budgetHoursText = "40", notes = "Gate code 4471. Dog in back yard.",
        rooms = listOf("Kitchen", "Basement", "Garage"), crew = listOf("m1"),
    )
    val team = listOf(CrewOption("m1", "Dana Ortiz"), CrewOption("m2", "Marcus Bell"), CrewOption("m3", "Priya Raman"))
    JobEditScreen(JobEditUiState(loading = false, members = team, isNew = false, draft = draft, initial = draft.copy(notes = ""), savedAddress = draft.address, lat = 39.76, lng = -84.19), {}, {})
}

@ThemePreviews
@Composable
fun JobEditRemoveRoomPreview() = Frame {
    val draft = JobDraft(name = "Kitchen remodel", clientName = "Okafor family", address = "22 Linden Ave, Kettering", rooms = listOf("Kitchen", "Pantry"))
    JobEditScreen(
        JobEditUiState(loading = false, isNew = false, draft = draft, initial = draft, removing = RoomRemoval("Kitchen", placed = true, photos = 14, items = 2, tasks = 3)),
        {}, {},
    )
}

private val sampleOverview = com.kurtz.homerecord.fieldlog.jobs.JobOverviewUiState(
    loading = false, name = "Panel upgrade 200A", clientName = "Rivera family", address = "418 Maple St, Dayton",
    lat = 39.76, lng = -84.19, status = JobStatus.ACTIVE, startDate = "2026-10-23", endDate = "2026-10-30",
    phases = listOf("Demo", "Rough-in", "Finish"), hoursThisWeek = 18.5, photoCount = 42, openTasks = 6,
    nextDay = "2026-10-23", crew = listOf("Dana Ortiz", "Marcus Bell"), today = "2026-10-22",
)

@ThemePreviews
@Composable
fun JobOverviewPreview() = Frame {
    com.kurtz.homerecord.fieldlog.jobs.JobScreen(sampleOverview, com.kurtz.homerecord.fieldlog.jobs.JobTab.OVERVIEW, {}, {}, {})
}

@ThemePreviews
@Composable
fun JobOverviewEmptyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.jobs.JobScreen(
        com.kurtz.homerecord.fieldlog.jobs.JobOverviewUiState(loading = false, name = "Garage subpanel"),
        com.kurtz.homerecord.fieldlog.jobs.JobTab.OVERVIEW, {}, {}, {},
    )
}

@ThemePreviews
@Composable
fun JobHouseEmptyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.jobs.JobScreen(
        com.kurtz.homerecord.fieldlog.jobs.JobOverviewUiState(loading = false, name = "Garage subpanel", clientName = "Rivera"),
        com.kurtz.homerecord.fieldlog.jobs.JobTab.HOUSE, {}, {}, {},
        houseContent = { com.kurtz.homerecord.fieldlog.jobs.JobHouseEmpty(onStart = {}, modifier = androidx.compose.ui.Modifier.fillMaxSize()) },
    )
}

private val clockJobs = listOf(
    com.kurtz.homerecord.fieldlog.clock.ClockJobOption("j1", "Garage subpanel", "412 Alder St, Pasadena"),
    com.kurtz.homerecord.fieldlog.clock.ClockJobOption("j2", "Ortiz kitchen remodel", "88 Maple Ave, Altadena"),
)

private val clockSheet = com.kurtz.homerecord.fieldlog.clock.WeekSheet(
    days = listOf(
        Triple(19, 8 * 60 + 15, emptySet<com.kurtz.homerecord.core.model.ShiftFlag>()),
        Triple(20, 9 * 60 + 30, setOf(com.kurtz.homerecord.core.model.ShiftFlag.FarFromJob)),
        Triple(21, 7 * 60 + 45, emptySet()),
        Triple(22, 0, emptySet()),
        Triple(23, 0, setOf(com.kurtz.homerecord.core.model.ShiftFlag.MissingClockOut, com.kurtz.homerecord.core.model.ShiftFlag.Edited)),
        Triple(24, 0, emptySet()),
        Triple(25, 0, emptySet()),
    ).map { (d, min, flags) -> com.kurtz.homerecord.fieldlog.clock.SheetDay(java.time.LocalDate.of(2026, 10, d), min * 60_000L, flags) },
    regularMillis = 24 * 3_600_000L + 30 * 60_000L,
    overtimeMillis = 1 * 3_600_000L + 0,
    doubleMillis = 0,
)

private const val CLOCK_NOW = 1_792_000_000_000L

private fun clockState(status: com.kurtz.homerecord.core.punch.ClockStatus = com.kurtz.homerecord.core.punch.ClockStatus.Out) =
    com.kurtz.homerecord.fieldlog.clock.ClockUiState(
        loading = false, status = status, jobs = clockJobs, selectedJobId = "j1", now = CLOCK_NOW, sheet = clockSheet,
    )

@ThemePreviews
@Composable
fun ClockOutPreview() = Frame { com.kurtz.homerecord.fieldlog.clock.ClockScreen(clockState(), {}) }

@ThemePreviews
@Composable
fun ClockApprovedPreview() = Frame { com.kurtz.homerecord.fieldlog.clock.ClockScreen(clockState().copy(weekApproved = true), {}) }

private val clockChanges = listOf(
    com.kurtz.homerecord.fieldlog.clock.PunchChange(
        "p1", com.kurtz.homerecord.core.model.PunchKind.IN, java.time.LocalDate.of(2026, 10, 20), java.time.LocalTime.of(7, 30), java.time.LocalTime.of(6, 45),
        "Dana Ruiz", java.time.LocalDateTime.of(2026, 10, 21, 16, 10), "Forgot to clock in at the supply house",
    ),
    com.kurtz.homerecord.fieldlog.clock.PunchChange(
        "p2", com.kurtz.homerecord.core.model.PunchKind.OUT, java.time.LocalDate.of(2026, 10, 19), java.time.LocalTime.of(16, 30), java.time.LocalTime.of(16, 30),
        "Dana Ruiz", java.time.LocalDateTime.of(2026, 10, 20, 8, 5), "Was on the Ortiz job after lunch",
        jobChanged = true, beforeJob = "Garage subpanel", afterJob = "Ortiz kitchen remodel",
    ),
)

@ThemePreviews
@Composable
fun ClockChangesPreview() = Frame {
    com.kurtz.homerecord.fieldlog.clock.ClockScreen(
        clockState().copy(changes = clockChanges), {},
        // Scrolled to the bottom, where the changes sit under the week's totals.
        listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = 6),
    )
}

@ThemePreviews
@Composable
fun ClockWorkingPreview() = Frame {
    com.kurtz.homerecord.fieldlog.clock.ClockScreen(
        clockState(com.kurtz.homerecord.core.punch.ClockStatus.Working("j1", CLOCK_NOW - (3 * 3600 + 12 * 60 + 5) * 1000L)).copy(selectedJobId = "j2"), {},
    )
}

@ThemePreviews
@Composable
fun ClockBreakPreview() = Frame {
    com.kurtz.homerecord.fieldlog.clock.ClockScreen(
        clockState(com.kurtz.homerecord.core.punch.ClockStatus.OnBreak("j1", CLOCK_NOW - 4 * 3600_000L, CLOCK_NOW - 600_000L)), {},
    )
}

@ThemePreviews
@Composable
fun ClockFarReasonPreview() = Frame {
    com.kurtz.homerecord.fieldlog.clock.ClockScreen(
        clockState().copy(farReason = com.kurtz.homerecord.fieldlog.clock.FarReasonSheet(1840, com.kurtz.homerecord.fieldlog.clock.FarReason.Other, "Dropping off a ladder")), {},
    )
}

@ThemePreviews
@Composable
fun ClockExplainPreview() = Frame {
    com.kurtz.homerecord.fieldlog.clock.ClockScreen(clockState().copy(explainLocation = true), {})
}

@ThemePreviews
@Composable
fun ClockGpsRequiredPreview() = Frame {
    com.kurtz.homerecord.fieldlog.clock.ClockScreen(clockState().copy(gpsRequired = true), {})
}

@ThemePreviews
@Composable
fun ClockNoJobsPreview() = Frame {
    com.kurtz.homerecord.fieldlog.clock.ClockScreen(clockState().copy(jobs = emptyList(), selectedJobId = null, savedWithoutLocation = true), {})
}

@ThemePreviews
@Composable
fun ClockLoadingPreview() = Frame { com.kurtz.homerecord.fieldlog.clock.ClockScreen(com.kurtz.homerecord.fieldlog.clock.ClockUiState(), {}) }

private val crewPicks = listOf(
    com.kurtz.homerecord.fieldlog.clock.CrewPick("m2", "Maria Soto"),
    com.kurtz.homerecord.fieldlog.clock.CrewPick("m3", "Dev Patel"),
    com.kurtz.homerecord.fieldlog.clock.CrewPick("m4", "Luis Ortega", onClock = true),
)

@ThemePreviews
@Composable
fun ClockCrewPreview() = Frame {
    com.kurtz.homerecord.fieldlog.clock.ClockScreen(
        clockState().copy(
            jobs = clockJobs.take(1), sheet = null, canLeadCrew = true, crew = crewPicks, crewSelected = setOf("m2", "m3"),
            crewNotice = com.kurtz.homerecord.fieldlog.clock.CrewNotice(2, listOf("Luis Ortega")),
        ), {},
    )
}

private val TS_MONDAY = java.time.LocalDate.of(2026, 10, 19)
private val TS_ZONE = java.time.ZoneId.of("America/Los_Angeles")
private fun tsAt(day: Int, h: Int, m: Int = 0) = TS_MONDAY.plusDays(day.toLong()).atTime(h, m).atZone(TS_ZONE).toInstant().toEpochMilli()
private fun tsDays(vararg worked: Pair<Int, Long>, flags: Map<Int, Set<com.kurtz.homerecord.core.model.ShiftFlag>> = emptyMap()) =
    (0..6).map { d -> com.kurtz.homerecord.fieldlog.clock.SheetDay(TS_MONDAY.plusDays(d.toLong()), worked.toMap()[d] ?: 0L, flags[d].orEmpty()) }

private val tsEdit = com.kurtz.homerecord.core.punch.PunchEdit("o1", "Nate Kurtz", tsAt(3, 15, 10), "Forgot to clock in", tsAt(1, 8, 45), tsAt(1, 7, 30))

private val tsPeople = listOf(
    com.kurtz.homerecord.fieldlog.timesheets.PersonWeek(
        "m1", "Dana Whitfield",
        com.kurtz.homerecord.fieldlog.clock.WeekSheet(tsDays(0 to 8 * 3_600_000L, 1 to 8 * 3_600_000L, 2 to 9 * 3_600_000L), 25 * 3_600_000L, 0, 0),
        emptySet(), approved = true,
        punches = listOf(
            com.kurtz.homerecord.fieldlog.timesheets.PunchRow("p1", "IN", tsAt(0, 7), "j1", "Garage subpanel", false),
            com.kurtz.homerecord.fieldlog.timesheets.PunchRow("p2", "OUT", tsAt(0, 15), "j1", "Garage subpanel", false),
        ),
    ),
    com.kurtz.homerecord.fieldlog.timesheets.PersonWeek(
        "m3", "Marcus Ortiz",
        com.kurtz.homerecord.fieldlog.clock.WeekSheet(
            tsDays(0 to 8 * 3_600_000L, 1 to 10 * 3_600_000L, 2 to 8 * 3_600_000L, flags = mapOf(1 to setOf(com.kurtz.homerecord.core.model.ShiftFlag.Edited, com.kurtz.homerecord.core.model.ShiftFlag.NoBreak))),
            26 * 3_600_000L, 0, 0,
        ),
        setOf(com.kurtz.homerecord.core.model.ShiftFlag.Edited, com.kurtz.homerecord.core.model.ShiftFlag.NoBreak), approved = false,
        punches = listOf(
            com.kurtz.homerecord.fieldlog.timesheets.PunchRow("p3", "IN", tsAt(1, 7, 30), "j2", "Ortiz kitchen remodel", false, listOf(tsEdit)),
            com.kurtz.homerecord.fieldlog.timesheets.PunchRow("p4", "OUT", tsAt(1, 17, 30), "j2", "Ortiz kitchen remodel", true),
        ),
    ),
    com.kurtz.homerecord.fieldlog.timesheets.PersonWeek(
        "m2", "Sam Rivera",
        com.kurtz.homerecord.fieldlog.clock.WeekSheet(tsDays(0 to 4 * 3_600_000L), 4 * 3_600_000L, 0, 0),
        setOf(com.kurtz.homerecord.core.model.ShiftFlag.MissingClockOut), approved = false,
        punches = listOf(com.kurtz.homerecord.fieldlog.timesheets.PunchRow("p5", "IN", tsAt(0, 8), "j1", "Garage subpanel", false)),
    ),
)

private fun tsState(open: String? = "m3") = com.kurtz.homerecord.fieldlog.timesheets.TimesheetsUiState(
    loading = false, weekStart = TS_MONDAY, people = tsPeople, expandedId = open, isCurrentWeek = true,
)

@ThemePreviews
@Composable
fun TimesheetsWeekPreview() = Frame { com.kurtz.homerecord.fieldlog.timesheets.TimesheetsScreen(tsState(), {}, {}, TS_ZONE) }

@ThemePreviews
@Composable
fun TimesheetsCollapsedPreview() = Frame { com.kurtz.homerecord.fieldlog.timesheets.TimesheetsScreen(tsState(open = null), {}, {}, TS_ZONE) }

@ThemePreviews
@Composable
fun TimesheetsEditPreview() = Frame {
    com.kurtz.homerecord.fieldlog.timesheets.TimesheetsScreen(
        tsState().copy(
            edit = com.kurtz.homerecord.fieldlog.timesheets.PunchEditSheet(
                "p3", "Marcus Ortiz", "IN", "Tue Oct 20", java.time.LocalTime.of(7, 30), "j2",
                listOf(com.kurtz.homerecord.fieldlog.timesheets.JobChoice("j1", "Garage subpanel"), com.kurtz.homerecord.fieldlog.timesheets.JobChoice("j2", "Ortiz kitchen remodel")),
                error = com.kurtz.homerecord.fieldlog.timesheets.EditError.ReasonRequired, audit = listOf(tsEdit),
                originalTime = java.time.LocalTime.of(8, 45),
            ),
        ),
        {}, {}, TS_ZONE,
    )
}

@ThemePreviews
@Composable
fun TimesheetsApproveAllPreview() = Frame { com.kurtz.homerecord.fieldlog.timesheets.TimesheetsScreen(tsState().copy(confirmApproveAll = true), {}, {}, TS_ZONE) }

@ThemePreviews
@Composable
fun TimesheetsEmptyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.timesheets.TimesheetsScreen(
        com.kurtz.homerecord.fieldlog.timesheets.TimesheetsUiState(loading = false, weekStart = TS_MONDAY.minusWeeks(1), isCurrentWeek = false), {}, {}, TS_ZONE,
    )
}

@ThemePreviews
@Composable
fun TimesheetsLoadingPreview() = Frame { com.kurtz.homerecord.fieldlog.timesheets.TimesheetsScreen(com.kurtz.homerecord.fieldlog.timesheets.TimesheetsUiState(), {}, {}, TS_ZONE) }

private fun hrs(h: Int, m: Int = 0) = (h * 60L + m) * 60_000L

private val repState = com.kurtz.homerecord.fieldlog.reports.ReportsUiState(
    loading = false, range = com.kurtz.homerecord.fieldlog.reports.ReportRange.ThisWeek,
    from = TS_MONDAY, to = TS_MONDAY.plusDays(6),
    byPerson = listOf(
        com.kurtz.homerecord.core.model.HoursLine("m1", "Dana Whitfield", hrs(25)),
        com.kurtz.homerecord.core.model.HoursLine("m3", "Marcus Ortiz", hrs(44, 30), hrs(40), hrs(4, 30)),
        com.kurtz.homerecord.core.model.HoursLine("m2", "Sam Rivera", hrs(4)),
    ),
    byJob = listOf(
        com.kurtz.homerecord.core.model.HoursLine("j2", "Ortiz kitchen remodel", hrs(44, 30)),
        com.kurtz.homerecord.core.model.HoursLine("j1", "Garage subpanel", hrs(29)),
    ),
    totalMillis = hrs(73, 30), regularMillis = hrs(69), overtimeMillis = hrs(4, 30),
)

@ThemePreviews
@Composable
fun ReportsWeekPreview() = Frame { com.kurtz.homerecord.fieldlog.reports.ReportsScreen(repState, {}, {}) }

@ThemePreviews
@Composable
fun ReportsSharedPreview() = Frame {
    com.kurtz.homerecord.fieldlog.reports.ReportsScreen(repState.copy(notice = com.kurtz.homerecord.fieldlog.reports.ExportNotice.Shared), {}, {})
}

@ThemePreviews
@Composable
fun ReportsEmptyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.reports.ReportsScreen(
        com.kurtz.homerecord.fieldlog.reports.ReportsUiState(
            loading = false, range = com.kurtz.homerecord.fieldlog.reports.ReportRange.LastWeek, from = TS_MONDAY.minusWeeks(1), to = TS_MONDAY.minusDays(1),
        ), {}, {},
    )
}

@ThemePreviews
@Composable
fun ReportsLoadingPreview() = Frame { com.kurtz.homerecord.fieldlog.reports.ReportsScreen(com.kurtz.homerecord.fieldlog.reports.ReportsUiState(), {}, {}) }

private val captureRooms = listOf(
    com.kurtz.homerecord.fieldlog.capture.CaptureRoom("r1", "Kitchen"),
    com.kurtz.homerecord.fieldlog.capture.CaptureRoom("r2", "Garage"),
    com.kurtz.homerecord.fieldlog.capture.CaptureRoom("r3", "Main bath"),
)

private val captureReady = com.kurtz.homerecord.fieldlog.capture.CaptureUiState(
    loading = false, jobName = "Ortiz kitchen rewire", rooms = captureRooms, phases = listOf("Rough-in", "Inspection", "Finish"),
    roomId = "r1", phase = "Rough-in", canFlip = true, access = com.kurtz.homerecord.fieldlog.capture.CameraAccess.Granted, savedCount = 3,
    cameraReady = true,
)

@Composable
private fun CaptureFrame(state: com.kurtz.homerecord.fieldlog.capture.CaptureUiState) = Frame {
    com.kurtz.homerecord.fieldlog.capture.CaptureScreen(state, {}) { m ->
        androidx.compose.foundation.layout.Box(m.androidx_background())
    }
}

private fun androidx.compose.ui.Modifier.androidx_background() =
    this.then(androidx.compose.ui.Modifier.fillMaxSize())

@ThemePreviews
@Composable
fun CaptureReadyPreview() = CaptureFrame(captureReady)

@Preview(name = "Landscape", device = "spec:width=891dp,height=411dp")
@Preview(name = "Landscape 200%", device = "spec:width=891dp,height=411dp", fontScale = 2f)
@Composable
fun CaptureReadyLandscapePreview() = CaptureFrame(captureReady)

@Preview(name = "Landscape dark", device = "spec:width=891dp,height=411dp", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun CaptureGhostLandscapePreview() = CaptureFrame(captureReady.copy(ghost = com.kurtz.homerecord.fieldlog.capture.GhostShot("b1", "/none/b1.jpg"), ghostOn = true, recording = true, recordedSeconds = 74))

@ThemePreviews
@Composable
fun CaptureDeniedPreview() = CaptureFrame(captureReady.copy(access = com.kurtz.homerecord.fieldlog.capture.CameraAccess.Denied))

@ThemePreviews
@Composable
fun CaptureNoCameraPreview() = CaptureFrame(captureReady.copy(lens = com.kurtz.homerecord.core.photo.LensChoice.None))

/** CameraX is still binding (slow on the CI emulator): the shutter shows a spinner and waits. */
@ThemePreviews
@Composable
fun CaptureStartingPreview() = CaptureFrame(captureReady.copy(cameraReady = false, savedCount = 0))

@ThemePreviews
@Composable
fun CaptureLoadingPreview() = CaptureFrame(com.kurtz.homerecord.fieldlog.capture.CaptureUiState())

@ThemePreviews
@Composable
fun CaptureGhostPreview() = CaptureFrame(captureReady.copy(ghost = com.kurtz.homerecord.fieldlog.capture.GhostShot("b1", "/none/b1.jpg"), ghostOn = true))

/** The Before was taken on another phone and is still downloading (cr:sync-local-columns). */
@ThemePreviews
@Composable
fun CaptureGhostDownloadingPreview() = CaptureFrame(captureReady.copy(ghost = com.kurtz.homerecord.fieldlog.capture.GhostShot("b1", ""), ghostOn = true))

@ThemePreviews
@Composable
fun CaptureRecordingPreview() = CaptureFrame(captureReady.copy(recording = true, recordedSeconds = 74))

@ThemePreviews
@Composable
fun CaptureNotePreview() = CaptureFrame(captureReady.copy(noteOpen = true, noteText = "Homeowner wants the island moved 6 in. toward the window."))

private fun tile(id: String, room: String? = null, phase: String? = null, caption: String? = null, annotated: Boolean = false, here: Boolean = true) =
    com.kurtz.homerecord.fieldlog.photos.PhotoTile(id, if (here) "/none/$id.jpg" else "", room, phase, caption, annotated)

private val photosReady = com.kurtz.homerecord.fieldlog.photos.PhotosUiState(
    loading = false, count = 5,
    days = listOf(
        com.kurtz.homerecord.fieldlog.photos.PhotoDayUi(
            TS_MONDAY.plusDays(2),
            listOf(
                com.kurtz.homerecord.fieldlog.photos.PhotoCell.Pair(
                    tile("b1", "Kitchen", "Rough-in", "Old panel feed"), tile("a1", "Kitchen", "Finish", "New subpanel in", annotated = true),
                ),
                com.kurtz.homerecord.fieldlog.photos.PhotoCell.Single(tile("p1", "Garage", "Rough-in")),
                com.kurtz.homerecord.fieldlog.photos.PhotoCell.Single(tile("p2", "Main bath", "Inspection", "GFCI tested", here = false)),
            ),
        ),
        com.kurtz.homerecord.fieldlog.photos.PhotoDayUi(
            TS_MONDAY, listOf(com.kurtz.homerecord.fieldlog.photos.PhotoCell.Single(tile("p3", "Kitchen", "Rough-in", "Studs open at the range wall"))),
        ),
    ),
)

@ThemePreviews
@Composable
fun PhotosTabPreview() = Frame { com.kurtz.homerecord.fieldlog.photos.PhotosScreen(photosReady, {}) }

@ThemePreviews
@Composable
fun PhotosEmptyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.photos.PhotosScreen(com.kurtz.homerecord.fieldlog.photos.PhotosUiState(loading = false), {})
}

@ThemePreviews
@Composable
fun PhotosLoadingPreview() = Frame { com.kurtz.homerecord.fieldlog.photos.PhotosScreen(com.kurtz.homerecord.fieldlog.photos.PhotosUiState(), {}) }

private val photoDetailReady = com.kurtz.homerecord.fieldlog.photos.PhotoDetailUiState(
    loading = false, imagePath = "/none/a1.jpg", caption = "New subpanel in, breakers labeled",
    rooms = listOf("Kitchen", "Garage", "Main bath", "Basement").mapIndexed { i, n -> com.kurtz.homerecord.fieldlog.photos.PhotoRoomOption("r$i", n) },
    roomId = "r0", phases = listOf("Rough-in", "Finish", "Inspection"), phase = "Finish", takenAt = 1_792_000_000_000, annotated = true, isBefore = true,
)

@ThemePreviews
@Composable
fun PhotoDetailPreview() = Frame { com.kurtz.homerecord.fieldlog.photos.PhotoDetailScreen(photoDetailReady, {}, zone = java.time.ZoneId.of("UTC")) }

/** Crew tag and caption the photo but cannot delete it (AC-F3). */
@ThemePreviews
@Composable
fun PhotoDetailCrewPreview() = Frame {
    com.kurtz.homerecord.fieldlog.photos.PhotoDetailScreen(photoDetailReady.copy(canDelete = false), {}, zone = java.time.ZoneId.of("UTC"))
}

@ThemePreviews
@Composable
fun PhotoDetailDeletePreview() = Frame {
    com.kurtz.homerecord.fieldlog.photos.PhotoDetailScreen(photoDetailReady.copy(confirmDelete = true), {}, zone = java.time.ZoneId.of("UTC"))
}

/** A photo taken on another phone, downloading by its R2 key (AC-F55). */
@ThemePreviews
@Composable
fun PhotoDetailDownloadingPreview() = Frame {
    com.kurtz.homerecord.fieldlog.photos.PhotoDetailScreen(
        photoDetailReady.copy(imagePath = "", image = com.kurtz.homerecord.fieldlog.photos.PhotoImage.Downloading), {}, java.time.ZoneId.of("UTC"),
    )
}

/** The same photo with no connection: what to do, and Retry. */
@ThemePreviews
@Composable
fun PhotoDetailOfflinePreview() = Frame {
    com.kurtz.homerecord.fieldlog.photos.PhotoDetailScreen(
        photoDetailReady.copy(imagePath = "", image = com.kurtz.homerecord.fieldlog.photos.PhotoImage.NoNetwork, annotated = false), {}, java.time.ZoneId.of("UTC"),
    )
}

@ThemePreviews
@Composable
fun PhotoDetailMissingPreview() = Frame {
    com.kurtz.homerecord.fieldlog.photos.PhotoDetailScreen(com.kurtz.homerecord.fieldlog.photos.PhotoDetailUiState(loading = false, missing = true), {})
}

private val annotateReady = com.kurtz.homerecord.fieldlog.photos.AnnotateUiState(
    loading = false, imagePath = "/none/a1.jpg", aspect = 4f / 3f,
    marks = listOf(
        com.kurtz.homerecord.core.photo.Annotation.Arrow(com.kurtz.homerecord.core.photo.NormPoint(.15f, .2f), com.kurtz.homerecord.core.photo.NormPoint(.4f, .45f)),
        com.kurtz.homerecord.core.photo.Annotation.Circle(com.kurtz.homerecord.core.photo.NormPoint(.65f, .4f), .12f),
        com.kurtz.homerecord.core.photo.Annotation.Measure(com.kurtz.homerecord.core.photo.NormPoint(.2f, .8f), com.kurtz.homerecord.core.photo.NormPoint(.7f, .8f), "36 in"),
        com.kurtz.homerecord.core.photo.Annotation.Text(com.kurtz.homerecord.core.photo.NormPoint(.55f, .15f), "Move panel"),
    ),
)

@ThemePreviews
@Composable
fun AnnotatePreview() = Frame { com.kurtz.homerecord.fieldlog.photos.AnnotateScreen(annotateReady, {}, {}) }

@ThemePreviews
@Composable
fun AnnotateEmptyPreview() = Frame { com.kurtz.homerecord.fieldlog.photos.AnnotateScreen(annotateReady.copy(marks = emptyList()), {}, {}) }

@ThemePreviews
@Composable
fun AnnotateTextPreview() = Frame {
    com.kurtz.homerecord.fieldlog.photos.AnnotateScreen(
        annotateReady.copy(tool = com.kurtz.homerecord.core.photo.AnnotationTool.Measure,
            pending = com.kurtz.homerecord.fieldlog.photos.PendingMark.Measure(com.kurtz.homerecord.core.photo.NormPoint(.2f, .8f), com.kurtz.homerecord.core.photo.NormPoint(.7f, .8f)), pendingText = "36 in"),
        {}, {},
    )
}

private fun fileRow(id: String, name: String, mime: String, size: Long, folder: String = "", offline: Boolean = false, upload: com.kurtz.homerecord.core.photo.UploadStatus = com.kurtz.homerecord.core.photo.UploadStatus.None) =
    com.kurtz.homerecord.fieldlog.files.FileRow(id, name, folder, mime, size, offline, upload)

private val filesReady = com.kurtz.homerecord.fieldlog.files.FilesUiState(
    loading = false, folders = listOf("Permits", "Quotes"),
    files = listOf(
        fileRow("f1", "Ortiz first floor plan.pdf", "application/pdf", 18_400_000, offline = true),
        fileRow("f2", "Panel schedule.pdf", "application/pdf", 640_000, upload = com.kurtz.homerecord.core.photo.UploadStatus.Uploading),
        fileRow("f3", "Site notes.txt", "text/plain", 2_100),
    ),
)

@ThemePreviews
@Composable
fun FilesTabPreview() = Frame { com.kurtz.homerecord.fieldlog.files.FilesScreen(filesReady, {}) }

/** Crew and Sub view and keep files offline; no Add, New folder, Rename or Delete (AC-F3). */
@ThemePreviews
@Composable
fun FilesCrewPreview() = Frame { com.kurtz.homerecord.fieldlog.files.FilesScreen(filesReady.copy(canManage = false), {}) }

/** The one create action open: Add file, Scan a document (AC-F22) and New folder (rule 13). No screenshot test: the menu items animate in, so a still shows only the X; the device crawler shows it settled. */
@ThemePreviews
@Composable
fun FilesMenuPreview() = Frame { com.kurtz.homerecord.fieldlog.files.FilesScreen(filesReady.copy(addMenuOpen = true), {}) }

@ThemePreviews
@Composable
fun FilesSearchPreview() = Frame {
    com.kurtz.homerecord.fieldlog.files.FilesScreen(
        filesReady.copy(query = "permit", results = listOf(fileRow("f4", "Electrical permit.pdf", "application/pdf", 310_000, folder = "Permits"))), {},
    )
}

@ThemePreviews
@Composable
fun FilesEmptyPreview() = Frame { com.kurtz.homerecord.fieldlog.files.FilesScreen(com.kurtz.homerecord.fieldlog.files.FilesUiState(loading = false), {}) }

@ThemePreviews
@Composable
fun FilesLoadingPreview() = Frame { com.kurtz.homerecord.fieldlog.files.FilesScreen(com.kurtz.homerecord.fieldlog.files.FilesUiState(), {}) }

@ThemePreviews
@Composable
fun FilesDeletePreview() = Frame {
    com.kurtz.homerecord.fieldlog.files.FilesScreen(
        filesReady.copy(dialog = com.kurtz.homerecord.fieldlog.files.FilesDialog.Delete("f2", "Panel schedule.pdf")), {},
    )
}

/** A two-page plan drawn with canvas so the file viewer previews show real pages. */
private object SamplePlanPages : com.kurtz.homerecord.coreui.pages.PageSource {
    override val pageCount = 3
    override suspend fun render(index: Int, widthPx: Int) = peek(index)
    override fun peek(index: Int): androidx.compose.ui.graphics.ImageBitmap {
        val bmp = android.graphics.Bitmap.createBitmap(600, 780, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val ink = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 34f; isAntiAlias = true }
        canvas.drawText(listOf("Garage subpanel plan", "Panel schedule", "Permit notes")[index], 40f, 80f, ink)
        ink.textSize = 24f
        listOf("100 A subpanel, 20 circuits", "Feeder: 3 AWG copper in 1 in conduit", "Inspection after rough-in")
            .forEachIndexed { i, line -> canvas.drawText(line, 40f, 140f + i * 40f, ink) }
        return bmp.asImageBitmap()
    }
}

@ThemePreviews
@Composable
fun FileViewerPreview() = Frame {
    com.kurtz.homerecord.fieldlog.files.FileViewerScreen(
        fileViewerReady, {}, java.time.ZoneId.of("UTC"),
    )
}

private val planVersions = listOf(
    com.kurtz.homerecord.fieldlog.files.FileVersion("v3", 3, "Marcus Ortiz", 1_792_080_000_000, shown = true),
    com.kurtz.homerecord.fieldlog.files.FileVersion("v2", 2, "Dana Reyes", 1_791_990_000_000, shown = false),
    com.kurtz.homerecord.fieldlog.files.FileVersion("v1", 1, null, 1_791_800_000_000, shown = false),
)

private val fileViewerReady = com.kurtz.homerecord.fieldlog.files.FileViewerUiState(
    loading = false, name = "Garage plan.pdf", path = "p", mime = "application/pdf", pages = SamplePlanPages,
    canMarkUp = true, versions = planVersions,
)

/** The history sheet over the viewer; the sheet itself is a popup the screenshot renderer cannot draw. */
@ThemePreviews
@Composable
fun FileViewerHistoryPreview() = Frame {
    Box(Modifier.fillMaxSize()) {
        com.kurtz.homerecord.fieldlog.files.FileViewerScreen(fileViewerReady, {}, java.time.ZoneId.of("UTC"))
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)))
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), tonalElevation = 1.dp,
        ) {
            Column(Modifier.padding(top = 16.dp)) {
                com.kurtz.homerecord.fieldlog.files.VersionHistory(planVersions, {}, java.time.ZoneId.of("UTC"))
            }
        }
    }
}

private val markupReady = com.kurtz.homerecord.fieldlog.files.MarkupUiState(
    loading = false, name = "Garage plan.pdf", pages = SamplePlanPages, tool = com.kurtz.homerecord.core.photo.PdfTool.Highlight,
    marks = listOf(
        com.kurtz.homerecord.core.photo.PdfMark.Highlight(0, com.kurtz.homerecord.core.photo.NormPoint(0.05f, 0.15f), com.kurtz.homerecord.core.photo.NormPoint(0.75f, 0.2f)),
        com.kurtz.homerecord.core.photo.PdfMark.Rect(0, com.kurtz.homerecord.core.photo.NormPoint(0.05f, 0.3f), com.kurtz.homerecord.core.photo.NormPoint(0.9f, 0.4f)),
        com.kurtz.homerecord.core.photo.PdfMark.Arrow(0, com.kurtz.homerecord.core.photo.NormPoint(0.6f, 0.7f), com.kurtz.homerecord.core.photo.NormPoint(0.45f, 0.42f)),
        com.kurtz.homerecord.core.photo.PdfMark.Text(0, com.kurtz.homerecord.core.photo.NormPoint(0.35f, 0.78f), "Move to north wall"),
        com.kurtz.homerecord.core.photo.PdfMark.Pen(0, listOf(
            com.kurtz.homerecord.core.photo.NormPoint(0.1f, 0.6f), com.kurtz.homerecord.core.photo.NormPoint(0.15f, 0.55f),
            com.kurtz.homerecord.core.photo.NormPoint(0.22f, 0.62f), com.kurtz.homerecord.core.photo.NormPoint(0.3f, 0.56f),
        )),
    ),
)

@ThemePreviews
@Composable
fun MarkupPreview() = Frame { com.kurtz.homerecord.fieldlog.files.MarkupScreen(markupReady) {} }

@ThemePreviews
@Composable
fun MarkupEmptyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.files.MarkupScreen(markupReady.copy(marks = emptyList(), page = 1, tool = com.kurtz.homerecord.core.photo.PdfTool.Pen)) {}
}

@ThemePreviews
@Composable
fun MarkupTextPreview() = Frame {
    com.kurtz.homerecord.fieldlog.files.MarkupScreen(
        markupReady.copy(
            tool = com.kurtz.homerecord.core.photo.PdfTool.Text, pendingText = com.kurtz.homerecord.core.photo.NormPoint(0.4f, 0.5f),
            pendingValue = "Check clearance",
        ),
    ) {}
}

@ThemePreviews
@Composable
fun MarkupNotAvailablePreview() = Frame {
    com.kurtz.homerecord.fieldlog.files.MarkupScreen(
        com.kurtz.homerecord.fieldlog.files.MarkupUiState(loading = false, problem = com.kurtz.homerecord.fieldlog.files.MarkupProblem.NotAvailable),
    ) {}
}

@ThemePreviews
@Composable
fun FileViewerNotOnPhonePreview() = Frame {
    com.kurtz.homerecord.fieldlog.files.FileViewerScreen(
        com.kurtz.homerecord.fieldlog.files.FileViewerUiState(
            loading = false, name = "Permit.pdf", mime = "application/pdf", problem = com.kurtz.homerecord.fieldlog.files.FileViewerProblem.NotOnPhone,
        ), {},
    )
}

@ThemePreviews
@Composable
fun FileViewerNoPreviewPreview() = Frame {
    com.kurtz.homerecord.fieldlog.files.FileViewerScreen(
        com.kurtz.homerecord.fieldlog.files.FileViewerUiState(
            loading = false, name = "Quote.xlsx", path = "p", mime = "application/vnd.ms-excel", problem = com.kurtz.homerecord.fieldlog.files.FileViewerProblem.NoPreview,
        ), {},
    )
}

private val logDay = java.time.LocalDate.of(2026, 10, 30)

/** A full Friday on the Hale kitchen: two people, notes, photos, a delay, rain (SPEC FL-Log). */
private val logReady = com.kurtz.homerecord.fieldlog.log.LogUiState(
    day = logDay, today = logDay, loading = false, canEdit = true, exists = true,
    crew = listOf(
        com.kurtz.homerecord.fieldlog.log.LogCrewRow("m1", "Marcus Ortiz", 465, onSiteNow = false),
        com.kurtz.homerecord.fieldlog.log.LogCrewRow("m2", "Sam Rivera", 390, onSiteNow = true),
    ),
    workDone = listOf(
        "Set the base cabinets along the sink wall and shimmed them level.",
        "Ran the 20 amp circuit for the dishwasher, boxes in, ready for rough-in inspection.",
    ),
    photos = listOf("p1", "p2", "p3", "p4").map { com.kurtz.homerecord.fieldlog.log.LogPhotoThumb(it, "") },
    tasksDone = listOf(com.kurtz.homerecord.fieldlog.log.LogLine("t1", "Level the sink base")),
    changeOrders = listOf(com.kurtz.homerecord.fieldlog.log.LogLine("co1", "Add under-cabinet lights", "$640.00")),
    materials = listOf(com.kurtz.homerecord.fieldlog.log.LogLine("ma1", "Home Depot, Electrical", "$212.48, Romex and boxes")),
    weather = com.kurtz.homerecord.core.model.LogWeather(highF = 58, lowF = 41, precipIn = 0.12, conditions = "rain"),
    weatherStatus = com.kurtz.homerecord.core.log.WeatherStatus.Shown,
    delays = listOf(com.kurtz.homerecord.core.model.LogDelay(com.kurtz.homerecord.core.model.DelayReason.MATERIALS, "Countertop templates pushed to Monday")),
    visitors = "Township inspector, 2:15 PM",
    safety = "",
    clientNotes = "Cabinets are in. Countertops get templated Monday.",
)

@ThemePreviews
@Composable
fun LogReadyPreview() = Frame { com.kurtz.homerecord.fieldlog.log.LogScreen(logReady, {}, zone = java.time.ZoneId.of("UTC")) }

/** Crew read the lead's log with no edit controls (AC-F3). */
@ThemePreviews
@Composable
fun LogCrewPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogScreen(
        logReady.copy(canEdit = false, day = logDay.minusDays(1), sentAt = 1_793_400_000_000), {}, zone = java.time.ZoneId.of("UTC"),
    )
}

/** Offline on a quiet morning: one person on site, no weather yet (AC-F31). */
@ThemePreviews
@Composable
fun LogOfflinePreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogScreen(
        com.kurtz.homerecord.fieldlog.log.LogUiState(
            day = logDay, today = logDay, loading = false, canEdit = true, exists = true,
            crew = listOf(com.kurtz.homerecord.fieldlog.log.LogCrewRow("m1", "Marcus Ortiz", 95, onSiteNow = true)),
            weatherStatus = com.kurtz.homerecord.core.log.WeatherStatus.Offline,
        ),
        {},
    )
}

/** Crew on a day the lead has not opened yet. */
@ThemePreviews
@Composable
fun LogEmptyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogScreen(com.kurtz.homerecord.fieldlog.log.LogUiState(day = logDay, today = logDay, loading = false), {})
}

@ThemePreviews
@Composable
fun LogLoadingPreview() = Frame { com.kurtz.homerecord.fieldlog.log.LogScreen(com.kurtz.homerecord.fieldlog.log.LogUiState(day = logDay, today = logDay), {}) }

@ThemePreviews
@Composable
fun LogFailedPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogScreen(com.kurtz.homerecord.fieldlog.log.LogUiState(day = logDay, today = logDay, loading = false, failed = true), {})
}

/** The delay sheet's content with Materials picked (AC-F32). */
@ThemePreviews
@Composable
fun LogDelaySheetPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogDelaySheetContent(
        com.kurtz.homerecord.fieldlog.log.LogEditor.Delay(com.kurtz.homerecord.core.model.DelayReason.INSPECTION, "Inspector moved us to Tuesday"), {},
    )
}

@ThemePreviews
@Composable
fun LogTextSheetPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogTextSheetContent(
        com.kurtz.homerecord.fieldlog.log.LogEditor.Text(
            com.kurtz.homerecord.fieldlog.log.LogTextSection.CLIENT_NOTES, "Cabinets are in. Countertops get templated Monday.",
        ),
        {},
    )
}
