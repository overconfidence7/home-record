package com.kurtz.homerecord.fieldlog

import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.asImageBitmap
import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
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

/**
 * A sheet's content as the app shows it: scrim above, the sheet's container, top corners and drag handle at the bottom,
 * clear of the status bar. Catalog sheet entries use it so the crawler sees what a user sees (QA rule 3).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(content: @Composable () -> Unit) = HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
    androidx.compose.foundation.layout.Box(
        androidx.compose.ui.Modifier.fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .statusBarsPadding(),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
    ) {
        Surface(
            shape = androidx.compose.material3.BottomSheetDefaults.ExpandedShape,
            color = androidx.compose.material3.BottomSheetDefaults.ContainerColor,
            modifier = androidx.compose.ui.Modifier.padding(top = 48.dp),
        ) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                androidx.compose.material3.BottomSheetDefaults.DragHandle()
                content()
            }
        }
    }
}

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
    // As the app shows it: inside the gate's Scaffold, so the crawler sees the status bar inset (judge:fl-catalog-welcome-insets).
    androidx.compose.material3.Scaffold { WelcomeScreen(WelcomeUiState(name = "Kurtz Electric", trade = "Electrical", phone = "(555) 014-2290"), {}, Modifier.padding(it)) }
}

@ThemePreviews
@Composable
fun WelcomeJoinErrorPreview() = Frame {
    androidx.compose.material3.Scaffold { WelcomeScreen(WelcomeUiState(mode = WelcomeMode.Join, code = "K7M2QX9R", error = ApiFailure.InvalidCode), {}, Modifier.padding(it)) }
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
        CompanySettings(OvertimeRule.Daily8Weekly40, breakRule = true, requireGps = true, punchDistanceFt = 500, timeZone = "America/Chicago"),
    )
    CompanyScreen(
        CompanyUiState(
            status = TeamStatus.Ready, loaded = profile, name = profile.name, trade = "Electrical", phone = "(555) 014-2290",
            email = "office@kurtzelectric.com", address = "418 Alder St, Harlan", overtimeRule = OvertimeRule.Daily8Weekly40,
            breakRule = true, requireGps = true, punchDistanceText = "500", timeZone = "America/Chicago",
        ),
        {}, {},
    )
}

@ThemePreviews
@Composable
fun CompanyTimeZonesPreview() = Frame { com.kurtz.homerecord.fieldlog.company.CompanyTimeZoneList("America/Chicago") {} }

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

private val TODAY_DAY = java.time.LocalDate.of(2025, 10, 25)

/** Crew at work with two tasks: one overdue from earlier in the week, one due today (AC-F34). */
@ThemePreviews
@Composable
fun TodayMyTasksPreview() = Frame {
    TodayScreen(
        TodayUiState(
            "Kurtz Electric", CompanyRole.Crew, hasJobs = true, jobs = todayJobs.take(1),
            clock = com.kurtz.homerecord.fieldlog.today.TodayClock(
                com.kurtz.homerecord.core.punch.ClockStatus.Working("j1", TODAY_NOW - 2 * 3600_000L), "Kitchen Remodel", TODAY_NOW,
            ),
            tasks = listOf(
                com.kurtz.homerecord.fieldlog.today.TodayTask(
                    "t1", "j2", "Replace GFCI by the back door", "Deck Rebuild", TODAY_DAY.minusDays(3), com.kurtz.homerecord.core.model.TaskDue.OVERDUE,
                ),
                com.kurtz.homerecord.fieldlog.today.TodayTask(
                    "t2", "j1", "Label the new kitchen circuits in the panel", "Kitchen Remodel", TODAY_DAY, com.kurtz.homerecord.core.model.TaskDue.TODAY,
                ),
            ),
        ),
        onEvent = {},
    )
}

/** Owner clocked in on Kitchen Remodel: the camera button shoots for it, and the newest job photos sit in a row (SPEC FL-Today). */
@ThemePreviews
@Composable
fun TodayRecentPhotosPreview() = Frame {
    TodayScreen(
        TodayUiState(
            "Kurtz Electric", CompanyRole.Owner, hasJobs = true, jobs = todayJobs, onSite = todayOnSite().take(1),
            clock = com.kurtz.homerecord.fieldlog.today.TodayClock(
                com.kurtz.homerecord.core.punch.ClockStatus.Working("j1", TODAY_NOW - 3 * 3600_000L), "Kitchen Remodel", TODAY_NOW,
            ),
            photos = listOf(
                com.kurtz.homerecord.fieldlog.today.TodayPhoto("ph1", "j1", "/none/ph1.jpg", "Kitchen Remodel"),
                com.kurtz.homerecord.fieldlog.today.TodayPhoto("ph2", "j1", "/none/ph2.jpg", "Kitchen Remodel"),
                com.kurtz.homerecord.fieldlog.today.TodayPhoto("ph3", "j2", "", "Deck Rebuild"),
                com.kurtz.homerecord.fieldlog.today.TodayPhoto("ph4", "j2", "/none/ph4.jpg", "Deck Rebuild"),
            ),
            capture = com.kurtz.homerecord.fieldlog.today.TodayCaptureChoice(target = "j1"),
        ),
        onEvent = {},
    )
}

