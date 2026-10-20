package com.kurtz.homerecord.housemanual

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.house.FloorsDialog
import com.kurtz.homerecord.housemanual.house.FloorsScreen
import com.kurtz.homerecord.housemanual.house.FloorsUiState
import com.kurtz.homerecord.housemanual.house.HouseRoot
import com.kurtz.homerecord.housemanual.onboarding.OnboardingScreen
import com.kurtz.homerecord.housemanual.onboarding.OnboardingUiState
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemanual.plan.PlanEditorScreen
import com.kurtz.homerecord.housemanual.plan.PlanEditorUiState
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.UnplacedRoom
import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.PlanOpening
import com.kurtz.homerecord.housemodel.plan.PlanStair
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "200% font", fontScale = 2f, showBackground = true)
annotation class ThemePreviews

@Composable
private fun Frame(content: @Composable () -> Unit) = HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { Surface { content() } }

private val floors = listOf(
    FloorSummary("f1", "Basement", 96, false, 4),
    FloorSummary("f2", "Main", 96, false, 7),
    FloorSummary("f3", "Upper", 96, false, 5),
)

private val home = HomeEntity(
    id = "h1", name = "Maple St", houseType = "COLONIAL", foundation = "BASEMENT", attachedGarage = true, createdAt = 1, updatedAt = 1,
)

@ThemePreviews
@Composable
fun OnboardingEmptyPreview() = Frame { OnboardingScreen(OnboardingUiState(), {}) }

@ThemePreviews
@Composable
fun OnboardingFilledPreview() = Frame {
    OnboardingScreen(
        OnboardingUiState(
            name = "Maple St",
            address = "418 Maple St, Harlan",
            type = HouseType.SPLIT_LEVEL,
            foundation = Foundation.WALKOUT_BASEMENT,
            attachedGarage = true,
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun OnboardingErrorsPreview() = Frame { OnboardingScreen(OnboardingUiState(showErrors = true), {}) }

@ThemePreviews
@Composable
fun FloorsListPreview() = Frame { FloorsScreen(FloorsUiState(loading = false, floors = floors), {}, {}, "Maple St") }

@ThemePreviews
@Composable
fun FloorsEmptyPreview() = Frame { FloorsScreen(FloorsUiState(loading = false), {}, {}, "Maple St") }

@ThemePreviews
@Composable
fun FloorsLoadingPreview() = Frame { FloorsScreen(FloorsUiState(), {}, {}, "Maple St") }

@ThemePreviews
@Composable
fun FloorsDeleteDialogPreview() = Frame {
    FloorsScreen(FloorsUiState(loading = false, floors = floors, dialog = FloorsDialog.Delete(floors[1])), {}, {}, "Maple St")
}

@ThemePreviews
@Composable
fun FloorsEditDialogPreview() = Frame {
    FloorsScreen(FloorsUiState(loading = false, floors = floors, dialog = FloorsDialog.Edit(floors[2])), {}, {}, "Maple St")
}

@ThemePreviews
@Composable
fun HouseRootPreview() = Frame { HouseRoot(home, floorCount = 3, onOpenFloors = {}) }

private val planRooms = listOf(
    PlanRoom.rectangle("r1", "Kitchen", "KITCHEN", 0, 0, 168, 144),
    PlanRoom.rectangle("r2", "Living room", "LIVING_ROOM", 168, 0, 240, 192),
    PlanRoom.rectangle("r3", "Dining room", "DINING_ROOM", 0, 144, 168, 120),
    PlanRoom.rectangle("r4", "Half bath", "BATHROOM", 168, 192, 72, 72),
)

private fun planState(vararg changes: PlanEditorUiState.() -> PlanEditorUiState): PlanEditorUiState =
    changes.fold(
        PlanEditorUiState(
            loading = false,
            floors = floors,
            floorId = "f2",
            rooms = planRooms,
            zoom = 0.8f,
            panX = 24f,
            panY = 24f,
            canUndo = true,
        ),
    ) { acc, f -> acc.f() }

@ThemePreviews
@Composable
fun PlanEditorPreview() = Frame { PlanEditorScreen(planState(), {}, {}, "Maple St") }

@ThemePreviews
@Composable
fun PlanEditorSelectedPreview() = Frame {
    PlanEditorScreen(planState({ copy(selectedId = "r1", canRedo = true) }), {}, {}, "Maple St")
}

@ThemePreviews
@Composable
fun PlanEditorShapePreview() = Frame {
    val lShape = PlanRoom("r2", "Living room", "LIVING_ROOM", listOf(Pt(168, 0), Pt(408, 0), Pt(408, 96), Pt(288, 96), Pt(288, 192), Pt(168, 192)))
    val rooms = planRooms.map { if (it.id == "r2") lShape else it }
    PlanEditorScreen(planState({ copy(rooms = rooms, selectedId = "r2", editShape = true) }), {}, {}, "Maple St")
}

private val planRoomsWithOpenings = planRooms.map { room ->
    when (room.id) {
        "r1" -> room
            .withOpening(PlanOpening("o1", 2, OpeningKind.DOOR, 60, 36, 80))
            .withOpening(PlanOpening("o2", 0, OpeningKind.WINDOW, 48, 48, 48))
        "r2" -> room
            .withOpening(PlanOpening("o3", 0, OpeningKind.WINDOW, 80, 60, 48))
            .withStair(PlanStair("s1", "f3", listOf(Pt(300, 40), Pt(336, 40), Pt(336, 160), Pt(300, 160))))
        else -> room
    }
}

@ThemePreviews
@Composable
fun PlanEditorWallPreview() = Frame {
    PlanEditorScreen(planState({ copy(rooms = planRoomsWithOpenings, selectedId = "r1", selectedWall = 3, wallTap = Pt(0, 70)) }), {}, {}, "Maple St")
}

@ThemePreviews
@Composable
fun PlanEditorDoorPreview() = Frame {
    PlanEditorScreen(planState({ copy(rooms = planRoomsWithOpenings, selectedId = "r1", selectedOpeningId = "o1") }), {}, {}, "Maple St")
}

@ThemePreviews
@Composable
fun PlanEditorStairsPreview() = Frame {
    PlanEditorScreen(planState({ copy(rooms = planRoomsWithOpenings, selectedId = "r2") }), {}, {}, "Maple St")
}

@ThemePreviews
@Composable
fun PlanEditorOverlapPreview() = Frame {
    val overlapping = planRooms + PlanRoom.rectangle("r5", "Pantry", "CLOSET", 120, 100, 96, 60)
    PlanEditorScreen(planState({ copy(rooms = overlapping, flaggedIds = setOf("r1", "r5"), selectedId = "r5") }), {}, {}, "Maple St")
}

@ThemePreviews
@Composable
fun PlanEditorUnplacedPreview() = Frame {
    PlanEditorScreen(
        planState({ copy(rooms = emptyList(), unplaced = listOf(UnplacedRoom("g1", "Garage", "GARAGE")), canUndo = false) }),
        {}, {}, "Maple St",
    )
}

@ThemePreviews
@Composable
fun PlanEditorEmptyPreview() = Frame {
    PlanEditorScreen(planState({ copy(rooms = emptyList(), canUndo = false, zoom = 1f, panX = 0f, panY = 0f) }), {}, {}, "Maple St")
}

@ThemePreviews
@Composable
fun PlanEditorLoadingPreview() = Frame { PlanEditorScreen(PlanEditorUiState(), {}, {}, "Maple St") }

@ThemePreviews
@Composable
fun PlanEditorNoFloorsPreview() = Frame { PlanEditorScreen(PlanEditorUiState(loading = false), {}, {}, "Maple St") }
