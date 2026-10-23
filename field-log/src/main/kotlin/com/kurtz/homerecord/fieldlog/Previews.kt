package com.kurtz.homerecord.fieldlog

import android.content.res.Configuration
import androidx.compose.material3.Surface
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