/** Clocked out with two jobs today: the camera button asks which job the photo is for. */
@ThemePreviews
@Composable
fun TodayCapturePickerPreview() = Frame {
    val choices = todayJobs + com.kurtz.homerecord.fieldlog.today.TodayJob("j3", "Garage subpanel", null)
    Box(Modifier.fillMaxSize()) {
        TodayScreen(
            TodayUiState(
                "Kurtz Electric", CompanyRole.Crew, hasJobs = true, jobs = todayJobs,
                clock = com.kurtz.homerecord.fieldlog.today.TodayClock(com.kurtz.homerecord.core.punch.ClockStatus.Out, null, TODAY_NOW),
                capture = com.kurtz.homerecord.fieldlog.today.TodayCaptureChoice(options = choices),
            ),
            onEvent = {},
        )
        // The sheet is a popup the renderer cannot draw; draw its content over a scrim instead.
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)))
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), tonalElevation = 1.dp,
        ) {
            Column(Modifier.padding(top = 16.dp)) { com.kurtz.homerecord.fieldlog.today.TodayCaptureChooser(choices) {} }
        }
    }
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
fun MoreOwnerPreview() = Frame {
    MoreScreen(
        MoreUiState(
            "Kurtz Electric", CompanyRole.Owner,
            plan = com.kurtz.homerecord.core.billing.FieldLogPlan(com.kurtz.homerecord.core.billing.FieldLogPlanKind.Trial, 9, 10),
            userEmail = "nate@kurtzelectric.com", versionName = "0.1.0",
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun MoreCrewPreview() = Frame {
    MoreScreen(MoreUiState("Kurtz Electric", CompanyRole.Crew, userEmail = "sam.ortiz@gmail.com", versionName = "0.1.0"), {})
}

/** Delete account (T081d): the Owner's confirm, which says the company goes too. */
@ThemePreviews
@Composable
fun MoreDeleteAccountConfirmPreview() = Frame {
    MoreScreen(
        MoreUiState("Kurtz Electric", CompanyRole.Owner, userEmail = "nate@kurtzelectric.com", versionName = "0.1.0"),
        {},
        deleteAccount = com.kurtz.homerecord.coreui.account.DeleteAccountUiState(confirmOpen = true),
    )
}

@ThemePreviews
@Composable
fun MoreLeadPreview() = Frame { MoreScreen(MoreUiState("Kurtz Electric", CompanyRole.Lead), {}) }

@ThemePreviews
@Composable
fun SettingsPreview() = Frame {
    com.kurtz.homerecord.fieldlog.settings.SettingsScreen(
        com.kurtz.homerecord.fieldlog.settings.SettingsUiState(
            storage = com.kurtz.homerecord.core.photo.FileStorage(24_500_000, 112_000_000),
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun SettingsMetricOffPreview() = Frame {
    com.kurtz.homerecord.fieldlog.settings.SettingsScreen(
        com.kurtz.homerecord.fieldlog.settings.SettingsUiState(
            com.kurtz.homerecord.core.units.UnitSystem.METRIC, notificationsOn = false,
            storage = com.kurtz.homerecord.core.photo.FileStorage(2_400_000, 0),
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun SettingsLoadingPreview() = Frame { com.kurtz.homerecord.fieldlog.settings.SettingsScreen(com.kurtz.homerecord.fieldlog.settings.SettingsUiState(), {}) }

@ThemePreviews
@Composable
fun AboutPreview() = Frame { com.kurtz.homerecord.fieldlog.settings.AboutScreen(com.kurtz.homerecord.fieldlog.settings.AboutUiState("0.1.0", 1), {}) }

/** Fixed times (Oct 28, 2026 in the afternoon, UTC) keep the screenshots stable. */
private val sampleConflicts = listOf(
    com.kurtz.homerecord.core.sync.ConflictEntry(
        "task", "t1", "Set the kitchen outlets", com.kurtz.homerecord.core.sync.ConflictOutcome.NewerKept,
        listOf("due date", "title"), 1_793_200_440_000L,
    ),
    com.kurtz.homerecord.core.sync.ConflictEntry(
        "punch", "p1", null, com.kurtz.homerecord.core.sync.ConflictOutcome.OfficeKept, listOf("note", "time"), 1_793_197_000_000L,
    ),
    com.kurtz.homerecord.core.sync.ConflictEntry(
        "daily_log", "d1", "2026-10-27", com.kurtz.homerecord.core.sync.ConflictOutcome.NewerKept, listOf("safety notes"), 1_793_194_800_000L,
    ),
    com.kurtz.homerecord.core.sync.ConflictEntry(
        "photo", "ph1", null, com.kurtz.homerecord.core.sync.ConflictOutcome.Refused, emptyList(), 1_793_190_000_000L,
    ),
)

@ThemePreviews
@Composable
fun SyncLogUpToDatePreview() = Frame { com.kurtz.homerecord.fieldlog.sync.SyncLogScreen(com.kurtz.homerecord.fieldlog.sync.SyncLogUiState(loading = false), {}) }

@ThemePreviews
@Composable
fun SyncLogConflictsPreview() = Frame {
    com.kurtz.homerecord.fieldlog.sync.SyncLogScreen(
        com.kurtz.homerecord.fieldlog.sync.SyncLogUiState(com.kurtz.homerecord.core.sync.SyncPhase.Idle, 3, sampleConflicts, loading = false), {},
    )
}

@ThemePreviews
@Composable
fun SyncLogOfflinePreview() = Frame {
    com.kurtz.homerecord.fieldlog.sync.SyncLogScreen(
        com.kurtz.homerecord.fieldlog.sync.SyncLogUiState(com.kurtz.homerecord.core.sync.SyncPhase.Offline, 12, sampleConflicts.take(1), loading = false), {},
    )
}

@ThemePreviews
@Composable
fun SyncLogLoadingPreview() = Frame { com.kurtz.homerecord.fieldlog.sync.SyncLogScreen(com.kurtz.homerecord.fieldlog.sync.SyncLogUiState(), {}) }

@ThemePreviews
@Composable
fun SyncLogLoadFailedPreview() = Frame { com.kurtz.homerecord.fieldlog.sync.SyncLogScreen(com.kurtz.homerecord.fieldlog.sync.SyncLogUiState(loading = false, loadFailed = true), {}) }

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
fun JobsLoadingPreview() = Frame { JobsScreen(JobsUiState(canAddJob = false), {}) }

@ThemePreviews
@Composable
fun JobsLoadFailedPreview() = Frame { JobsScreen(JobsUiState(loading = false, loadFailed = true, canAddJob = false), {}) }

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

/** A one-person company with no punches yet: one "Invite crew" row and one "No hours" line (judge:fl-clock-solo-owner-noise). */
@ThemePreviews
@Composable
fun ClockSoloOwnerPreview() = Frame {
    com.kurtz.homerecord.fieldlog.clock.ClockScreen(clockState().copy(
            jobs = clockJobs.take(1), canLeadCrew = true, crew = emptyList(),
            sheet = com.kurtz.homerecord.fieldlog.clock.WeekSheet(tsDays(), 0, 0, 0),
        ), {})
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
    summaries = listOf(
        com.kurtz.homerecord.fieldlog.reports.SummaryJob("j2", "Ortiz kitchen remodel", "418 Linden Ave"),
        com.kurtz.homerecord.fieldlog.reports.SummaryJob("j1", "Garage subpanel", "22 Birch Ct"),
        com.kurtz.homerecord.fieldlog.reports.SummaryJob("j3", "Hale bath rough-in", null),
    ),
    photoJobs = listOf(
        com.kurtz.homerecord.fieldlog.reports.PhotoReportJob("j2", "Ortiz kitchen remodel", 14),
        com.kurtz.homerecord.fieldlog.reports.PhotoReportJob("j1", "Garage subpanel", 3),
        com.kurtz.homerecord.fieldlog.reports.PhotoReportJob("j3", "Hale bath rough-in", 0),
    ),
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

private val repDelays = listOf(
    com.kurtz.homerecord.core.model.JobDelays(
        "j2", "Ortiz kitchen remodel", 2,
        listOf(
            com.kurtz.homerecord.core.model.DelayEntry(TS_MONDAY.plusDays(1), 0, com.kurtz.homerecord.core.model.DelayReason.WEATHER, "Rain held up the siding"),
            com.kurtz.homerecord.core.model.DelayEntry(TS_MONDAY.plusDays(1), 1, com.kurtz.homerecord.core.model.DelayReason.MATERIALS, "Cabinet boxes arrive Thursday"),
            com.kurtz.homerecord.core.model.DelayEntry(TS_MONDAY.plusDays(3), 0, com.kurtz.homerecord.core.model.DelayReason.INSPECTION, "Township rough-in inspection moved to Friday"),
        ),
    ),
    com.kurtz.homerecord.core.model.JobDelays(
        "j1", "Garage subpanel", 1,
        listOf(com.kurtz.homerecord.core.model.DelayEntry(TS_MONDAY.plusDays(2), 0, com.kurtz.homerecord.core.model.DelayReason.CLIENT, "")),
    ),
)

/** Delays in the range as a job timeline (AC-F32); one person and one job keep the hours short so the delays show. */
@ThemePreviews
@Composable
fun ReportsDelaysPreview() = Frame {
    com.kurtz.homerecord.fieldlog.reports.ReportsScreen(
        repState.copy(
            byPerson = listOf(com.kurtz.homerecord.core.model.HoursLine("m1", "Dana Whitfield", hrs(25))),
            byJob = listOf(com.kurtz.homerecord.core.model.HoursLine("j2", "Ortiz kitchen remodel", hrs(25))),
            totalMillis = hrs(25), regularMillis = hrs(25), overtimeMillis = 0,
            delays = repDelays,
        ),
        {}, {},
    )
}

/** Job summaries tab (AC-F47): one PDF being made, and the last one failed. */
@ThemePreviews
@Composable
fun ReportsSummariesPreview() = Frame {
    com.kurtz.homerecord.fieldlog.reports.ReportsScreen(
        com.kurtz.homerecord.fieldlog.reports.ReportsUiState(
            loading = false, range = com.kurtz.homerecord.fieldlog.reports.ReportRange.ThisWeek, from = TS_MONDAY, to = TS_MONDAY.plusDays(6),
            byPerson = listOf(com.kurtz.homerecord.core.model.HoursLine("m1", "Dana Whitfield", hrs(8))),
            byJob = listOf(com.kurtz.homerecord.core.model.HoursLine("j2", "Ortiz kitchen remodel", hrs(8))),
            totalMillis = hrs(8), regularMillis = hrs(8),
            tab = com.kurtz.homerecord.fieldlog.reports.ReportTab.Summaries,
            summaries = repState.summaries, summaryBusy = "j2", summaryFailed = true,
            photoJobs = repState.photoJobs,
        ),
        {}, {},
    )
}

/** Photo reports tab (AC-F28): one job with photos, one with none, one PDF being made. */
@ThemePreviews
@Composable
fun ReportsPhotosPreview() = Frame {
    com.kurtz.homerecord.fieldlog.reports.ReportsScreen(
        com.kurtz.homerecord.fieldlog.reports.ReportsUiState(
            loading = false, range = com.kurtz.homerecord.fieldlog.reports.ReportRange.LastFourWeeks, from = TS_MONDAY.minusWeeks(4), to = TS_MONDAY.minusDays(1),
            byPerson = listOf(com.kurtz.homerecord.core.model.HoursLine("m1", "Dana Whitfield", hrs(31))),
            byJob = listOf(com.kurtz.homerecord.core.model.HoursLine("j2", "Ortiz kitchen remodel", hrs(31))),
            totalMillis = hrs(31), regularMillis = hrs(31),
            photoJobs = listOf(
                com.kurtz.homerecord.fieldlog.reports.PhotoReportJob("j2", "Ortiz kitchen remodel", 38),
                com.kurtz.homerecord.fieldlog.reports.PhotoReportJob("j3", "Hale basement finish", 12),
                com.kurtz.homerecord.fieldlog.reports.PhotoReportJob("j1", "Garage subpanel", 0),
            ),
            tab = com.kurtz.homerecord.fieldlog.reports.ReportTab.Photos,
            photoBusy = "j3",
            photoNotice = com.kurtz.homerecord.fieldlog.reports.PhotoReportOutcome.NoneHere,
        ),
        {}, {},
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

/** The owner shared the job's gallery with the client; the link is on, with Turn off link (AC-F28, AC-B35). */
@ThemePreviews
@Composable
fun PhotosTabLinkPreview() = Frame {
    com.kurtz.homerecord.fieldlog.photos.PhotosScreen(
        photosReady.copy(link = com.kurtz.homerecord.fieldlog.share.ShareLinkUi(allowed = true, url = "https://api.homerecord.app/share/gallery/Hk2mW9qT")),
        {},
    )
}

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
    roomId = "r0", phases = listOf("Rough-in", "Finish", "Inspection"), phase = "Finish", takenAt = 1_792_000_000_000, takenBy = "Marco Diaz", annotated = true, isBefore = true,
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
fun LogDelaySheetPreview() = SheetFrame {
    com.kurtz.homerecord.fieldlog.log.LogDelaySheetContent(
        com.kurtz.homerecord.fieldlog.log.LogEditor.Delay(com.kurtz.homerecord.core.model.DelayReason.INSPECTION, "Inspector moved us to Tuesday"), {},
    )
}

@ThemePreviews
@Composable
fun LogTextSheetPreview() = SheetFrame {
    com.kurtz.homerecord.fieldlog.log.LogTextSheetContent(
        com.kurtz.homerecord.fieldlog.log.LogEditor.Text(
            com.kurtz.homerecord.fieldlog.log.LogTextSection.CLIENT_NOTES, "Cabinets are in. Countertops get templated Monday.",
        ),
        {},
    )
}

/** The lead left a photo and the materials receipt out, and typed the weather in by hand (AC-F30). */
@ThemePreviews
@Composable
fun LogLeftOutPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogScreen(
        logReady.copy(
            photos = logReady.photos.drop(1),
            materials = emptyList(),
            weather = com.kurtz.homerecord.core.model.LogWeather(highF = 61, lowF = 44, precipIn = 0.3, conditions = "showers"),
            weatherEdited = true,
            dropped = mapOf(com.kurtz.homerecord.core.model.LogLineKind.PHOTO to 1, com.kurtz.homerecord.core.model.LogLineKind.MATERIAL to 1),
        ),
        {}, zone = java.time.ZoneId.of("UTC"),
    )
}

/** Sent Friday afternoon to the client and the office; the bar now offers Send again (AC-F33). */
@ThemePreviews
@Composable
fun LogSentPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogScreen(
        logReady.copy(sentAt = 1_793_478_600_000, sentTo = listOf("jen.hale@example.com", "office@ortizbuild.com")),
        {}, zone = java.time.ZoneId.of("UTC"),
    )
}

/** Sent, and the owner also shared a link to the day's log; the link is on (AC-B35). */
@ThemePreviews
@Composable
fun LogSentLinkPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogScreen(
        logReady.copy(
            sentAt = 1_793_478_600_000, sentTo = listOf("jen.hale@example.com"),
            link = com.kurtz.homerecord.fieldlog.share.ShareLinkUi(allowed = true, url = "https://api.homerecord.app/share/log/Zp4Rt7cN"),
        ),
        {}, zone = java.time.ZoneId.of("UTC"),
    )
}

/** Sent at 4:10, then the lead added a safety note: the line says the client's copy is out of date (AC-F33, R09d). */
@ThemePreviews
@Composable
fun LogSentChangedPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogScreen(
        logReady.copy(sentAt = 1_793_478_600_000, sentTo = listOf("jen.hale@example.com"), changedSinceSent = true),
        {}, zone = java.time.ZoneId.of("UTC"),
    )
}

/** The Send sheet for a job with a client email and an office address (AC-F33). */
@ThemePreviews
@Composable
fun LogSendSheetPreview() = SheetFrame {
    com.kurtz.homerecord.fieldlog.log.LogSendSheetContent(
        com.kurtz.homerecord.fieldlog.log.LogEditor.Send(listOf("jen.hale@example.com", "office@ortizbuild.com")), sending = false, failed = false, {},
    )
}

/** No emails on the job, and the last try found no app to take the PDF: Share PDF is the one action. */
@ThemePreviews
@Composable
fun LogSendSheetNoEmailsPreview() = SheetFrame {
    com.kurtz.homerecord.fieldlog.log.LogSendSheetContent(com.kurtz.homerecord.fieldlog.log.LogEditor.Send(emptyList()), sending = false, failed = true, {})
}

/** A new job's first morning: nothing logged yet, so Send asks before an empty page goes to the client. */
@ThemePreviews
@Composable
fun LogSendEmptyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogScreen(
        com.kurtz.homerecord.fieldlog.log.LogUiState(
            day = logDay, today = logDay, loading = false, canEdit = true, exists = true,
            weather = com.kurtz.homerecord.core.model.LogWeather(highF = 58, lowF = 41, conditions = "clouds"),
            weatherStatus = com.kurtz.homerecord.core.log.WeatherStatus.Shown,
            editor = com.kurtz.homerecord.fieldlog.log.LogEditor.SendEmpty,
        ),
        {}, zone = java.time.ZoneId.of("UTC"),
    )
}

/** A daily log page drawn with canvas so the PDF preview shows what the client gets. */
private object SampleLogPages : com.kurtz.homerecord.coreui.pages.PageSource {
    override val pageCount = 2
    override suspend fun render(index: Int, widthPx: Int) = peek(index)
    override fun peek(index: Int): androidx.compose.ui.graphics.ImageBitmap {
        val bmp = android.graphics.Bitmap.createBitmap(612, 792, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val ink = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 22f; isAntiAlias = true; isFakeBoldText = true }
        val body = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 14f; isAntiAlias = true }
        val lines = if (index == 0) listOf(
            "Ortiz Building, (412) 555-0148" to body, "Daily log, Friday, October 30, 2026" to ink, "Hale kitchen, Jen Hale, 18 Birch Ln" to body,
            "" to body, "Crew on site" to ink, "Marcus Ortiz, 7h 45m" to body, "Sam Rivera, 6h 30m" to body,
            "" to body, "Work done" to ink, "Set the base cabinets along the sink wall." to body, "Ran the 20 amp dishwasher circuit." to body,
            "" to body, "Delays" to ink, "Materials: countertop templates pushed to Monday" to body,
        ) else listOf("Photos" to ink, "" to body, "Notes for the client" to ink, "Cabinets are in. Countertops get templated Monday." to body)
        lines.forEachIndexed { i, (text, paint) -> canvas.drawText(text, 48f, 72f + i * 30f, paint) }
        canvas.drawText("Page ${index + 1} of 2", 270f, 760f, body)
        return bmp.asImageBitmap()
    }
}

@ThemePreviews
@Composable
fun LogPdfPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogPreviewScreen(com.kurtz.homerecord.fieldlog.log.LogPreviewUiState(logDay, loading = false, pages = SampleLogPages), {})
}

@ThemePreviews
@Composable
fun LogPdfFailedPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogPreviewScreen(com.kurtz.homerecord.fieldlog.log.LogPreviewUiState(logDay, loading = false, failed = true), {})
}

/** Correcting a rainy Friday's weather the lookup got wrong (rule 15). */
@ThemePreviews
@Composable
fun LogWeatherFormPreview() = Frame {
    val draft = com.kurtz.homerecord.fieldlog.log.WeatherDraft(conditions = "showers", high = "61", low = "44", precip = "0.3")
    com.kurtz.homerecord.fieldlog.log.LogWeatherScreen(
        com.kurtz.homerecord.fieldlog.log.LogWeatherUiState(
            day = logDay, loading = false, draft = draft,
            initial = draft.copy(conditions = "rain", high = "58", low = "41", precip = "0.12"), handEntered = true,
        ),
        {},
    )
}

/** Save pressed with no conditions picked and a low above the high (rule 21). */
@ThemePreviews
@Composable
fun LogWeatherFormErrorsPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogWeatherScreen(
        com.kurtz.homerecord.fieldlog.log.LogWeatherUiState(
            day = logDay, loading = false, showErrors = true,
            draft = com.kurtz.homerecord.fieldlog.log.WeatherDraft(high = "48", low = "52", precip = "a little"),
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun LogWeatherFormFailedPreview() = Frame {
    com.kurtz.homerecord.fieldlog.log.LogWeatherScreen(com.kurtz.homerecord.fieldlog.log.LogWeatherUiState(day = logDay, loading = false, failed = true), {})
}

// FL-Tasks (AC-F34): a kitchen remodel near the end, with a punch list started.
private val tasksDay = java.time.LocalDate.of(2026, 10, 22)

private fun taskRow(
    id: String, title: String, assignee: String? = null, due: java.time.LocalDate? = null, room: String? = null,
    punch: Boolean = false, done: Boolean = false, photo: Boolean = false, note: String? = null, canTick: Boolean = true,
) = com.kurtz.homerecord.fieldlog.tasks.TaskRow(
    id, title, assignee, due,
    if (done) com.kurtz.homerecord.core.model.TaskDue.NONE else com.kurtz.homerecord.core.model.TaskDue.of(due, tasksDay),
    room, photo, note, punch, done, canTick,
)

private val tasksReady = com.kurtz.homerecord.fieldlog.tasks.TasksUiState(
    loading = false,
    open = listOf(
        taskRow("t1", "Pull the old panel and cap the feeders", "Dana Ortiz", tasksDay.minusDays(2), "Basement", photo = true),
        taskRow("t2", "Set the kitchen counter outlets", "Marcus Bell", tasksDay, "Kitchen", note = "GFCI on the two by the sink."),
        taskRow("t3", "Replace cracked cover plate by the back door", "Marcus Bell", tasksDay.plusDays(3), "Mudroom", punch = true),
        taskRow("t4", "Label the new circuits on the panel door"),
    ),
    done = listOf(
        taskRow("t5", "Order the 200A meter base", "Nate Kurtz", done = true),
        taskRow("t6", "Call for rough-in inspection", "Nate Kurtz", room = "Kitchen", done = true),
    ),
    total = 6, canManage = true,
)

@Composable
private fun TasksTab(state: com.kurtz.homerecord.fieldlog.tasks.TasksUiState) =
    com.kurtz.homerecord.fieldlog.jobs.JobScreen(
        sampleOverview, com.kurtz.homerecord.fieldlog.jobs.JobTab.TASKS, {}, {}, {},
        tasksContent = { com.kurtz.homerecord.fieldlog.tasks.TasksScreen(state, {}) },
    )

/** The office's view: open tasks in the crew's order, then Done (AC-F34). */
@ThemePreviews
@Composable
fun TasksTabPreview() = Frame { TasksTab(tasksReady) }

/** Punch list filter on: only the punch items (FL-Tasks). */
@ThemePreviews
@Composable
fun TasksPunchPreview() = Frame {
    TasksTab(tasksReady.copy(filter = com.kurtz.homerecord.core.model.TaskFilter.PUNCH, open = tasksReady.open.filter { it.punch }, done = emptyList()))
}

/** Crew see only their own tasks, tick them, and get no Add task (AC-F3). */
@ThemePreviews
@Composable
fun TasksCrewPreview() = Frame {
    TasksTab(tasksReady.copy(open = tasksReady.open.filter { it.assignee == "Marcus Bell" }, done = emptyList(), total = 2, canManage = false))
}

@ThemePreviews
@Composable
fun TasksEmptyPreview() = Frame { TasksTab(com.kurtz.homerecord.fieldlog.tasks.TasksUiState(loading = false, canManage = true)) }

@ThemePreviews
@Composable
fun TasksCrewEmptyPreview() = Frame { TasksTab(com.kurtz.homerecord.fieldlog.tasks.TasksUiState(loading = false)) }

/** Punch list filter with no punch items yet: offers to show all (rule 11). */
@ThemePreviews
@Composable
fun TasksNoPunchPreview() = Frame {
    TasksTab(tasksReady.copy(filter = com.kurtz.homerecord.core.model.TaskFilter.PUNCH, open = emptyList(), done = emptyList()))
}

@ThemePreviews
@Composable
fun TasksLoadingPreview() = Frame { TasksTab(com.kurtz.homerecord.fieldlog.tasks.TasksUiState()) }

private val taskPeople = listOf(
    com.kurtz.homerecord.fieldlog.tasks.PersonOption("m2", "Dana Ortiz", onJob = true),
    com.kurtz.homerecord.fieldlog.tasks.PersonOption("m3", "Marcus Bell", onJob = true),
    com.kurtz.homerecord.fieldlog.tasks.PersonOption("m1", "Nate Kurtz"),
)
private val taskRooms = listOf(
    com.kurtz.homerecord.fieldlog.tasks.RoomOption("r1", "Kitchen"),
    com.kurtz.homerecord.fieldlog.tasks.RoomOption("r2", "Mudroom"),
    com.kurtz.homerecord.fieldlog.tasks.RoomOption("r3", "Basement"),
)
private val taskEditDraft = com.kurtz.homerecord.fieldlog.tasks.TaskFormDraft(
    title = "Replace cracked cover plate by the back door", assigneeId = "m3", due = "2026-10-25", roomId = "r2",
    note = "Almond duplex plate, the client has a spare in the garage.", punch = true,
)

/** Editing a punch item: who, when, where, a note, Punch list on (AC-F34). */
@ThemePreviews
@Composable
fun TaskFormEditPreview() = Frame {
    com.kurtz.homerecord.fieldlog.tasks.TaskFormScreen(
        com.kurtz.homerecord.fieldlog.tasks.TaskFormUiState(
            isNew = false, loading = false, draft = taskEditDraft, initial = taskEditDraft, people = taskPeople, rooms = taskRooms,
        ),
        {},
    )
}

/** A new task with its name typed and then cleared (rule 21). */
@ThemePreviews
@Composable
fun TaskFormNewPreview() = Frame {
    com.kurtz.homerecord.fieldlog.tasks.TaskFormScreen(
        com.kurtz.homerecord.fieldlog.tasks.TaskFormUiState(
            loading = false, draft = com.kurtz.homerecord.fieldlog.tasks.TaskFormDraft(title = "", note = "Bring the fish tape."),
            initial = com.kurtz.homerecord.fieldlog.tasks.TaskFormDraft(title = "x"), people = taskPeople, rooms = taskRooms,
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun TaskFormFailedPreview() = Frame {
    com.kurtz.homerecord.fieldlog.tasks.TaskFormScreen(com.kurtz.homerecord.fieldlog.tasks.TaskFormUiState(isNew = false, loading = false, failed = true), {})
}

/** A hand-drawn looking signature for the pad previews: a looping first stroke and a crossing second one. */
private val coSignatureStrokes: List<com.kurtz.homerecord.core.model.SignatureStroke> = run {
    val loops = (0..60).flatMap { i ->
        val t = i / 60f
        listOf(0.12f + 0.55f * t + 0.03f * kotlin.math.sin(t * 25f), 0.52f - 0.18f * kotlin.math.sin(t * 12.5f) * (1f - 0.4f * t))
    }
    val tail = (0..20).flatMap { i ->
        val t = i / 20f
        listOf(0.6f + 0.28f * t, 0.6f - 0.22f * t + 0.05f * kotlin.math.sin(t * 6f))
    }
    listOf(com.kurtz.homerecord.core.model.SignatureStroke(loops), com.kurtz.homerecord.core.model.SignatureStroke(tail))
}

/** Sign on phone with the client's name filled in and nothing drawn yet: Sign is off (AC-F38). */
@ThemePreviews
@Composable
fun ChangeOrderSignBlankPreview() = Frame {
    com.kurtz.homerecord.fieldlog.changes.ChangeOrderSignScreen(
        com.kurtz.homerecord.fieldlog.changes.ChangeOrderSignUiState(
            loading = false, title = "Pantry light on a motion switch", totalCents = 42_500, lineCount = 2, signerName = "Dana Hale",
        ),
        {},
    )
}

/** Signed on the pad: Clear and Sign are on (AC-F38). */
@ThemePreviews
@Composable
fun ChangeOrderSignDrawnPreview() = Frame {
    com.kurtz.homerecord.fieldlog.changes.ChangeOrderSignScreen(
        com.kurtz.homerecord.fieldlog.changes.ChangeOrderSignUiState(
            loading = false, title = "Pantry light on a motion switch", totalCents = 42_500, lineCount = 2, signerName = "Dana Hale",
            strokes = coSignatureStrokes,
        ),
        {},
    )
}

/** The signature did not save: the pad keeps the drawing and says to try again. */
@ThemePreviews
@Composable
fun ChangeOrderSignErrorPreview() = Frame {
    com.kurtz.homerecord.fieldlog.changes.ChangeOrderSignUiState(
        loading = false, title = "Pantry light on a motion switch", totalCents = 42_500, lineCount = 2, signerName = "",
        strokes = coSignatureStrokes, saveFailed = true,
    ).let { com.kurtz.homerecord.fieldlog.changes.ChangeOrderSignScreen(it, {}) }
}

/** Signed by the client through the link (AC-F40): the signature, "Signed through the link", and the link still on. */
@ThemePreviews
@Composable
fun ChangeOrderFormSignedWebPreview() = Frame {
    val draft = com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormDraft(
        title = "Second outlet on the island", clientName = "Dana Hale",
        lines = listOf(com.kurtz.homerecord.fieldlog.changes.FormLine("l1", "Outlet, box and wire", "1", "185.00")),
    )
    com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormScreen(
        com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormUiState(
            isNew = false, loading = false, locked = true, draft = draft, initial = draft, linkAllowed = true,
            linkUrl = "https://api.homerecord.app/share/co/q3V8pZx1",
            signature = com.kurtz.homerecord.fieldlog.changes.CoSignature("Dana Hale", 1_793_808_000_000L, null, onWeb = true),
        ),
        {},
    )
}

/** Turn off link asks first (rule 16, AC-B35). */
@ThemePreviews
@Composable
fun ChangeOrderFormLinkOffPreview() = Frame {
    val draft = com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormDraft(
        title = "Second outlet on the island", clientName = "Dana Hale",
        lines = listOf(com.kurtz.homerecord.fieldlog.changes.FormLine("l1", "Outlet, box and wire", "1", "185.00")),
    )
    com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormScreen(
        com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormUiState(
            isNew = false, loading = false, draft = draft, initial = draft, linkAllowed = true,
            linkUrl = "https://api.homerecord.app/share/co/q3V8pZx1", confirmLinkOff = true,
        ),
        {},
    )
}

/** Delete asks first (rule 16). */
@ThemePreviews
@Composable
fun TaskFormDeletePreview() = Frame {
    com.kurtz.homerecord.fieldlog.tasks.TaskFormScreen(
        com.kurtz.homerecord.fieldlog.tasks.TaskFormUiState(
            isNew = false, loading = false, draft = taskEditDraft, initial = taskEditDraft, people = taskPeople, rooms = taskRooms, confirmDelete = true,
        ),
        {},
    )
}

// Hale kitchen job's main floor: kitchen, mudroom by the back door, dining room.
private val pinRooms = listOf(
    com.kurtz.homerecord.housemodel.plan.PlanRoom.rectangle("r1", "Kitchen", "KITCHEN", 0, 0, 168, 144),
    com.kurtz.homerecord.housemodel.plan.PlanRoom.rectangle("r2", "Mudroom", "MUDROOM", 168, 0, 96, 144),
    com.kurtz.homerecord.housemodel.plan.PlanRoom.rectangle("r4", "Dining room", "DINING_ROOM", 0, 144, 264, 132),
)
private val pinFloors = listOf(
    com.kurtz.homerecord.fieldlog.tasks.PinFloor("f0", "Basement", listOf(com.kurtz.homerecord.housemodel.plan.PlanRoom.rectangle("r3", "Basement", "BASEMENT", 0, 0, 264, 276))),
    com.kurtz.homerecord.fieldlog.tasks.PinFloor("f1", "Main floor", pinRooms),
)
private val pinnedDraft = taskEditDraft.copy(planPin = com.kurtz.homerecord.core.model.TaskPlanPin("f1", 216, 110))

/** FL-House with two task pins on the main floor; a tap on a pin opens its task (AC-F35). */
@ThemePreviews
@Composable
fun JobHousePinsPreview() = Frame {
    com.kurtz.homerecord.fieldlog.jobs.JobScreen(
        com.kurtz.homerecord.fieldlog.jobs.JobOverviewUiState(loading = false, name = "Hale kitchen", clientName = "Hale"),
        com.kurtz.homerecord.fieldlog.jobs.JobTab.HOUSE, {}, {}, {},
        houseContent = { JobHousePinsContent() },
    )
}

@Composable
private fun JobHousePinsContent() {
    com.kurtz.homerecord.housemodel.ui.HouseScreen(
        state = com.kurtz.homerecord.housemodel.ui.HouseUiState(
            loading = false, homeName = "Hale kitchen", summary = "418 Linden Ave, Ithaca",
            floors = listOf(
                com.kurtz.homerecord.housemodel.FloorSummary("f0", "Basement", 96, false, 1),
                com.kurtz.homerecord.housemodel.FloorSummary("f1", "Main floor", 108, false, 3),
            ),
            floorId = "f1", rooms = pinRooms,
        ),
        onEvent = {}, onOpenRoom = {}, onEditPlan = {}, onEditShape = {}, onOpenFloors = {}, on3D = {}, onBehindWall = {}, onOpenMark = {}, onHandoff = {},
        pins = listOf(
            com.kurtz.homerecord.housemodel.blueprint.BlueprintPin("t1", "Task 1: Move the outlet behind the range", 60, 30, 1),
            com.kurtz.homerecord.housemodel.blueprint.BlueprintPin("t2", "Task 2: Replace cracked cover plate by the back door", 216, 110, 2),
        ),
        showHeader = false,
    )
}

/** FL-House in 3D: the job's main floor, opening on Rough-in with the crew's photo of the kitchen's north wall (AC-C20). */
@ThemePreviews
@Composable
fun JobHouse3DPreview() = Frame {
    val scene = com.kurtz.homerecord.housemodel.scene.FloorScene.build(
        pinRooms, 108,
        mapOf(
            com.kurtz.homerecord.housemodel.walls.WallKey("r1", 0) to
                mapOf(com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN to "/media/kitchen-north-rough.jpg"),
        ),
    )
    // The crew's rough-in drain and wire on the kitchen's north wall, Drain and vent and Electrical on (AC-C23).
    val markers = com.kurtz.homerecord.housemodel.scene.SceneMarkers.place(
        scene,
        listOf(
            com.kurtz.homerecord.housemodel.overlays.Overlay(
                "ov1", "h1", "r1", "w1", com.kurtz.homerecord.housemodel.overlays.OverlayKind.DRAIN, "Sink drain", null,
                com.kurtz.homerecord.housemodel.overlays.OverlayDepth.BEHIND_DRYWALL,
                listOf(com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.3f, 0.7f), com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.3f, 1f)),
                com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN, null, null, 0L,
            ),
            com.kurtz.homerecord.housemodel.overlays.Overlay(
                "ov2", "h1", "r1", "w1", com.kurtz.homerecord.housemodel.overlays.OverlayKind.ELECTRICAL_WIRE, "Counter circuit", null,
                com.kurtz.homerecord.housemodel.overlays.OverlayDepth.BEHIND_DRYWALL,
                listOf(com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.05f, 0.55f), com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.9f, 0.55f)),
                com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN, null, null, 0L,
            ),
        ),
        mapOf("w1" to com.kurtz.homerecord.housemodel.walls.WallKey("r1", 0)),
    )
    com.kurtz.homerecord.housemodel.ui.House3DScreen(
        com.kurtz.homerecord.housemodel.ui.House3DUiState(
            loading = false, homeName = "Hale kitchen",
            floors = listOf(
                com.kurtz.homerecord.housemodel.FloorSummary("f0", "Basement", 96, false, 1),
                com.kurtz.homerecord.housemodel.FloorSummary("f1", "Main floor", 108, false, 3),
            ),
            floorId = "f1",
            stage = com.kurtz.homerecord.housemodel.walls.WallStage.BehindTheWall,
            scene = scene,
            markers = markers,
            layers = com.kurtz.homerecord.housemodel.blueprint.LayerSet(
                setOf(
                    com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer.DRAIN_VENT,
                    com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer.ELECTRICAL,
                ),
            ),
        ),
        {}, {}, {},
    )
}

/** The task form with its pin on the plan: "Main floor · Mudroom" (AC-F35). */
@ThemePreviews
@Composable
fun TaskFormPinnedPreview() = Frame {
    com.kurtz.homerecord.fieldlog.tasks.TaskFormScreen(
        com.kurtz.homerecord.fieldlog.tasks.TaskFormUiState(
            isNew = false, loading = false, draft = pinnedDraft, initial = pinnedDraft, people = taskPeople, rooms = taskRooms, plan = pinFloors,
        ),
        {},
    )
}

/** Pin on plan: the main floor with the task's pin in the mudroom, floor chips, Remove pin (AC-F35). */
@ThemePreviews
@Composable
fun TaskPinPickerPreview() = Frame {
    com.kurtz.homerecord.fieldlog.tasks.TaskFormScreen(
        com.kurtz.homerecord.fieldlog.tasks.TaskFormUiState(
            isNew = false, loading = false, draft = pinnedDraft, initial = pinnedDraft, people = taskPeople, rooms = taskRooms,
            plan = pinFloors, pinning = true, pinFloorId = "f1",
        ),
        {},
    )
}

/** Pin on plan before a pin is placed: one sentence says where to tap. */
@ThemePreviews
@Composable
fun TaskPinPickerEmptyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.tasks.TaskFormScreen(
        com.kurtz.homerecord.fieldlog.tasks.TaskFormUiState(
            loading = false, draft = com.kurtz.homerecord.fieldlog.tasks.TaskFormDraft(title = "Add a GFCI by the sink"),
            people = taskPeople, rooms = taskRooms, plan = pinFloors, pinning = true, pinFloorId = "f1",
        ),
        {},
    )
}

// The garage plan with two task pins on its first page (AC-F35).
private val filePins = listOf(
    com.kurtz.homerecord.fieldlog.files.PagePin("t1", 1, "Move the subpanel 6 inches left", 0, 0.62f, 0.34f),
    com.kurtz.homerecord.fieldlog.files.PagePin("t2", 2, "Add a GFCI by the workbench", 0, 0.3f, 0.6f),
)
private val fileViewerPinned = fileViewerReady.copy(jobId = "j1", pins = filePins, canPinTasks = true)

/** A plan with task pins; Pin a task in the top bar (AC-F35). */
@ThemePreviews
@Composable
fun FileViewerPinsPreview() = Frame {
    com.kurtz.homerecord.fieldlog.files.FileViewerScreen(fileViewerPinned, {}, java.time.ZoneId.of("UTC"))
}

/** Pin mode: the hint bar under the pages says to tap where the work is, with Cancel (AC-F35). */
@ThemePreviews
@Composable
fun FileViewerPinModePreview() = Frame {
    com.kurtz.homerecord.fieldlog.files.FileViewerScreen(fileViewerPinned.copy(pinMode = true), {}, java.time.ZoneId.of("UTC"))
}

/** "Pin which task?" over the viewer: New task here, then the job's open tasks; the sheet is a popup the renderer cannot draw. */
@ThemePreviews
@Composable
fun FileViewerPinChooserPreview() = Frame {
    Box(Modifier.fillMaxSize()) {
        com.kurtz.homerecord.fieldlog.files.FileViewerScreen(fileViewerPinned.copy(pinMode = true), {}, java.time.ZoneId.of("UTC"))
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)))
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), tonalElevation = 1.dp,
        ) {
            Column(Modifier.padding(top = 16.dp)) {
                com.kurtz.homerecord.fieldlog.files.PinChooser(
                    listOf(
                        com.kurtz.homerecord.fieldlog.files.TaskChoice("t1", "Move the subpanel 6 inches left", pinnedHere = true),
                        com.kurtz.homerecord.fieldlog.files.TaskChoice("t3", "Label every breaker in the new subpanel", pinnedHere = false),
                        com.kurtz.homerecord.fieldlog.files.TaskChoice("t4", "Seal the conduit through the garage wall", pinnedHere = false),
                    ),
                    {},
                )
            }
        }
    }
}

