package com.kurtz.homerecord.fieldlog.qa

import androidx.compose.runtime.Composable
import com.kurtz.homerecord.fieldlog.*

/** One screen with realistic fake state; shared by the OPEN_SCREEN intent and the crawler (SPEC AC-U12). */
data class QaScreen(val id: String, val nonRoot: Boolean, val content: @Composable () -> Unit)

object ScreenCatalog {
    val entries: List<QaScreen> = listOf(
        QaScreen("welcome-create", nonRoot = false) { WelcomeCreatePreview() },
        QaScreen("welcome-join-error", nonRoot = false) { WelcomeJoinErrorPreview() },
        QaScreen("team-owner", nonRoot = true) { TeamOwnerPreview() },
        QaScreen("team-office", nonRoot = true) { TeamOfficePreview() },
        QaScreen("team-error", nonRoot = true) { TeamErrorPreview() },
        QaScreen("company-form", nonRoot = true) { CompanyFormPreview() },
        QaScreen("today-admin", nonRoot = false) { TodayAdminPreview() },
        QaScreen("today-crew", nonRoot = false) { TodayCrewPreview() },
        QaScreen("jobs-list", nonRoot = false) { JobsListPreview() },
        QaScreen("jobs-empty", nonRoot = false) { JobsEmptyPreview() },
        QaScreen("jobs-no-matches", nonRoot = false) { JobsNoMatchesPreview() },
        QaScreen("job-edit", nonRoot = true) { JobEditPreview() },
        QaScreen("job-overview", nonRoot = true) { JobOverviewPreview() },
        QaScreen("job-overview-empty", nonRoot = true) { JobOverviewEmptyPreview() },
        QaScreen("job-house-empty", nonRoot = true) { JobHouseEmptyPreview() },
        QaScreen("clock-out", nonRoot = false) { ClockOutPreview() },
        QaScreen("clock-working", nonRoot = false) { ClockWorkingPreview() },
        QaScreen("clock-break", nonRoot = false) { ClockBreakPreview() },
        QaScreen("clock-far-reason", nonRoot = false) { ClockFarReasonPreview() },
        QaScreen("clock-explain", nonRoot = false) { ClockExplainPreview() },
        QaScreen("clock-gps-required", nonRoot = false) { ClockGpsRequiredPreview() },
        QaScreen("clock-no-jobs", nonRoot = false) { ClockNoJobsPreview() },
        QaScreen("clock-loading", nonRoot = false) { ClockLoadingPreview() },
        QaScreen("more-owner", nonRoot = false) { MoreOwnerPreview() },
        QaScreen("more-crew", nonRoot = false) { MoreCrewPreview() },
    )

    fun find(id: String?): QaScreen? = entries.firstOrNull { it.id == id }
}
