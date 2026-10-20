package com.kurtz.homerecord.housemanual.qa

import androidx.compose.runtime.Composable
import com.kurtz.homerecord.housemanual.*

/** One screen with realistic fake state; shared by the OPEN_SCREEN intent and the crawler (SPEC AC-U12). */
data class QaScreen(val id: String, val nonRoot: Boolean, val content: @Composable () -> Unit)

object ScreenCatalog {
    val entries: List<QaScreen> = listOf(
        QaScreen("onboarding-empty", nonRoot = false) { OnboardingEmptyPreview() },
        QaScreen("onboarding-filled", nonRoot = false) { OnboardingFilledPreview() },
        QaScreen("onboarding-errors", nonRoot = false) { OnboardingErrorsPreview() },
        QaScreen("floors-list", nonRoot = true) { FloorsListPreview() },
        QaScreen("floors-empty", nonRoot = true) { FloorsEmptyPreview() },
        QaScreen("floors-loading", nonRoot = true) { FloorsLoadingPreview() },
        QaScreen("floors-delete", nonRoot = true) { FloorsDeleteDialogPreview() },
        QaScreen("floors-edit", nonRoot = true) { FloorsEditDialogPreview() },
        QaScreen("plan-editor", nonRoot = true) { PlanEditorPreview() },
        QaScreen("plan-selected", nonRoot = true) { PlanEditorSelectedPreview() },
        QaScreen("plan-shape", nonRoot = true) { PlanEditorShapePreview() },
        QaScreen("plan-wall", nonRoot = true) { PlanEditorWallPreview() },
        QaScreen("plan-door", nonRoot = true) { PlanEditorDoorPreview() },
        QaScreen("plan-stairs", nonRoot = true) { PlanEditorStairsPreview() },
        QaScreen("plan-overlap", nonRoot = true) { PlanEditorOverlapPreview() },
        QaScreen("plan-unplaced", nonRoot = true) { PlanEditorUnplacedPreview() },
        QaScreen("plan-empty", nonRoot = true) { PlanEditorEmptyPreview() },
        QaScreen("plan-loading", nonRoot = true) { PlanEditorLoadingPreview() },
        QaScreen("plan-no-floors", nonRoot = true) { PlanEditorNoFloorsPreview() },
        QaScreen("house-blueprint", nonRoot = false) { HouseBlueprintPreview() },
        QaScreen("house-layers", nonRoot = false) { HouseLayersPreview() },
        QaScreen("house-room-menu", nonRoot = false) { HouseRoomMenuPreview() },
        QaScreen("house-empty-floor", nonRoot = false) { HouseEmptyFloorPreview() },
        QaScreen("house-loading", nonRoot = false) { HouseLoadingPreview() },
        QaScreen("house-no-floors", nonRoot = false) { HouseNoFloorsPreview() },
        QaScreen("room-page", nonRoot = true) { RoomPagePreview() },
        QaScreen("room-missing", nonRoot = true) { RoomMissingPreview() },
    )

    fun find(id: String?): QaScreen? = entries.firstOrNull { it.id == id }
}