private val filePinnedDraft = taskEditDraft.copy(
    title = "Move the subpanel 6 inches left", roomId = null, punch = false,
    filePin = com.kurtz.homerecord.fieldlog.tasks.FormFilePin("plan1", 1, 0.62f, 0.34f),
)

/** The task form with its pin on a file page: "Garage plan.pdf · Page 2" and Remove (AC-F35). */
@ThemePreviews
@Composable
fun TaskFormFilePinPreview() = Frame {
    com.kurtz.homerecord.fieldlog.tasks.TaskFormScreen(
        com.kurtz.homerecord.fieldlog.tasks.TaskFormUiState(
            isNew = false, loading = false, draft = filePinnedDraft, initial = filePinnedDraft, people = taskPeople, rooms = taskRooms,
            fileName = "Garage plan.pdf",
        ),
        {},
    )
}

/** A Crew member's own task opened from a pin, read only: who, due, room, plan pin, punch list, note (R09g, AC-F35). */
@ThemePreviews
@Composable
fun TaskViewPreview() = Frame {
    com.kurtz.homerecord.fieldlog.tasks.TaskFormScreen(
        com.kurtz.homerecord.fieldlog.tasks.TaskFormUiState(
            isNew = false, loading = false, readOnly = true, draft = pinnedDraft, initial = pinnedDraft, people = taskPeople, rooms = taskRooms,
            plan = pinFloors,
        ),
        {},
    )
}

