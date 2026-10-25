package com.kurtz.homerecord.fieldlog

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
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
fun TodayAdminPreview() = Frame { TodayScreen(TodayUiState("Kurtz Electric", CompanyRole.Owner), onInviteCrew = {}) }

@ThemePreviews
@Composable
fun TodayCrewPreview() = Frame { TodayScreen(TodayUiState("Kurtz Electric", CompanyRole.Crew), onInviteCrew = {}) }

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
        onInviteCrew = {},
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
        onInviteCrew = {},
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
        onInviteCrew = {},
    )
}

@ThemePreviews
@Composable
fun MoreOwnerPreview() = Frame { MoreScreen(MoreUiState("Kurtz Electric", CompanyRole.Owner), {}) }

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
        name = "Panel upgrade 200A", clientName = "Rivera family", address = "418 Maple St, Dayton",
        startDate = "2026-10-23", endDate = "2026-10-30", budgetHoursText = "40", notes = "Gate code 4471. Dog in back yard.",
        rooms = listOf("Kitchen", "Basement", "Garage"), crew = listOf("m1"),
    )
    val team = listOf(CrewOption("m1", "Dana Ortiz"), CrewOption("m2", "Marcus Bell"), CrewOption("m3", "Priya Raman"))
    JobEditScreen(JobEditUiState(loading = false, members = team, isNew = false, draft = draft, initial = draft.copy(notes = ""), savedAddress = draft.address, lat = 39.76, lng = -84.19), {}, {})
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
        clockState().copy(farReason = com.kurtz.homerecord.fieldlog.clock.FarReasonSheet(1840, com.kurtz.homerecord.fieldlog.clock.FarReasonSheet.OTHER, "Dropping off a ladder")), {},
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
                "p3", "Marcus Ortiz", "IN", "Tue Oct 20", "7:30 AM", "j2",
                listOf(com.kurtz.homerecord.fieldlog.timesheets.JobChoice("j1", "Garage subpanel"), com.kurtz.homerecord.fieldlog.timesheets.JobChoice("j2", "Ortiz kitchen remodel")),
                error = com.kurtz.homerecord.fieldlog.timesheets.EditError.ReasonRequired, audit = listOf(tsEdit),
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
