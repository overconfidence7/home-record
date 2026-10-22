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
        QaScreen("more-owner", nonRoot = false) { MoreOwnerPreview() },
        QaScreen("more-crew", nonRoot = false) { MoreCrewPreview() },
    )

    fun find(id: String?): QaScreen? = entries.firstOrNull { it.id == id }
}