/** A Crew member's ticked task pinned on a file page, read only (R09g). */
@ThemePreviews
@Composable
fun TaskViewDonePreview() = Frame {
    com.kurtz.homerecord.fieldlog.tasks.TaskFormScreen(
        com.kurtz.homerecord.fieldlog.tasks.TaskFormUiState(
            isNew = false, loading = false, readOnly = true, isDone = true, draft = filePinnedDraft, initial = filePinnedDraft,
            people = taskPeople, rooms = taskRooms, fileName = "Garage plan.pdf",
        ),
        {},
    )
}

/** The crew's wall at rough-in before the photo is in: the bath fan circuit and its switch on the plain wall (AC-C25). */
@ThemePreviews
@Composable
fun JobWallOverlaysPreview() = Frame {
    fun overlay(id: String, kind: com.kurtz.homerecord.housemodel.overlays.OverlayKind, label: String, vararg xy: Float) =
        com.kurtz.homerecord.housemodel.overlays.Overlay(
            id, "h1", "r2", "w5", kind, label, null, com.kurtz.homerecord.housemodel.overlays.OverlayDepth.BEHIND_DRYWALL,
            xy.toList().chunked(2).map { com.kurtz.homerecord.housemodel.overlays.OverlayPoint(it[0], it[1]) },
            com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN, null, null, 0L,
        )
    com.kurtz.homerecord.housemodel.ui.WallOverlaysScreen(
        com.kurtz.homerecord.housemodel.ui.WallOverlaysUiState(
            loading = false, roomName = "Hall bath", stage = com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN,
            wall = com.kurtz.homerecord.housemodel.walls.RoomWall("w5", com.kurtz.homerecord.housemodel.walls.WallSide.EAST, null, 96, 96),
            overlays = listOf(
                overlay("o1", com.kurtz.homerecord.housemodel.overlays.OverlayKind.ELECTRICAL_WIRE, "Bath fan circuit", 0.2f, 0f, 0.2f, 0.45f),
                overlay("o2", com.kurtz.homerecord.housemodel.overlays.OverlayKind.SWITCH, "Fan and light", 0.2f, 0.5f),
                overlay("o3", com.kurtz.homerecord.housemodel.overlays.OverlayKind.VENT, "Vent stack", 0.75f, 1f, 0.75f, 0f),
            ),
        ),
        {},
    )
}

private fun behindWall(id: String, side: com.kurtz.homerecord.housemodel.walls.WallSide, lengthIn: Int, vararg stages: com.kurtz.homerecord.housemodel.walls.WallStage) =
    com.kurtz.homerecord.housemodel.walls.RoomWall(id, side, null, lengthIn, 96, stages.associateWith { "/media/$id-${it.name.lowercase()}.jpg" })

private val behindWallRooms = listOf(
    com.kurtz.homerecord.housemodel.ui.BehindWallRoom(
        "r3", "Basement", "Basement",
        listOf(
            behindWall("b1", com.kurtz.homerecord.housemodel.walls.WallSide.NORTH, 264, com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN),
            behindWall("b2", com.kurtz.homerecord.housemodel.walls.WallSide.EAST, 276, com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN),
        ),
    ),
    com.kurtz.homerecord.housemodel.ui.BehindWallRoom(
        "r1", "Kitchen", "Main floor",
        listOf(
            behindWall("w1", com.kurtz.homerecord.housemodel.walls.WallSide.NORTH, 168, com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN, com.kurtz.homerecord.housemodel.walls.WallStage.FINISHED),
            behindWall("w2", com.kurtz.homerecord.housemodel.walls.WallSide.EAST, 144, com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN),
            behindWall("w3", com.kurtz.homerecord.housemodel.walls.WallSide.SOUTH, 168),
            behindWall("w4", com.kurtz.homerecord.housemodel.walls.WallSide.WEST, 144),
        ),
    ),
    com.kurtz.homerecord.housemodel.ui.BehindWallRoom(
        "r2", "Mudroom", "Main floor",
        listOf(
            behindWall("m1", com.kurtz.homerecord.housemodel.walls.WallSide.NORTH, 96),
            behindWall("m2", com.kurtz.homerecord.housemodel.walls.WallSide.EAST, 144),
            behindWall("m3", com.kurtz.homerecord.housemodel.walls.WallSide.SOUTH, 96),
            behindWall("m4", com.kurtz.homerecord.housemodel.walls.WallSide.WEST, 144),
        ),
    ),
    com.kurtz.homerecord.housemodel.ui.BehindWallRoom("r5", "Pantry", "Main floor"),
)

/** FL-BehindWall: the job's rooms by floor, the basement done at Rough-in, the kitchen half way (AC-F49). */
@ThemePreviews
@Composable
fun BehindWallRoomsPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.BehindWallScreen(
        com.kurtz.homerecord.housemodel.ui.BehindWallUiState(loading = false, title = "Hale kitchen", rooms = behindWallRooms),
        {},
    )
}

/** FL-BehindWall on the kitchen: the north wall has both photos, the east wall waits for its after-drywall photo (AC-F49). */
@ThemePreviews
@Composable
fun BehindWallWallsPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.BehindWallScreen(
        com.kurtz.homerecord.housemodel.ui.BehindWallUiState(loading = false, title = "Hale kitchen", rooms = behindWallRooms, roomId = "r1"),
        {},
    )
}

/** FL-BehindWall before any room is drawn: one line and Edit plan (rule 10). */
@ThemePreviews
@Composable
fun BehindWallEmptyPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.BehindWallScreen(
        com.kurtz.homerecord.housemodel.ui.BehindWallUiState(loading = false, title = "Hale kitchen"),
        {},
    )
}

/** FL-BehindWall with the photo sheet open on the kitchen's east wall, whose next photo is the Finished one. */
@ThemePreviews
@Composable
fun BehindWallSheetPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.BehindWallScreen(
        com.kurtz.homerecord.housemodel.ui.BehindWallUiState(loading = false, title = "Hale kitchen", rooms = behindWallRooms, roomId = "r1", photoSheetFor = "w2"),
        {},
    )
}

// FL-ChangeOrder (AC-F37): a kitchen remodel where the client added an island outlet and a pantry light.
private val coRows = listOf(
    com.kurtz.homerecord.fieldlog.changes.ChangeRow("co3", "Pantry light on a motion switch", com.kurtz.homerecord.core.model.ChangeOrderStatus.DRAFT, 42_500, "Dana Hale", 2),
    com.kurtz.homerecord.fieldlog.changes.ChangeRow("co2", "Second outlet on the island", com.kurtz.homerecord.core.model.ChangeOrderStatus.SIGNED, 33_500, "Dana Hale", 2),
    com.kurtz.homerecord.fieldlog.changes.ChangeRow("co1", "Move the range circuit to 50A", com.kurtz.homerecord.core.model.ChangeOrderStatus.DECLINED, 61_000, null, 3),
)

@Composable
private fun ChangesTab(state: com.kurtz.homerecord.fieldlog.changes.ChangesUiState) =
    com.kurtz.homerecord.fieldlog.jobs.JobScreen(
        sampleOverview, com.kurtz.homerecord.fieldlog.jobs.JobTab.CHANGES, {}, {}, {},
        changesContent = { com.kurtz.homerecord.fieldlog.changes.ChangesScreen(state, {}) },
    )

/** The office's Changes tab: a draft waiting on the client, a signed one and a declined one, newest first (AC-F37). */
@ThemePreviews
@Composable
fun ChangesListPreview() = Frame { ChangesTab(com.kurtz.homerecord.fieldlog.changes.ChangesUiState(loading = false, rows = coRows, canManage = true)) }

/** No change orders yet: the one action matches the FAB (rule 10). */
@ThemePreviews
@Composable
fun ChangesEmptyPreview() = Frame { ChangesTab(com.kurtz.homerecord.fieldlog.changes.ChangesUiState(loading = false, canManage = true)) }

/** Crew see the list and no New change order (AC-F3). */
@ThemePreviews
@Composable
fun ChangesCrewPreview() = Frame { ChangesTab(com.kurtz.homerecord.fieldlog.changes.ChangesUiState(loading = false, rows = coRows.take(2), canManage = false)) }

@ThemePreviews
@Composable
fun ChangesLoadFailedPreview() = Frame { ChangesTab(com.kurtz.homerecord.fieldlog.changes.ChangesUiState(loading = false, loadFailed = true)) }

private val coDraft = com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormDraft(
    title = "Pantry light on a motion switch",
    clientName = "Dana Hale",
    description = "Client asked for a ceiling light in the pantry that turns on when the door opens. Run from the kitchen lighting circuit.",
    lines = listOf(
        com.kurtz.homerecord.fieldlog.changes.FormLine("l1", "LED fixture and motion switch", "1", "185.00"),
        com.kurtz.homerecord.fieldlog.changes.FormLine("l2", "Labor", "2.5", "96"),
    ),
    photoIds = listOf("ph-pantry", "ph-switch"),
)

/** The job's photos as the picker lists them, newest first; not downloaded, so the placeholder shows. */
private val coJobPhotos = listOf("ph-switch", "ph-pantry", "ph-island", "ph-panel", "ph-hall").mapIndexed { i, id ->
    com.kurtz.homerecord.fieldlog.changes.CoPhoto(id, "", 1_000L - i)
}

/** Editing a draft: two lines, the total from MoneyMath, Speak under the description, Delete at the end (AC-F37). */
@ThemePreviews
@Composable
fun ChangeOrderFormEditPreview() = Frame {
    com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormScreen(
        com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormUiState(
            isNew = false, loading = false, draft = coDraft, initial = coDraft, canDictate = true, jobPhotos = coJobPhotos,
        ),
        {},
    )
}

/** The form with Add job photos open, as the app shows it; the catalog uses it (a sheet draws on a device, not in a preview). */
@Composable
fun ChangeOrderFormPhotoSheetOpen() = Frame {
    com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormScreen(
        com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormUiState(
            isNew = false, loading = false, draft = coDraft, initial = coDraft, jobPhotos = coJobPhotos, pickingPhotos = true,
        ),
        {},
    )
}

/** Add job photos: the job's photos three across, the two on the change order ticked (T056b). */
@ThemePreviews
@Composable
fun ChangeOrderFormPhotoSheetPreview() = Frame {
    androidx.compose.material3.Surface(
        color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = androidx.compose.ui.Modifier.padding(top = 120.dp),
    ) {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(top = 16.dp)) {
            com.kurtz.homerecord.fieldlog.changes.PhotoSheetContent(
                com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormUiState(
                    isNew = false, loading = false, draft = coDraft, initial = coDraft, jobPhotos = coJobPhotos, pickingPhotos = true,
                ),
                {},
            )
        }
    }
}

/** A new change order with the client filled in from the job, one line typed and a quantity that is not a number yet (rule 21). */
@ThemePreviews
@Composable
fun ChangeOrderFormNewPreview() = Frame {
    val draft = com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormDraft(
        title = "Second outlet on the island", clientName = "Dana Hale",
        lines = listOf(com.kurtz.homerecord.fieldlog.changes.FormLine("l1", "Outlet, box and wire", "0", "185")),
    )
    com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormScreen(
        com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormUiState(loading = false, draft = draft, initial = draft.copy(title = ""), canDictate = true),
        {},
    )
}

/** A signed change order opens locked (AC-F38). */
@ThemePreviews
@Composable
fun ChangeOrderFormSignedPreview() = Frame {
    com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormScreen(
        com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormUiState(
            isNew = false, loading = false, locked = true, draft = coDraft, initial = coDraft, jobPhotos = coJobPhotos,
            signature = com.kurtz.homerecord.fieldlog.changes.CoSignature("Dana Hale", 1_793_808_000_000L, null),
        ),
        {},
    )
}

/** Share PDF on a short signed order after the PDF could not be made: the message under the button (AC-F39). */
@ThemePreviews
@Composable
fun ChangeOrderFormShareFailedPreview() = Frame {
    val draft = com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormDraft(
        title = "Second outlet on the island", clientName = "Dana Hale",
        lines = listOf(com.kurtz.homerecord.fieldlog.changes.FormLine("l1", "Outlet, box and wire", "1", "185.00")),
    )
    com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormScreen(
        com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormUiState(
            isNew = false, loading = false, locked = true, draft = draft, initial = draft, shareFailed = true,
            signature = com.kurtz.homerecord.fieldlog.changes.CoSignature("Dana Hale", 1_793_808_000_000L, null),
        ),
        {},
    )
}

/** A short signed order, so the signature, who signed and when, and Duplicate are on screen (AC-F38). */
@ThemePreviews
@Composable
fun ChangeOrderFormSignedShortPreview() = Frame {
    val draft = com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormDraft(
        title = "Second outlet on the island", clientName = "Dana Hale",
        lines = listOf(com.kurtz.homerecord.fieldlog.changes.FormLine("l1", "Outlet, box and wire", "1", "185.00")),
    )
    com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormScreen(
        com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormUiState(
            isNew = false, loading = false, locked = true, draft = draft, initial = draft,
            signature = com.kurtz.homerecord.fieldlog.changes.CoSignature("Dana Hale", 1_793_808_000_000L, null),
        ),
        {},
    )
}

/** Delete asks first (rule 16). */
@ThemePreviews
@Composable
fun ChangeOrderFormDeletePreview() = Frame {
    com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormScreen(
        com.kurtz.homerecord.fieldlog.changes.ChangeOrderFormUiState(isNew = false, loading = false, draft = coDraft, initial = coDraft, confirmDelete = true),
        {},
    )
}

// FL-Schedule (AC-F41, AC-F43): Kurtz Electric's week of Nov 2, seen on Wednesday Nov 4.
private val schedWed = java.time.LocalDate.of(2026, 11, 4)
private val schedMonday = com.kurtz.homerecord.core.model.ScheduleWeek.weekStart(schedWed)
private val schedJobs = mapOf("j1" to "Hale kitchen", "j2" to "Smith deck", "j3" to "Garage subpanel")
private val schedEntries = listOf(
    com.kurtz.homerecord.core.model.ScheduleEntry("a1", "j1", "m1", schedMonday),
    com.kurtz.homerecord.core.model.ScheduleEntry("a2", "j1", "m1", schedWed),
    com.kurtz.homerecord.core.model.ScheduleEntry("a3", "j1", "m2", schedWed),
    com.kurtz.homerecord.core.model.ScheduleEntry("a4", "j2", "m2", schedWed),
    com.kurtz.homerecord.core.model.ScheduleEntry("a5", "j2", "m3", schedWed.plusDays(1)),
    com.kurtz.homerecord.core.model.ScheduleEntry("a6", "j3", "m3", schedWed.plusDays(2)),
    com.kurtz.homerecord.core.model.ScheduleEntry("a7", "j2", "m2", schedWed.plusDays(1)),
)
private val schedPeople = listOf("m1" to "Nate Kurtz", "m2" to "Luis Romero", "m3" to "Casey Brooks", "m4" to "Dana Ortiz")
private val schedOptions = schedJobs.map { (id, name) -> com.kurtz.homerecord.fieldlog.schedule.ScheduleJobOption(id, name) }.sortedBy { it.name }

private fun schedTeam(sheet: com.kurtz.homerecord.fieldlog.schedule.ScheduleSheet? = null) = com.kurtz.homerecord.fieldlog.schedule.ScheduleUiState(
    loading = false, today = schedWed, weekStart = schedMonday, selected = schedWed,
    rows = com.kurtz.homerecord.core.model.ScheduleWeek.build(schedMonday, schedPeople, schedEntries, schedJobs),
    jobs = schedOptions, sheet = sheet,
)

/** The owner on Wednesday: four people, Luis on two jobs (badge), Dana not scheduled; Add on every row. */
@ThemePreviews
@Composable
fun ScheduleTeamPreview() = Frame { com.kurtz.homerecord.fieldlog.schedule.ScheduleScreen(schedTeam(), {}) }

/** Next week from the owner's phone: "This week" brings today back. */
@ThemePreviews
@Composable
fun ScheduleNextWeekPreview() = Frame {
    com.kurtz.homerecord.fieldlog.schedule.ScheduleScreen(
        schedTeam().copy(weekStart = schedMonday.plusWeeks(1), selected = schedWed.plusWeeks(1),
            rows = com.kurtz.homerecord.core.model.ScheduleWeek.build(schedMonday.plusWeeks(1), schedPeople, schedEntries, schedJobs)),
        {},
    )
}

/** Add on Dana's row: which job for Wednesday. The sheet is drawn over a scrim, as the renderer cannot draw popups. */
@ThemePreviews
@Composable
fun SchedulePickJobPreview() = Frame {
    Box(Modifier.fillMaxSize()) {
        com.kurtz.homerecord.fieldlog.schedule.ScheduleScreen(schedTeam(), {})
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)))
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), tonalElevation = 1.dp,
        ) {
            Column(Modifier.padding(top = 16.dp)) {
                com.kurtz.homerecord.coreui.components.OptionsSheetContent(
                    "Which job for Dana Ortiz on Wed, Nov 4?",
                    schedOptions.map { com.kurtz.homerecord.coreui.components.SheetOption(it.id, it.name, androidx.compose.material.icons.Icons.Rounded.Work) },
                ) {}
            }
        }
    }
}

/** The owner before any job is live: what to do first (rule 10). */
@ThemePreviews
@Composable
fun ScheduleNoJobsPreview() = Frame {
    com.kurtz.homerecord.fieldlog.schedule.ScheduleScreen(
        com.kurtz.homerecord.fieldlog.schedule.ScheduleUiState(
            loading = false, today = schedWed, weekStart = schedMonday, selected = schedWed,
            rows = com.kurtz.homerecord.core.model.ScheduleWeek.build(schedMonday, schedPeople.take(1), emptyList(), schedJobs),
        ),
        {},
    )
}

/** Luis (Crew) sees his own week: today on two jobs with the badge, tomorrow on the Smith deck. */
@ThemePreviews
@Composable
fun ScheduleCrewPreview() = Frame {
    com.kurtz.homerecord.fieldlog.schedule.ScheduleScreen(
        com.kurtz.homerecord.fieldlog.schedule.ScheduleUiState(
            loading = false, today = schedWed, weekStart = schedMonday, selected = schedWed, ownOnly = true,
            rows = com.kurtz.homerecord.core.model.ScheduleWeek.build(schedMonday, listOf("m2" to "Luis Romero"), schedEntries, schedJobs),
        ),
        {},
    )
}

/** Crew with nothing this week. */
@ThemePreviews
@Composable
fun ScheduleCrewEmptyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.schedule.ScheduleScreen(
        com.kurtz.homerecord.fieldlog.schedule.ScheduleUiState(
            loading = false, today = schedWed, weekStart = schedMonday, selected = schedWed, ownOnly = true,
            rows = com.kurtz.homerecord.core.model.ScheduleWeek.build(schedMonday, listOf("m4" to "Dana Ortiz"), emptyList(), schedJobs),
        ),
        {},
    )
}

/** Crew clocked out with nothing today: Today says tomorrow is the Smith deck (AC-F41). */
@ThemePreviews
@Composable
fun TodayCrewTomorrowPreview() = Frame {
    TodayScreen(
        TodayUiState(
            "Kurtz Electric", CompanyRole.Crew, hasJobs = true,
            clock = com.kurtz.homerecord.fieldlog.today.TodayClock(com.kurtz.homerecord.core.punch.ClockStatus.Out, null, TODAY_NOW),
            next = com.kurtz.homerecord.fieldlog.today.TodayNextDay(schedWed.plusDays(1), tomorrow = true, jobNames = listOf("Smith deck")),
        ),
        onEvent = {},
    )
}

private val previewClients = listOf(
    com.kurtz.homerecord.fieldlog.clients.ClientRow(
        "c1", "Dana Hale", "(555) 014-2290", "dana.hale@gmail.com",
        listOf(
            com.kurtz.homerecord.fieldlog.clients.ClientJob("j1", "Hale kitchen remodel", JobStatus.ACTIVE),
            com.kurtz.homerecord.fieldlog.clients.ClientJob("j4", "Hale basement panel swap", JobStatus.DONE),
        ),
    ),
    com.kurtz.homerecord.fieldlog.clients.ClientRow(
        "c2", "Pat and Lee Smith", "(555) 017-8834", null,
        listOf(com.kurtz.homerecord.fieldlog.clients.ClientJob("j2", "Smith deck lighting", JobStatus.UPCOMING)),
    ),
    com.kurtz.homerecord.fieldlog.clients.ClientRow(
        "c3", "Westbrook Property Mgmt", null, "maintenance@westbrookpm.com",
        listOf(com.kurtz.homerecord.fieldlog.clients.ClientJob("j3", "Unit 4B service upgrade", JobStatus.ACTIVE)),
    ),
)

@ThemePreviews
@Composable
fun ClientsListPreview() = Frame {
    com.kurtz.homerecord.fieldlog.clients.ClientsScreen(
        com.kurtz.homerecord.fieldlog.clients.ClientsUiState(loading = false, clients = previewClients), {}, {},
    )
}

@ThemePreviews
@Composable
fun ClientsEmptyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.clients.ClientsScreen(com.kurtz.homerecord.fieldlog.clients.ClientsUiState(loading = false), {}, {})
}

@ThemePreviews
@Composable
fun ClientsLoadFailedPreview() = Frame { com.kurtz.homerecord.fieldlog.clients.ClientsScreen(com.kurtz.homerecord.fieldlog.clients.ClientsUiState(loading = false, loadFailed = true), {}, {}) }

private fun previewMaterial(id: String, job: String, vendor: String?, cents: Long, cat: com.kurtz.homerecord.core.material.MaterialCategory, date: String, note: String? = null) =
    com.kurtz.homerecord.core.material.Material(id, job, vendor, cents, cat, date, null, note)

private val previewMaterialGroups = com.kurtz.homerecord.core.material.MaterialsReport.group(
    listOf(
        previewMaterial("m1", "j1", "City Electric Supply", 12_371, com.kurtz.homerecord.core.material.MaterialCategory.MATERIALS, "2026-11-03", "12/2 NM-B and Wago 221s"),
        previewMaterial("m2", "j1", "Sunbelt Rentals", 9_500, com.kurtz.homerecord.core.material.MaterialCategory.EQUIPMENT_RENTAL, "2026-11-04", "Rotary hammer, one day"),
        previewMaterial("m3", "j1", null, 1_500, com.kurtz.homerecord.core.material.MaterialCategory.PERMITS, "2026-10-28"),
        previewMaterial("m4", "j2", "Home Depot", 41_288, com.kurtz.homerecord.core.material.MaterialCategory.MATERIALS, "2026-11-02", "Deck screws and joist hangers"),
        previewMaterial("m5", "j2", "Speedway", 6_214, com.kurtz.homerecord.core.material.MaterialCategory.FUEL, "2026-11-02"),
    ),
    mapOf("j1" to "Hale kitchen", "j2" to "Smith deck"),
)

private val previewMaterialJobs = listOf(
    com.kurtz.homerecord.fieldlog.materials.JobOption("j1", "Hale kitchen"),
    com.kurtz.homerecord.fieldlog.materials.JobOption("j2", "Smith deck"),
)

/** Receipts by job with each job's total and the total for all jobs (AC-F44). */
@ThemePreviews
@Composable
fun MaterialsListPreview() = Frame {
    com.kurtz.homerecord.fieldlog.materials.MaterialsScreen(com.kurtz.homerecord.fieldlog.materials.MaterialsUiState(loading = false, groups = previewMaterialGroups), {}, {})
}

@ThemePreviews
@Composable
fun MaterialsEmptyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.materials.MaterialsScreen(com.kurtz.homerecord.fieldlog.materials.MaterialsUiState(loading = false), {}, {})
}

/** A new receipt: Scan receipt first, then the fields; Save tapped with no amount shows the error (AC-F44). */
@ThemePreviews
@Composable
fun MaterialFormNewPreview() = Frame {
    com.kurtz.homerecord.fieldlog.materials.MaterialFormScreen(
        com.kurtz.homerecord.fieldlog.materials.MaterialFormUiState(
            loading = false, canScan = true, jobs = previewMaterialJobs, showErrors = true,
            draft = com.kurtz.homerecord.fieldlog.materials.MaterialFormDraft(jobId = "j1", vendor = "City Electric Supply", date = "2026-11-04"),
        ),
        {}, {},
    )
}

/** A scanned receipt: the photo, Scan again, Remove, and "Use $123.71 from the receipt" (AC-F44). */
@ThemePreviews
@Composable
fun MaterialFormScannedPreview() = Frame {
    com.kurtz.homerecord.fieldlog.materials.MaterialFormScreen(
        com.kurtz.homerecord.fieldlog.materials.MaterialFormUiState(
            loading = false, canScan = true, jobs = previewMaterialJobs, suggestedCents = 12_371,
            draft = com.kurtz.homerecord.fieldlog.materials.MaterialFormDraft(jobId = "j1", date = "2026-11-04", receiptPath = "/preview/receipt.jpg"),
        ),
        {}, {},
    )
}

/** Editing a saved receipt whose scan had no readable total: the note, the typed amount, Delete receipt. */
@ThemePreviews
@Composable
fun MaterialFormEditPreview() = Frame {
    val draft = com.kurtz.homerecord.fieldlog.materials.MaterialFormDraft(
        jobId = "j2", vendor = "Home Depot", amount = "412.88", category = com.kurtz.homerecord.core.material.MaterialCategory.MATERIALS,
        date = "2026-11-02", note = "Deck screws and joist hangers", receiptPath = "/preview/receipt.jpg",
    )
    com.kurtz.homerecord.fieldlog.materials.MaterialFormScreen(
        com.kurtz.homerecord.fieldlog.materials.MaterialFormUiState(loading = false, isNew = false, canScan = true, jobs = previewMaterialJobs, noTotalFound = true, draft = draft, initial = draft),
        {}, {},
    )
}

/** The job picker sheet's list (AC-F44). */
@ThemePreviews
@Composable
fun MaterialFormJobSheetPreview() = Frame {
    com.kurtz.homerecord.coreui.components.OptionsSheetContent(
        "Pick a job",
        previewMaterialJobs.map { com.kurtz.homerecord.coreui.components.SheetOption(it.id, it.name, androidx.compose.material.icons.Icons.Rounded.Work) },
    ) {}
}

// FL-Item (T062, AC-F50): the water heater installed on the Hale kitchen job.
private val waterHeaterEntity = com.kurtz.homerecord.core.data.ItemEntity(
    id = "it1", homeId = "h1", roomId = "r-utility", category = "WATER", subtype = "water_heater", name = "Water heater",
    make = "Rheem", model = "XE50M06ST45U1", serial = "Q051826153", inServiceOn = "2026-10-21",
    notes = "50 gal electric. Expansion tank added on the cold side; drain pan piped to the floor drain.", createdAt = 1, updatedAt = 1,
)

private val waterHeaterWarranty = com.kurtz.homerecord.core.data.WarrantyEntity(
    id = "w-it1", itemId = "it1", partsMonths = 72, laborMonths = 12, endsOn = "2032-10-21", registered = false,
    sourceUrl = "https://example.com/rheem-warranty", lookedUpAt = 1_761_000_000_000, confidence = "HIGH",
    summary = "6-year tank and parts, 1-year in-home labor.", createdAt = 1, updatedAt = 1,
)

private fun waterHeaterDetail(warranty: Boolean = true, item: com.kurtz.homerecord.core.data.ItemEntity = waterHeaterEntity) =
    com.kurtz.homerecord.housemodel.items.ItemDetail(
        item = item, category = com.kurtz.homerecord.core.model.ItemCategory.WATER, subtypeLabel = "Water heater", roomName = "Utility room",
        warranty = waterHeaterWarranty.takeIf { warranty },
        status = if (warranty) com.kurtz.homerecord.core.model.WarrantyStatus.Active("2032-10-21") else com.kurtz.homerecord.core.model.WarrantyStatus.Unknown,
        photos = emptyList(),
    )

/** A Lead's view of the water heater with its looked-up warranty (AC-F50). */
@ThemePreviews
@Composable
fun JobItemPreview() = Frame {
    com.kurtz.homerecord.fieldlog.house.JobItemScreen(com.kurtz.homerecord.fieldlog.house.JobItemUiState(loading = false, detail = waterHeaterDetail(), canEdit = true), {})
}

/** Just entered: no warranty yet, "Look up warranty" offered. */
@ThemePreviews
@Composable
fun JobItemNoWarrantyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.house.JobItemScreen(com.kurtz.homerecord.fieldlog.house.JobItemUiState(loading = false, detail = waterHeaterDetail(false), canEdit = true), {})
}

/** A fixture with no make or model: the lookup asks for them. */
@ThemePreviews
@Composable
fun JobItemNeedsModelPreview() = Frame {
    val vanity = waterHeaterEntity.copy(id = "it2", category = "FIXTURES", subtype = "faucet", name = "Vanity faucet", make = null, model = null, serial = null, inServiceOn = null, notes = null)
    com.kurtz.homerecord.fieldlog.house.JobItemScreen(
        com.kurtz.homerecord.fieldlog.house.JobItemUiState(
            loading = false, detail = waterHeaterDetail(false, vanity).copy(subtypeLabel = "Faucet", roomName = "Hall bath"), canEdit = true,
            lookupNote = com.kurtz.homerecord.fieldlog.house.JobItemLookupNote.NeedsMakeModel,
        ),
        {},
    )
}

/** Crew read the item; no Edit, Delete or lookup. */
@ThemePreviews
@Composable
fun JobItemCrewPreview() = Frame {
    com.kurtz.homerecord.fieldlog.house.JobItemScreen(com.kurtz.homerecord.fieldlog.house.JobItemUiState(loading = false, detail = waterHeaterDetail(), canEdit = false), {})
}

@ThemePreviews
@Composable
fun JobItemDeletePreview() = Frame {
    com.kurtz.homerecord.fieldlog.house.JobItemScreen(
        com.kurtz.homerecord.fieldlog.house.JobItemUiState(loading = false, detail = waterHeaterDetail(), canEdit = true, confirmDelete = true), {},
    )
}

@ThemePreviews
@Composable
fun JobItemMissingPreview() = Frame {
    com.kurtz.homerecord.fieldlog.house.JobItemScreen(com.kurtz.homerecord.fieldlog.house.JobItemUiState(loading = false), {})
}

/** The terms the lookup found, to save or discard (AC-C34). */
@ThemePreviews
@Composable
fun JobItemReviewPreview() = Frame {
    com.kurtz.homerecord.fieldlog.house.JobItemReviewContent(
        com.kurtz.homerecord.core.warranty.WarrantyTerms(
            partsMonths = 72, laborMonths = 12, specialTerms = listOf("Tank: 6 years to the original owner"),
            sourceUrl = "https://example.com/rheem-warranty", confidence = "HIGH", summary = "6-year tank and parts, 1-year in-home labor.",
        ),
        {},
    )
}

/** The item page with the review sheet open, as the app shows it; the catalog uses it (a sheet draws on a device, not in a preview). */
@Composable
fun JobItemReviewSheetOpen() = Frame {
    com.kurtz.homerecord.fieldlog.house.JobItemScreen(
        com.kurtz.homerecord.fieldlog.house.JobItemUiState(
            loading = false, detail = waterHeaterDetail(false), canEdit = true,
            review = com.kurtz.homerecord.core.warranty.WarrantyTerms(
                partsMonths = 72, laborMonths = 12, specialTerms = listOf("Tank: 6 years to the original owner"),
                sourceUrl = "https://example.com/rheem-warranty", confidence = "HIGH", summary = "6-year tank and parts, 1-year in-home labor.",
            ),
        ),
        {},
    )
}

/** FL-Room on a job with the items installed there and "Add an item here" for a Lead (AC-F50). */
@ThemePreviews
@Composable
fun JobRoomItemsPreview() = Frame {
    androidx.compose.runtime.CompositionLocalProvider(com.kurtz.homerecord.coreui.shell.LocalTagPrefix provides "fl") {
        com.kurtz.homerecord.housemodel.ui.RoomScreen(
            com.kurtz.homerecord.housemodel.ui.RoomUiState(
                loading = false, floorName = "Main floor", homeName = "Hale kitchen", canAddItem = true,
                room = com.kurtz.homerecord.housemodel.plan.PlanRoom.rectangle("r-utility", "Utility room", "UTILITY", 0, 0, 96, 120),
                items = listOf(
                    com.kurtz.homerecord.housemodel.ui.RoomItem("it1", "Water heater", "Rheem XE50M06ST45U1", "WaterDrop", com.kurtz.homerecord.core.model.WarrantyStatus.Active("2032-10-21")),
                    com.kurtz.homerecord.housemodel.ui.RoomItem("it3", "Sump pump", "Zoeller M53", "Water"),
                ),
                wallStage = com.kurtz.homerecord.housemodel.walls.WallStage.BehindTheWall,
            ),
        ) {}
    }
}

private val haleHandoff = com.kurtz.homerecord.core.bundle.HandoffSummary(
    rooms = 4, wallsWithPhotos = 3, wallPhotos = 7, overlays = 5, items = 2, documents = 1, changeOrders = 2,
)

/** FL-Handoff for the Hale kitchen: what the homeowner gets and Export .homerecord (AC-F51). */
@ThemePreviews
@Composable
fun HandoffPreview() = Frame {
    com.kurtz.homerecord.fieldlog.handoff.HandoffScreen(
        com.kurtz.homerecord.fieldlog.handoff.HandoffUiState(loading = false, jobName = "Hale kitchen", summary = haleHandoff, email = "dana.hale@gmail.com"), {},
    )
}

/** Send to homeowner going out: "Sending…" under the button (AC-B40). */
@ThemePreviews
@Composable
fun HandoffSendingPreview() = Frame {
    com.kurtz.homerecord.fieldlog.handoff.HandoffScreen(
        com.kurtz.homerecord.fieldlog.handoff.HandoffUiState(
            loading = false, jobName = "Hale kitchen", summary = haleHandoff, email = "dana.hale@gmail.com", sending = true,
        ),
        {},
    )
}

/** Sent: where the link went and until when, with Send again (AC-B40). */
@ThemePreviews
@Composable
fun HandoffSentPreview() = Frame {
    com.kurtz.homerecord.fieldlog.handoff.HandoffScreen(
        com.kurtz.homerecord.fieldlog.handoff.HandoffUiState(
            loading = false, jobName = "Hale kitchen", summary = haleHandoff, email = "dana.hale@gmail.com",
            sentTo = "dana.hale@gmail.com", sentUntil = java.time.LocalDate.of(2026, 12, 5),
        ),
        {},
    )
}

/** No signal on the jobsite: the reason under Send with Retry; Export still works. */
@ThemePreviews
@Composable
fun HandoffSendOfflinePreview() = Frame {
    com.kurtz.homerecord.fieldlog.handoff.HandoffScreen(
        com.kurtz.homerecord.fieldlog.handoff.HandoffUiState(
            loading = false, jobName = "Hale kitchen", summary = haleHandoff, email = "dana.hale@gmail.com",
            sendFailure = com.kurtz.homerecord.core.bundle.HandoffSendFailure.Offline,
        ),
        {},
    )
}

/** A mistyped address is caught before anything is sent. */
@ThemePreviews
@Composable
fun HandoffEmailInvalidPreview() = Frame {
    com.kurtz.homerecord.fieldlog.handoff.HandoffScreen(
        com.kurtz.homerecord.fieldlog.handoff.HandoffUiState(
            loading = false, jobName = "Hale kitchen", summary = haleHandoff, email = "dana.hale@gmail", emailInvalid = true,
        ),
        {},
    )
}

/** A job whose house has nothing on it yet: one row says so and Export waits. */
@ThemePreviews
@Composable
fun HandoffEmptyPreview() = Frame {
    com.kurtz.homerecord.fieldlog.handoff.HandoffScreen(
        com.kurtz.homerecord.fieldlog.handoff.HandoffUiState(
            loading = false, jobName = "Garage subpanel",
            summary = com.kurtz.homerecord.core.bundle.HandoffSummary(0, 0, 0, 0, 0, 0, 0),
        ),
        {},
    )
}

/** Writing the file: "Making the file…" under a disabled Export. */
@ThemePreviews
@Composable
fun HandoffMakingPreview() = Frame {
    com.kurtz.homerecord.fieldlog.handoff.HandoffScreen(
        com.kurtz.homerecord.fieldlog.handoff.HandoffUiState(loading = false, jobName = "Hale kitchen", summary = haleHandoff, exporting = true), {},
    )
}

/** The file could not be written (disk full): the reason under Export, which stays on for another try. */
@ThemePreviews
@Composable
fun HandoffFailedPreview() = Frame {
    com.kurtz.homerecord.fieldlog.handoff.HandoffScreen(
        com.kurtz.homerecord.fieldlog.handoff.HandoffUiState(loading = false, jobName = "Hale kitchen", summary = haleHandoff, failed = true), {},
    )
}

private val storeOffers = listOf(
    com.kurtz.homerecord.core.billing.CompanyPlanOffer("fieldlog_solo_monthly", com.kurtz.homerecord.core.billing.FieldLogPlanKind.Solo, "$39.00"),
    com.kurtz.homerecord.core.billing.CompanyPlanOffer("fieldlog_crew_monthly", com.kurtz.homerecord.core.billing.FieldLogPlanKind.Crew, "$79.00"),
)
private fun fieldLogPlan(kind: com.kurtz.homerecord.core.billing.FieldLogPlanKind, days: Int? = null, limit: Int = 10) =
    com.kurtz.homerecord.core.billing.FieldLogPlan(kind, days, limit)

/** FL-Paywall from More on day 5 of the trial: 9 days left, Solo and Crew at store prices (AC-F56). */
@ThemePreviews
@Composable
fun PlanTrialPreview() = Frame {
    com.kurtz.homerecord.fieldlog.billing.PlanScreen(
        com.kurtz.homerecord.fieldlog.billing.PlanUiState(fieldLogPlan(com.kurtz.homerecord.core.billing.FieldLogPlanKind.Trial, 9), loading = false, offers = storeOffers), {},
    )
}

/** The trial ended: the Owner's whole app is this screen, with Sign out at the bottom (AC-F56). */
@ThemePreviews
@Composable
fun PlanLapsedPreview() = Frame {
    com.kurtz.homerecord.fieldlog.billing.PlanScreen(
        com.kurtz.homerecord.fieldlog.billing.PlanUiState(
            fieldLogPlan(com.kurtz.homerecord.core.billing.FieldLogPlanKind.Expired, limit = 0), lapsed = true, loading = false, offers = storeOffers,
        ),
        {},
    )
}

/** On Crew: the Crew card says so; Solo can still be picked to move down. */
@ThemePreviews
@Composable
fun PlanCrewPreview() = Frame {
    com.kurtz.homerecord.fieldlog.billing.PlanScreen(
        com.kurtz.homerecord.fieldlog.billing.PlanUiState(
            fieldLogPlan(com.kurtz.homerecord.core.billing.FieldLogPlanKind.Crew), loading = false, offers = storeOffers,
            bought = com.kurtz.homerecord.core.billing.FieldLogPlanKind.Crew,
        ),
        {},
    )
}

/** No prices (offline, or no billing key in this build): the reason and Retry. */
@ThemePreviews
@Composable
fun PlanFailedPreview() = Frame {
    com.kurtz.homerecord.fieldlog.billing.PlanScreen(
        com.kurtz.homerecord.fieldlog.billing.PlanUiState(
            fieldLogPlan(com.kurtz.homerecord.core.billing.FieldLogPlanKind.Expired, limit = 0), lapsed = true, loading = false, loadFailed = true,
        ),
        {},
    )
}

/** Prices on their way from the store. */
@ThemePreviews
@Composable
fun PlanLoadingPreview() = Frame {
    com.kurtz.homerecord.fieldlog.billing.PlanScreen(
        com.kurtz.homerecord.fieldlog.billing.PlanUiState(fieldLogPlan(com.kurtz.homerecord.core.billing.FieldLogPlanKind.Trial, 1)), {},
    )
}

/** A crew phone after the plan ended: "Ask your owner to renew" over my own week, read only (AC-F56). */
@ThemePreviews
@Composable
fun RenewCrewPreview() = Frame { com.kurtz.homerecord.fieldlog.clock.ClockScreen(clockState(), {}, readOnly = true) }
