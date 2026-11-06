package com.kurtz.homerecord.housemanual

import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import com.kurtz.homerecord.coreui.pages.PageSource
import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.ui.FloorsDialog
import com.kurtz.homerecord.housemodel.ui.FloorsScreen
import com.kurtz.homerecord.housemodel.ui.FloorsUiState
import com.kurtz.homerecord.housemanual.onboarding.OnboardingScreen
import com.kurtz.homerecord.housemanual.onboarding.OnboardingUiState
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.ui.HouseDialog
import com.kurtz.homerecord.housemodel.ui.HouseScreen
import com.kurtz.homerecord.housemodel.ui.HouseUiState
import com.kurtz.homerecord.housemodel.ui.RoomItem
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallSide
import com.kurtz.homerecord.housemodel.walls.WallStage
import com.kurtz.homerecord.housemodel.ui.RoomScreen
import com.kurtz.homerecord.housemodel.ui.ItemEditScreen
import com.kurtz.homerecord.housemodel.ui.ItemEditUiState
import com.kurtz.homerecord.housemodel.RoomChoice
import com.kurtz.homerecord.housemodel.items.ItemDraft
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.data.ContactEntity
import com.kurtz.homerecord.housemanual.contacts.ContactFormContent
import com.kurtz.homerecord.housemanual.documents.DocumentFormContent
import com.kurtz.homerecord.housemanual.documents.DocumentForm
import com.kurtz.homerecord.housemanual.documents.DocumentsScreen
import com.kurtz.homerecord.housemanual.documents.DocumentsUiState
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.housemanual.contacts.ContactsScreen
import com.kurtz.homerecord.housemanual.contacts.ContactsUiState
import com.kurtz.homerecord.housemodel.upkeep.ContactDraft
import com.kurtz.homerecord.housemanual.items.ItemDetailScreen
import com.kurtz.homerecord.housemanual.items.ItemDetailUiState
import com.kurtz.homerecord.housemanual.home.HomeScreen
import com.kurtz.homerecord.housemanual.home.HomeUiState
import com.kurtz.homerecord.housemanual.home.SearchResultRow
import com.kurtz.homerecord.core.model.SearchKind
import com.kurtz.homerecord.housemodel.home.AttentionKind
import com.kurtz.homerecord.housemodel.home.AttentionRow
import com.kurtz.homerecord.housemanual.items.ItemsScreen
import com.kurtz.homerecord.housemanual.items.ItemsUiState
import com.kurtz.homerecord.housemanual.items.RoomOption
import com.kurtz.homerecord.housemodel.items.ItemFilter
import com.kurtz.homerecord.housemodel.items.ItemGrouping
import com.kurtz.homerecord.housemodel.items.ItemListBuilder
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.items.WarrantyFilter
import com.kurtz.homerecord.housemodel.ui.RoomUiState
import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer
import com.kurtz.homerecord.housemodel.blueprint.LayerSet
import com.kurtz.homerecord.housemodel.ui.PlanEditorScreen
import com.kurtz.homerecord.housemodel.ui.PlanEditorUiState
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.UnplacedRoom
import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.PlanOpening
import com.kurtz.homerecord.housemodel.plan.PlanStair
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import com.kurtz.homerecord.housemodel.walls.RemovedWallPhoto

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "200% font", fontScale = 2f, showBackground = true)
annotation class ThemePreviews

@Composable
private fun Frame(content: @Composable () -> Unit) = HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { Surface { content() } }

/**
 * A sheet's content as the app shows it: scrim above, the sheet's container, top corners and drag handle at the bottom,
 * clear of the status bar. Catalog sheet entries use it so the crawler sees what a user sees (QA rule 3).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(content: @Composable () -> Unit) = HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
            .statusBarsPadding(),
        contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
    ) {
        Surface(
            shape = androidx.compose.material3.BottomSheetDefaults.ExpandedShape,
            color = androidx.compose.material3.BottomSheetDefaults.ContainerColor,
            modifier = Modifier.padding(top = 48.dp),
        ) {
            androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                androidx.compose.material3.BottomSheetDefaults.DragHandle()
                content()
            }
        }
    }
}

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
            canUndo = true,
            canEditOverlays = true,
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
    PlanEditorScreen(planState({ copy(rooms = emptyList(), canUndo = false) }), {}, {}, "Maple St")
}

@ThemePreviews
@Composable
fun PlanEditorLoadingPreview() = Frame { PlanEditorScreen(PlanEditorUiState(), {}, {}, "Maple St") }

private fun planOverlay(id: String, kind: com.kurtz.homerecord.housemodel.overlays.OverlayKind, label: String, roomId: String, vararg xy: Float, depth: com.kurtz.homerecord.housemodel.overlays.OverlayDepth = com.kurtz.homerecord.housemodel.overlays.OverlayDepth.IN_SLAB) =
    com.kurtz.homerecord.housemodel.overlays.Overlay(
        id, "h1", roomId, null, kind, label, null, depth,
        xy.toList().chunked(2).map { com.kurtz.homerecord.housemodel.overlays.OverlayPoint(it[0], it[1]) },
        WallStage.ROUGH_IN, null, null, 0L,
    )

/** The main floor's plan overlays: the range's gas line from the meter side, the supply trunk overhead, drains and a register. */
private val planOverlays = listOf(
    planOverlay("p1", com.kurtz.homerecord.housemodel.overlays.OverlayKind.GAS, "Range gas line", "r2", 384f, 180f, 384f, 120f, 132f, 120f, 132f, 100f),
    planOverlay("p2", com.kurtz.homerecord.housemodel.overlays.OverlayKind.DUCT, "Supply trunk", "r1", 24f, 60f, 372f, 60f, depth = com.kurtz.homerecord.housemodel.overlays.OverlayDepth.IN_CEILING),
    planOverlay("p3", com.kurtz.homerecord.housemodel.overlays.OverlayKind.DRAIN, "Floor drain", "r4", 186f, 250f),
    planOverlay("p4", com.kurtz.homerecord.housemodel.overlays.OverlayKind.REGISTER, "", "r3", 84f, 240f, depth = com.kurtz.homerecord.housemodel.overlays.OverlayDepth.EXPOSED),
    planOverlay("p5", com.kurtz.homerecord.housemodel.overlays.OverlayKind.CLEANOUT, "Main stack cleanout", "r1", 150f, 24f),
)

/** Overlays mode (AC-C25): the floor's pipes, ducts and drains in their layer colors, Add overlay, what to tap. */
@ThemePreviews
@Composable
fun PlanEditorOverlaysPreview() = Frame { PlanEditorScreen(planState({ copy(rooms = planRoomsWithOpenings, overlayMode = true, overlays = planOverlays) }), {}, {}, "Maple St") }

/** Landscape: the room panel sits beside the plan and the L-shaped room shows whole (judge:hm-plan-editor-fit). */
@Preview(name = "Landscape", device = "spec:width=891dp,height=411dp")
@Composable
fun PlanEditorShapeLandscapePreview() = PlanEditorShapePreview()

/** Landscape Overlays: the hint panel sits beside the plan, which keeps the full height (judge:hm-plan-editor-landscape-panel). */
@Preview(name = "Landscape", device = "spec:width=891dp,height=411dp")
@Composable
fun PlanEditorOverlaysLandscapePreview() = PlanEditorOverlaysPreview()

/** The range gas line picked: handles on its points to drag, Edit, Delete and Done (AC-C28). */
@ThemePreviews
@Composable
fun PlanEditorOverlayPickedPreview() = Frame {
    PlanEditorScreen(planState({ copy(rooms = planRoomsWithOpenings, overlayMode = true, overlays = planOverlays, overlaySelectedId = "p1") }), {}, {}, "Maple St")
}

/** Drawing a new run on the plan: three taps so far, Undo, Cancel and Next (AC-C25). */
@ThemePreviews
@Composable
fun PlanEditorOverlayDrawingPreview() = Frame {
    val points = listOf(com.kurtz.homerecord.housemodel.overlays.OverlayPoint(36f, 132f), com.kurtz.homerecord.housemodel.overlays.OverlayPoint(36f, 200f), com.kurtz.homerecord.housemodel.overlays.OverlayPoint(120f, 200f))
    PlanEditorScreen(planState({ copy(rooms = planRoomsWithOpenings, overlayMode = true, overlays = planOverlays, drawing = points) }), {}, {}, "Maple St")
}

/** Overlays mode on a floor with nothing drawn yet. */
@ThemePreviews
@Composable
fun PlanEditorOverlaysEmptyPreview() = Frame { PlanEditorScreen(planState({ copy(rooms = planRoomsWithOpenings, overlayMode = true) }), {}, {}, "Maple St") }

@ThemePreviews
@Composable
fun PlanEditorNoFloorsPreview() = Frame { PlanEditorScreen(PlanEditorUiState(loading = false), {}, {}, "Maple St") }

private val houseRooms = planRoomsWithOpenings + PlanRoom(
    "r6", "Mudroom", "OTHER", listOf(Pt(240, 192), Pt(408, 192), Pt(408, 264), Pt(312, 264), Pt(312, 336), Pt(240, 336)),
)

private fun houseState(vararg changes: HouseUiState.() -> HouseUiState): HouseUiState =
    changes.fold(
        HouseUiState(loading = false, homeName = "Maple St", summary = "Colonial · Basement", floors = floors, floorId = "f2", rooms = houseRooms),
    ) { acc, f -> acc.f() }

@Composable
private fun houseScreen(state: HouseUiState) = HouseScreen(state, {}, {}, {}, {}, {}, on3D = {}, onOpenMark = {})

@ThemePreviews
@Composable
fun HouseBlueprintPreview() = Frame { houseScreen(houseState()) }

@Preview(name = "Landscape", device = "spec:width=891dp,height=411dp")
@Composable
fun HouseBlueprintLandscapePreview() = Frame { houseScreen(houseState()) }

@ThemePreviews
@Composable
fun HouseLayersPreview() = Frame {
    houseScreen(
        houseState({
            copy(
                layers = LayerSet(setOf(BlueprintLayer.ITEMS, BlueprintLayer.PLUMBING, BlueprintLayer.ELECTRICAL, BlueprintLayer.GAS, BlueprintLayer.DRAIN_VENT)),
                overlays = planOverlays,
                marks = houseMarks,
            )
        }),
    )
}

/** Wall overlays laid along their walls and placed items on the blueprint (AC-C15). */
private val houseMarks = com.kurtz.homerecord.housemodel.blueprint.PlanMarks.place(
    houseRooms,
    listOf(
        house3dOverlay("wm1", "r1", "w-r1-0", com.kurtz.homerecord.housemodel.overlays.OverlayKind.WATER_COLD, "Cold to sink", 0.1f to 0.95f, 0.1f to 0.6f, 0.55f to 0.6f),
        house3dOverlay("wm2", "r1", "w-r1-3", com.kurtz.homerecord.housemodel.overlays.OverlayKind.OUTLET, "Counter outlet", 0.3f to 0.62f),
        house3dOverlay("wm3", "r3", "w-r3-3", com.kurtz.homerecord.housemodel.overlays.OverlayKind.ELECTRICAL_WIRE, "Dining circuit", 0.1f to 0.85f, 0.9f to 0.85f),
        house3dOverlay("wm4", "r4", "w-r4-1", com.kurtz.homerecord.housemodel.overlays.OverlayKind.DRAIN, "Vanity drain", 0.5f to 0.9f, 0.5f to 0.2f),
    ),
    mapOf(
        "w-r1-0" to com.kurtz.homerecord.housemodel.walls.WallKey("r1", 0),
        "w-r1-3" to com.kurtz.homerecord.housemodel.walls.WallKey("r1", 3),
        "w-r3-3" to com.kurtz.homerecord.housemodel.walls.WallKey("r3", 3),
        "w-r4-1" to com.kurtz.homerecord.housemodel.walls.WallKey("r4", 1),
    ),
    listOf(
        com.kurtz.homerecord.housemodel.scene.ItemSpot("i1", "r3", 130, 240, "Chest freezer", iconName = "Kitchen"),
        com.kurtz.homerecord.housemodel.scene.ItemSpot("i2", "r6", 262, 310, "Water heater", iconName = "WaterDrop"),
        com.kurtz.homerecord.housemodel.scene.ItemSpot("i3", "r2", 220, 150, "Thermostat", iconName = "Thermostat"),
    ),
)

@ThemePreviews
@Composable
fun HouseRoomMenuPreview() = Frame { houseScreen(houseState({ copy(dialog = HouseDialog.Menu(houseRooms[0])) })) }

@ThemePreviews
@Composable
fun HouseEmptyFloorPreview() = Frame { houseScreen(houseState({ copy(rooms = emptyList(), floorId = "f1") })) }

@ThemePreviews
@Composable
fun HouseLoadingPreview() = Frame { houseScreen(HouseUiState(homeName = "Maple St", summary = "Colonial · Basement")) }

@ThemePreviews
@Composable
fun HouseNoFloorsPreview() = Frame { houseScreen(HouseUiState(loading = false, homeName = "Maple St", summary = "Colonial · Basement")) }

@ThemePreviews
@Composable
fun RoomPagePreview() = Frame { RoomScreen(RoomUiState(loading = false, room = houseRooms[0], floorName = "Main floor", homeName = "Maple St", canAddItem = true,
    floorId = "f2", canView3D = true,
    items = listOf(
        RoomItem("i1", "Dishwasher", "Bosch SHPM88Z75N", iconName = ItemCategory.subtype("dishwasher")?.iconName ?: "Category", warranty = WarrantyStatus.Active("2028-03-14")),
        RoomItem("i2", "Range hood", "Broan", iconName = ItemCategory.subtype("range_hood")?.iconName ?: "Category"),
    ),
    walls = kitchenWalls,
), {}) }

/** The kitchen's four walls: the sink wall photographed at rough-in and finished, the range wall finished only. */
private val kitchenWalls = listOf(
    RoomWall("w1", WallSide.NORTH, null, 168, 96, mapOf(WallStage.ROUGH_IN to "/preview/sink-rough.jpg", WallStage.FINISHED to "/preview/sink.jpg")),
    RoomWall("w2", WallSide.EAST, null, 144, 96, mapOf(WallStage.FINISHED to "/preview/range.jpg")),
    RoomWall("w3", WallSide.SOUTH, null, 168, 96),
    RoomWall("w4", WallSide.WEST, null, 144, 96),
)

/** The Walls section on its own (no items yet), on Rough-in, with Photograph on each wall (AC-C17). */
@ThemePreviews
@Composable
fun RoomWallsPreview() = Frame { RoomScreen(RoomUiState(loading = false, room = houseRooms[0], floorName = "Main floor", homeName = "Maple St", canAddItem = true,
    walls = kitchenWalls, wallStage = WallStage.ROUGH_IN, canPhotograph = true,
), {}) }

/**
 * The kitchen after its pantry notch was squared off on the plan (N001): three walls left, and the removed walls'
 * Rough-in and Finished photos kept under "Photos of removed walls" with their stage and date. Tall so the list shows.
 */
@Preview(name = "Light", showBackground = true, heightDp = 1300)
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, heightDp = 1300)
@Preview(name = "200% font", fontScale = 2f, showBackground = true, heightDp = 2200)
@Composable
fun RoomRemovedWallPhotosPreview() = Frame { RoomScreen(RoomUiState(loading = false, room = houseRooms[0], floorName = "Main floor", homeName = "Maple St",
    walls = kitchenWalls.take(3), wallStage = WallStage.FINISHED, canPhotograph = true,
    removedWallPhotos = listOf(
        RemovedWallPhoto("rp1", WallStage.FINISHED, "/preview/pantry.jpg", java.time.LocalDate.of(2026, 9, 2).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()),
        RemovedWallPhoto("rp2", WallStage.ROUGH_IN, "/preview/pantry-rough.jpg", java.time.LocalDate.of(2025, 4, 18).atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()),
    ),
), {}) }

@ThemePreviews
@Composable
fun RoomMissingPreview() = Frame { RoomScreen(RoomUiState(loading = false)) {} }

private val itemRooms = listOf(RoomChoice("r1", "Kitchen", "Main"), RoomChoice("r2", "Utility room", "Basement"), RoomChoice("r3", "Primary bedroom", "Upper"))

private val furnaceDraft = ItemDraft(
    subtypeId = "furnace", roomId = "r2", name = "Basement furnace", make = "Carrier", model = "59TP6A060S17",
    serial = "3416A12345", manufacturedOn = "2016-04-18", purchasedOn = "2016-09-02", price = "4850.00", vendor = "Harlan Heating",
)

@ThemePreviews
@Composable
fun ItemsEmptyPreview() = Frame { ItemsScreen(ItemsUiState(loading = false), {}) }

private fun itemRow(id: String, name: String, make: String?, model: String?, cat: ItemCategory, sub: String, room: String, w: WarrantyStatus) =
    ItemRow(id, name, make, model, cat, sub, "r-$room", room, w)

private val sampleItems = listOf(
    itemRow("i1", "Basement furnace", "Carrier", "59TP6A060S17", ItemCategory.HEATING_COOLING, "furnace", "Basement", WarrantyStatus.Expired("2026-04-18")),
    itemRow("i2", "Water heater", "Rheem", "XE50T10HD50U1", ItemCategory.WATER, "water_heater", "Basement", WarrantyStatus.Expiring("2026-12-02", 40)),
    itemRow("i3", "Dishwasher", "Bosch", "SHPM65Z55N", ItemCategory.APPLIANCES, "dishwasher", "Kitchen", WarrantyStatus.Active("2028-03-14")),
    itemRow("i4", "Refrigerator", "Whirlpool", null, ItemCategory.APPLIANCES, "refrigerator", "Kitchen", WarrantyStatus.Unknown),
)

private fun itemsState(grouping: ItemGrouping = ItemGrouping.CATEGORY, filter: ItemFilter = ItemFilter()) = ItemsUiState(
    loading = false, totalCount = sampleItems.size, grouping = grouping, filter = filter,
    // No preview searches, so the category name the search would match is never read.
    groups = ItemListBuilder.build(sampleItems, grouping, filter) { it.name },
    categoryOptions = sampleItems.map { it.category }.distinct().sorted(),
    roomOptions = listOf(RoomOption("r-Basement", "Basement"), RoomOption("r-Kitchen", "Kitchen")),
)

@ThemePreviews
@Composable
fun ItemsByCategoryPreview() = Frame { ItemsScreen(itemsState(), {}) }

@ThemePreviews
@Composable
fun ItemsByRoomPreview() = Frame { ItemsScreen(itemsState(ItemGrouping.ROOM), {}) }

@ThemePreviews
@Composable
fun ItemsFilteredPreview() = Frame { ItemsScreen(itemsState(filter = ItemFilter(warranty = WarrantyFilter.EXPIRED)), {}) }

@ThemePreviews
@Composable
fun ItemsNoMatchPreview() = Frame { ItemsScreen(itemsState(filter = ItemFilter(query = "zzz")), {}) }

@ThemePreviews
@Composable
fun ItemsLoadingPreview() = Frame { ItemsScreen(ItemsUiState(), {}) }

@ThemePreviews
@Composable
fun ItemEditNewPreview() = Frame { ItemEditScreen(ItemEditUiState(loading = false, rooms = itemRooms), {}, {}) }

@ThemePreviews
@Composable
fun ItemEditFilledPreview() = Frame { ItemEditScreen(ItemEditUiState(loading = false, rooms = itemRooms, draft = furnaceDraft.copy(id = "i1"), dirty = true), {}, {}) }

@ThemePreviews
@Composable
fun ItemEditErrorsPreview() = Frame {
    ItemEditScreen(ItemEditUiState(loading = false, rooms = itemRooms, draft = ItemDraft(price = "abc"), showErrors = true, dirty = true), {}, {})
}

/** The Main floor's walls with Finished photos on the kitchen's north and the living room's north wall (AC-C20). */
private val house3dScene = com.kurtz.homerecord.housemodel.scene.FloorScene.build(
    houseRooms,
    96,
    mapOf(
        com.kurtz.homerecord.housemodel.walls.WallKey("r1", 0) to mapOf(WallStage.FINISHED to "/media/kitchen-north-wall.jpg"),
        com.kurtz.homerecord.housemodel.walls.WallKey("r2", 0) to mapOf(
            WallStage.FINISHED to "/media/living-north-wall.jpg",
            WallStage.ROUGH_IN to "/media/living-north-rough.jpg",
        ),
    ),
)

private fun house3dState(vararg changes: com.kurtz.homerecord.housemodel.ui.House3DUiState.() -> com.kurtz.homerecord.housemodel.ui.House3DUiState) =
    changes.fold(
        com.kurtz.homerecord.housemodel.ui.House3DUiState(loading = false, homeName = "Maple St", floors = floors, floorId = "f2", scene = house3dScene),
    ) { acc, f -> acc.f() }

@Composable
private fun house3dScreen(state: com.kurtz.homerecord.housemodel.ui.House3DUiState) =
    com.kurtz.homerecord.housemodel.ui.House3DScreen(state, {}, {}, {})

@ThemePreviews
@Composable
fun House3DPreview() = Frame { house3dScreen(house3dState()) }

/** Standing in the kitchen after tapping it (AC-C22): titled by the room, Back to floor beside Reset view. */
@ThemePreviews
@Composable
fun House3DRoomPreview() = Frame { house3dScreen(house3dState({ copy(roomId = "r1") })) }

@Preview(name = "Landscape", device = "spec:width=891dp,height=411dp")
@Composable
fun House3DLandscapePreview() = Frame { house3dScreen(house3dState()) }

/** Overlays and items in 3D (AC-C23): Plumbing, Electrical and Items on; the kitchen's supply lines and outlets on its
 * north wall, the living room's gas line across the floor, the fridge and furnace at their plan spots. */
private fun house3dOverlay(id: String, room: String, wall: String?, kind: com.kurtz.homerecord.housemodel.overlays.OverlayKind, label: String, vararg pts: Pair<Float, Float>) =
    com.kurtz.homerecord.housemodel.overlays.Overlay(
        id, "h1", room, wall, kind, label, null, com.kurtz.homerecord.housemodel.overlays.OverlayDepth.BEHIND_DRYWALL,
        pts.map { com.kurtz.homerecord.housemodel.overlays.OverlayPoint(it.first, it.second) }, WallStage.ROUGH_IN, null, null, 0L,
    )

private val house3dMarkers = com.kurtz.homerecord.housemodel.scene.SceneMarkers.place(
    house3dScene,
    listOf(
        house3dOverlay("ov1", "r1", "w-r1-0", com.kurtz.homerecord.housemodel.overlays.OverlayKind.WATER_COLD, "Cold to sink", 0.1f to 0.95f, 0.1f to 0.6f, 0.55f to 0.6f),
        house3dOverlay("ov2", "r1", "w-r1-0", com.kurtz.homerecord.housemodel.overlays.OverlayKind.WATER_HOT, "Hot to sink", 0.15f to 0.95f, 0.15f to 0.68f, 0.55f to 0.68f),
        house3dOverlay("ov3", "r1", "w-r1-0", com.kurtz.homerecord.housemodel.overlays.OverlayKind.OUTLET, "Counter outlet", 0.8f to 0.62f),
        house3dOverlay("ov4", "r2", "w-r2-0", com.kurtz.homerecord.housemodel.overlays.OverlayKind.ELECTRICAL_WIRE, "Living room circuit", 0.05f to 0.85f, 0.95f to 0.85f),
        house3dOverlay("ov5", "r2", null, com.kurtz.homerecord.housemodel.overlays.OverlayKind.GAS, "Gas to fireplace", 190f to 180f, 300f to 180f, 300f to 30f),
    ),
    mapOf(
        "w-r1-0" to com.kurtz.homerecord.housemodel.walls.WallKey("r1", 0),
        "w-r2-0" to com.kurtz.homerecord.housemodel.walls.WallKey("r2", 0),
    ),
    listOf(
        com.kurtz.homerecord.housemodel.scene.ItemSpot("i1", "r1", 30, 30, "Refrigerator"),
        com.kurtz.homerecord.housemodel.scene.ItemSpot("i2", "r3", 120, 200, "Dining room light"),
    ),
)

@ThemePreviews
@Composable
fun House3DMarkersPreview() = Frame {
    house3dScreen(
        house3dState({
            copy(
                markers = house3dMarkers,
                layers = com.kurtz.homerecord.housemodel.blueprint.LayerSet(
                    setOf(
                        com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer.PLUMBING,
                        com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer.ELECTRICAL,
                        com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer.GAS,
                        com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer.ITEMS,
                    ),
                ),
            )
        }),
    )
}

/** Rough-in chosen: one wall has a rough-in photo. */
@ThemePreviews
@Composable
fun House3DRoughInPreview() = Frame { house3dScreen(house3dState({ copy(stage = WallStage.ROUGH_IN) })) }

/** The Upper floor has no rooms drawn yet. */
@ThemePreviews
@Composable
fun House3DEmptyPreview() = Frame {
    house3dScreen(house3dState({ copy(floorId = "f3", scene = com.kurtz.homerecord.housemodel.scene.FloorScene.build(emptyList(), 96, emptyMap())) }))
}

/** Basement and Upper rooms for the stacked 3D previews (AC-C21). */
private val basementRooms = listOf(
    PlanRoom.rectangle("b1", "Utility room", "UTILITY", 0, 0, 168, 264),
    PlanRoom.rectangle("b2", "Family room", "LIVING_ROOM", 168, 0, 240, 264),
)
private val upperRooms = listOf(
    PlanRoom.rectangle("u1", "Primary bedroom", "BEDROOM", 0, 0, 216, 168),
    PlanRoom.rectangle("u2", "Bedroom", "BEDROOM", 216, 0, 192, 168),
    PlanRoom.rectangle("u3", "Hall bath", "BATHROOM", 0, 168, 120, 96),
)
private val house3dPictures = mapOf(
    com.kurtz.homerecord.housemodel.walls.WallKey("r1", 0) to mapOf(WallStage.FINISHED to "/media/kitchen-north-wall.jpg"),
    com.kurtz.homerecord.housemodel.walls.WallKey("u1", 0) to mapOf(WallStage.FINISHED to "/media/bedroom-north-wall.jpg"),
)

/** Main chosen: the basement below drawn faded under it. */
@ThemePreviews
@Composable
fun House3DStackedPreview() = Frame {
    val scene = com.kurtz.homerecord.housemodel.scene.FloorScene.stack(
        listOf(
            com.kurtz.homerecord.housemodel.scene.SceneLayer(com.kurtz.homerecord.housemodel.scene.StackedFloor("f1", 96, -102, ghost = true), basementRooms),
            com.kurtz.homerecord.housemodel.scene.SceneLayer(com.kurtz.homerecord.housemodel.scene.StackedFloor("f2", 96, 0, ghost = false), houseRooms),
        ),
        house3dPictures,
    )
    house3dScreen(house3dState({ copy(scene = scene) }))
}

/** All floors pulled apart, bottom first. */
@ThemePreviews
@Composable
fun House3DAllFloorsPreview() = Frame {
    val stack = com.kurtz.homerecord.housemodel.scene.FloorStack.layout(
        floors.mapIndexed { i, f -> f.copy(elevationIn = (i - 1) * 108) },
        "f2",
        allFloors = true,
    )
    val rooms = mapOf("f1" to basementRooms, "f2" to houseRooms, "f3" to upperRooms)
    val scene = com.kurtz.homerecord.housemodel.scene.FloorScene.stack(
        stack.map { com.kurtz.homerecord.housemodel.scene.SceneLayer(it, rooms.getValue(it.floorId)) },
        house3dPictures,
    )
    house3dScreen(house3dState({ copy(allFloors = true, scene = scene) }))
}

@ThemePreviews
@Composable
fun House3DLoadingPreview() = Frame { house3dScreen(house3dState({ copy(loading = true, scene = null) })) }

@ThemePreviews
@Composable
fun House3DErrorPreview() = Frame { house3dScreen(house3dState({ copy(failed = true, scene = null) })) }

@ThemePreviews
@Composable
fun ItemEditTypeSheetPreview() = SheetFrame { com.kurtz.homerecord.housemodel.ui.SubtypeList("furnace", {}) }

@ThemePreviews
@Composable
fun ItemEditTypeSheetSearchPreview() = SheetFrame { com.kurtz.homerecord.housemodel.ui.SubtypeList(null, {}, query = "water") }

@ThemePreviews
@Composable
fun ItemEditPlateSheetPreview() = SheetFrame {
    com.kurtz.homerecord.housemodel.ui.PlateScanContent(
        com.kurtz.homerecord.housemodel.ui.PlateScan(
            lines = listOf("RHEEM", "MODEL NO: XG50T06EC36U1", "SERIAL NO: 1234567890", "50 GAL", "240 V AC"),
            make = "Rheem", model = "XG50T06EC36U1", target = com.kurtz.homerecord.core.model.PlateField.SERIAL,
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun ItemEditLoadingPreview() = Frame { ItemEditScreen(ItemEditUiState(loading = true), {}, {}) }

private val furnaceEntity = com.kurtz.homerecord.core.data.ItemEntity(
    id = "i1", homeId = "h1", roomId = "r2", category = "HEATING_COOLING", subtype = "furnace", name = "Basement furnace",
    make = "Carrier", model = "59TP6A060S17", serial = "3416A12345", manufacturedOn = "2016-04-18", purchasedOn = "2016-09-02",
    inServiceOn = "2016-09-20", priceCents = 485_000, vendor = "Harlan Heating", manualUrl = "https://example.com/manual.pdf",
    notes = "Filter is 16x25x1. Pilot light was replaced with an igniter in 2021.", createdAt = 1, updatedAt = 1,
)

private val furnaceWarranty = com.kurtz.homerecord.core.data.WarrantyEntity(
    id = "w1", itemId = "i1", partsMonths = 120, laborMonths = 12, endsOn = "2026-09-20", registered = true,
    sourceUrl = "https://example.com/carrier-warranty", lookedUpAt = 1_760_000_000_000, summary = "10-year limited parts when registered within 90 days.",
    createdAt = 1, updatedAt = 1,
)

private fun furnaceDetail(withWarranty: Boolean = true) = com.kurtz.homerecord.housemodel.items.ItemDetail(
    item = furnaceEntity, category = ItemCategory.HEATING_COOLING, subtypeLabel = "Furnace", roomName = "Utility room",
    warranty = furnaceWarranty.takeIf { withWarranty },
    status = if (withWarranty) WarrantyStatus.Expired("2026-09-20") else WarrantyStatus.Unknown, photos = emptyList(),
)

@ThemePreviews
@Composable
fun ItemDetailPreview() = Frame { ItemDetailScreen(ItemDetailUiState(loading = false, detail = furnaceDetail()), {}) }

private val furnaceLinks = listOf(
    com.kurtz.homerecord.housemodel.overlays.OverlayLink(
        sampleOverlay("o7", com.kurtz.homerecord.housemodel.overlays.OverlayKind.GAS, "Furnace gas line", 0.1f, 0.2f, 0.6f, 0.2f), "Utility room",
    ),
    com.kurtz.homerecord.housemodel.overlays.OverlayLink(
        sampleOverlay("o8", com.kurtz.homerecord.housemodel.overlays.OverlayKind.DUCT, "", 0.5f, 0f, 0.5f, 0.3f, depth = com.kurtz.homerecord.housemodel.overlays.OverlayDepth.IN_CEILING),
        "Utility room",
    ),
)

/** The furnace page with the overlays that serve it; Links sits below Documents (AC-C26). */
@ThemePreviews
@Composable
fun ItemDetailLinksPagePreview() = Frame { ItemDetailScreen(ItemDetailUiState(loading = false, detail = furnaceDetail(false), overlayLinks = furnaceLinks), {}) }

/** The item page's Links section on its own: the gas line and the duct drawn on walls that serve the furnace (AC-C26). */
@ThemePreviews
@Composable
fun ItemDetailLinksPreview() = Frame {
    androidx.compose.foundation.layout.Column(Modifier.padding(vertical = 16.dp)) { com.kurtz.homerecord.housemanual.items.ItemLinksSection(furnaceLinks) {} }
}

@ThemePreviews
@Composable
fun ItemDetailNoWarrantyPreview() = Frame { ItemDetailScreen(ItemDetailUiState(loading = false, detail = furnaceDetail(false)), {}) }

@ThemePreviews
@Composable
fun ItemDetailDeletePreview() = Frame { ItemDetailScreen(ItemDetailUiState(loading = false, detail = furnaceDetail(), confirmDelete = true), {}) }

@ThemePreviews
@Composable
fun ItemDetailMissingPreview() = Frame { ItemDetailScreen(ItemDetailUiState(loading = false), {}) }

@ThemePreviews
@Composable
fun ItemDetailLoadingPreview() = Frame { ItemDetailScreen(ItemDetailUiState(), {}) }

@ThemePreviews
@Composable
fun ItemDetailLookupNotePreview() = Frame {
    ItemDetailScreen(ItemDetailUiState(loading = false, detail = furnaceDetail(false), lookupNote = com.kurtz.homerecord.housemanual.items.LookupNote.Failed), {})
}

@ThemePreviews
@Composable
fun ItemDetailReviewBadgePreview() = Frame {
    val awaiting = furnaceDetail().copy(warranty = furnaceWarranty.copy(confidence = "REVIEW_HIGH"))
    ItemDetailScreen(ItemDetailUiState(loading = false, detail = awaiting), {})
}

@ThemePreviews
@Composable
fun ItemDetailReviewSheetPreview() = SheetFrame {
    com.kurtz.homerecord.housemanual.items.WarrantyReviewContent(
        com.kurtz.homerecord.core.warranty.WarrantyTerms(
            partsMonths = 120, laborMonths = 12, specialTerms = listOf("Heat exchanger: lifetime to the original owner"),
            sourceUrl = "https://example.com/carrier-warranty", confidence = "HIGH", summary = "10-year limited parts when registered within 90 days.",
        ),
        {},
    )
}

private val furnaceConsumables = listOf(
    com.kurtz.homerecord.core.data.ConsumableEntity("c1", "i1", "FILTER", "16x25x1 MERV 8", 2, "https://example.com/filters", 1, 1),
    com.kurtz.homerecord.core.data.ConsumableEntity("c2", "i1", "BATTERY", "9V thermostat battery", 1, null, 1, 1),
)

private val furnaceService = listOf(
    com.kurtz.homerecord.core.data.ServiceRecordEntity("s1", "i1", "2026-03-14", "c1", "Annual tune-up and igniter check", 14_900, 1, 1),
    com.kurtz.homerecord.core.data.ServiceRecordEntity("s2", "i1", "2024-11-02", null, "Replaced inducer motor", 62_500, 1, 1),
)

@ThemePreviews
@Composable
fun ItemDetailRecallsPreview() = Frame {
    ItemDetailScreen(
        ItemDetailUiState(
            loading = false, detail = furnaceDetail(),
            recalls = com.kurtz.homerecord.housemanual.items.RecallUi.Done(
                listOf(
                    com.kurtz.homerecord.core.lookup.RecallInfo("24-118", "Carrier gas furnaces recalled for overheating", "2024-03-14", "The heat exchanger can crack and leak carbon monoxide.", "Stop using the furnace and call Carrier for a free repair.", "https://www.cpsc.gov/Recalls/2024/example", true),
                    com.kurtz.homerecord.core.lookup.RecallInfo("19-042", "Carrier thermostats recalled", "2019-01-10", "The wiring can overheat.", null, null, false),
                ),
            ),
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun ItemDetailUpkeepPreview() = Frame {
    ItemDetailScreen(
        ItemDetailUiState(loading = false, detail = furnaceDetail(), consumables = furnaceConsumables, service = furnaceService, contacts = sampleContacts.map { it.contact },
            documents = listOf(DocumentEntity(id = "d1", homeId = "h1", itemId = "i1", folder = "Appliances", name = "Furnace manual.pdf", path = "p1", mime = "application/pdf", size = 2_411_520, createdAt = 2, updatedAt = 2)),
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun ItemDetailConsumableFormPreview() = Frame {
    FormScreenScaffold(stringResource(R.string.item_detail_consumable_add), "hm_item_detail_consumable_save", true, {}, {}) {
        com.kurtz.homerecord.housemanual.items.ConsumableFormContent(
            com.kurtz.homerecord.housemodel.upkeep.ConsumableDraft(id = null, spec = "", quantity = "2", reorderUrl = "https://example.com/filters"), true, {},
        )
    }
}

@ThemePreviews
@Composable
fun ItemDetailServiceFormPreview() = Frame {
    FormScreenScaffold(stringResource(R.string.item_detail_service_add), "hm_item_detail_service_save", true, {}, {}) {
        com.kurtz.homerecord.housemanual.items.ServiceFormContent(
            com.kurtz.homerecord.housemodel.upkeep.ServiceDraft(date = "2026-03-14", contactId = "c1", description = "Annual tune-up and igniter check", cost = "149.00"),
            sampleContacts.map { it.contact }, false, {},
        )
    }
}

/** Delete in the consumable form asks first (rule 16). */
@ThemePreviews
@Composable
fun ItemDetailConsumableDeletePreview() = Frame {
    val draft = com.kurtz.homerecord.housemodel.upkeep.ConsumableDraft(id = "c1", spec = "16x25x1 MERV 8", quantity = "2", reorderUrl = "https://example.com/filters")
    FormScreenScaffold(stringResource(R.string.item_detail_consumable_edit_title), "hm_item_detail_consumable_save", false, {}, {}) {
        com.kurtz.homerecord.housemanual.items.ConsumableFormContent(draft, false, {})
    }
    com.kurtz.homerecord.housemanual.items.UpkeepDeleteDialog(com.kurtz.homerecord.housemanual.items.UpkeepDelete.Consumable("c1", "16x25x1 MERV 8"), {})
}

private val sampleContacts = listOf(
    com.kurtz.homerecord.housemodel.upkeep.ContactRow(
        ContactEntity(id = "c1", homeId = "h1", name = "Dale Harlan", company = "Harlan Heating", trade = "HVAC", phone = "555-0142", email = "dale@harlanheating.example", notes = "Annual tune-up in September.", createdAt = 1, updatedAt = 1),
        2, listOf("i1"), listOf("Basement furnace"),
    ),
    com.kurtz.homerecord.housemodel.upkeep.ContactRow(
        ContactEntity(id = "c2", homeId = "h1", name = "Maria Soto", trade = "Plumber", phone = "555-0177", createdAt = 1, updatedAt = 1), 0, emptyList(), emptyList(),
    ),
)

@ThemePreviews
@Composable
fun ContactsListPreview() = Frame { ContactsScreen(ContactsUiState(loading = false, rows = sampleContacts), {}) }

@ThemePreviews
@Composable
fun ContactsEmptyPreview() = Frame { ContactsScreen(ContactsUiState(loading = false), {}) }

@ThemePreviews
@Composable
fun ContactsLoadingPreview() = Frame { ContactsScreen(ContactsUiState(), {}) }

@ThemePreviews
@Composable
fun ContactFormPreview() = Frame {
    FormScreenScaffold(stringResource(R.string.contacts_form_new), "hm_contacts_save", true, {}, {}) {
        ContactFormContent(ContactDraft(name = "Dale Harlan", company = "Harlan Heating", trade = "HVAC", phone = "555-0142", email = "dale@harlan"), true, {})
    }
}

private val sampleDocuments = listOf(
    com.kurtz.homerecord.housemodel.documents.DocumentFolder(
        "Appliances",
        listOf(
            DocumentEntity(id = "d1", homeId = "h1", folder = "Appliances", name = "Furnace manual.pdf", path = "p1", mime = "application/pdf", size = 2_411_520, createdAt = 2, updatedAt = 2),
            DocumentEntity(id = "d2", homeId = "h1", folder = "Appliances", name = "Water heater receipt.jpg", path = "p2", mime = "image/jpeg", size = 318_000, createdAt = 1, updatedAt = 1),
        ),
    ),
    com.kurtz.homerecord.housemodel.documents.DocumentFolder(
        "Permits",
        listOf(DocumentEntity(id = "d3", homeId = "h1", folder = "Permits", name = "Deck permit 2024.pdf", path = "p3", mime = "application/pdf", size = 96_000, createdAt = 3, updatedAt = 3)),
    ),
)

@ThemePreviews
@Composable
fun DocumentsListPreview() = Frame { DocumentsScreen(DocumentsUiState(loading = false, folders = sampleDocuments), {}) }

@ThemePreviews
@Composable
fun DocumentsEmptyPreview() = Frame { DocumentsScreen(DocumentsUiState(loading = false), {}) }

/** Catalog only: the menu animates open, so a static screenshot shows its first frame (the X, no items). */
@ThemePreviews
@Composable
fun DocumentsAddMenuPreview() = Frame { DocumentsScreen(DocumentsUiState(loading = false, addMenuOpen = true), {}) }

@ThemePreviews
@Composable
fun DocumentsLoadingPreview() = Frame { DocumentsScreen(DocumentsUiState(), {}) }

@ThemePreviews
@Composable
fun DocumentsUnsupportedPreview() = Frame { DocumentsScreen(DocumentsUiState(loading = false, folders = sampleDocuments, unsupportedNotice = true), {}) }

@ThemePreviews
@Composable
fun DocumentFormPreview() = SheetFrame {
    DocumentFormContent(DocumentForm("d1", "Furnace manual.pdf", "Appliances"), false, {})
}

/** A two-page "manual" drawn with canvas so the viewer previews show real pages. */
private object SamplePages : com.kurtz.homerecord.coreui.pages.PageSource {
    override val pageCount = 2
    override suspend fun render(index: Int, widthPx: Int) = peek(index)
    override fun peek(index: Int): androidx.compose.ui.graphics.ImageBitmap {
        val bmp = android.graphics.Bitmap.createBitmap(600, 780, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        canvas.drawColor(android.graphics.Color.WHITE)
        val ink = android.graphics.Paint().apply { color = android.graphics.Color.DKGRAY; textSize = 34f; isAntiAlias = true }
        canvas.drawText(if (index == 0) "Furnace owner's manual" else "Changing the filter", 40f, 80f, ink)
        ink.textSize = 24f
        listOf("Model GMVC96 · Serial 0415123456", "Replace the 16x25x1 filter every 3 months.", "Power off before opening the blower door.")
            .forEachIndexed { i, line -> canvas.drawText(line, 40f, 140f + i * 40f, ink) }
        return bmp.asImageBitmap()
    }
}

@ThemePreviews
@Composable
fun DocumentViewerPreview() = Frame {
    com.kurtz.homerecord.housemanual.documents.DocumentViewerScreen(
        com.kurtz.homerecord.housemanual.documents.DocumentViewerUiState(
            loading = false, name = "Furnace manual.pdf", path = "p1", mime = "application/pdf", pages = SamplePages,
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun DocumentViewerMissingPreview() = Frame {
    com.kurtz.homerecord.housemanual.documents.DocumentViewerScreen(
        com.kurtz.homerecord.housemanual.documents.DocumentViewerUiState(loading = false, name = "Deck permit 2024.pdf", missing = true),
        {},
    )
}

@ThemePreviews
@Composable
fun DocumentViewerOfflinePreview() = Frame {
    com.kurtz.homerecord.housemanual.documents.DocumentViewerScreen(
        com.kurtz.homerecord.housemanual.documents.DocumentViewerUiState(loading = false, name = "Roof warranty 2019.pdf", missing = true, offline = true),
        {},
    )
}

private val homeRecent = sampleItems.take(3)

private val homeAttention = listOf(
    AttentionRow(AttentionKind.REMINDER_OVERDUE, "t1", "i1", "Replace furnace filter", "2026-10-01"),
    AttentionRow(AttentionKind.WARRANTY_EXPIRING, "i2", "i2", "Water heater", "2026-12-02"),
    AttentionRow(AttentionKind.MISSING_MAKE_MODEL, "i4", "i4", "Refrigerator", ""),
)

@ThemePreviews
@Composable
fun HomeEmptyPreview() = Frame { HomeScreen(HomeUiState(loading = false, homeName = "Maple St"), {}) }

@ThemePreviews
@Composable
fun HomeFullPreview() = Frame {
    HomeScreen(HomeUiState(loading = false, homeName = "Maple St", itemCount = 4, shutoffCount = 3, attention = homeAttention, recent = homeRecent), {})
}

@ThemePreviews
@Composable
fun HomeQuietPreview() = Frame {
    HomeScreen(HomeUiState(loading = false, homeName = "Maple St", itemCount = 4, shutoffCount = 1, recent = homeRecent), {})
}

@ThemePreviews
@Composable
fun HomeSearchPreview() = Frame {
    HomeScreen(
        HomeUiState(
            loading = false, homeName = "Maple St", itemCount = 4, query = "garage",
            results = listOf(
                SearchResultRow(SearchKind.ROOM, "r1", "Garage", null),
                SearchResultRow(SearchKind.ITEM, "i1", "Garage freezer", "Frigidaire FFFH17F4QW"),
                SearchResultRow(SearchKind.OVERLAY, "o1", "Freezer outlet circuit", "Electrical wire · Garage", "electrical_wire"),
                SearchResultRow(SearchKind.DOCUMENT, "d1", "Garage door opener manual", "Manuals"),
                SearchResultRow(SearchKind.BREAKER, "b9", "Garage outlets", "Breaker 9 · Main panel · 20 A", openId = "panel-item", slot = 9),
            ),
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun HomeSearchNonePreview() = Frame { HomeScreen(HomeUiState(loading = false, homeName = "Maple St", itemCount = 4, query = "zzz"), {}) }

@ThemePreviews
@Composable
fun HomeLoadingPreview() = Frame { HomeScreen(HomeUiState(), {}) }

private val samplePlans = listOf(
    com.kurtz.homerecord.core.billing.ProPlan("pro_annual", com.kurtz.homerecord.core.billing.ProPlanKind.Annual, "$24.99 / year"),
    com.kurtz.homerecord.core.billing.ProPlan("pro_lifetime", com.kurtz.homerecord.core.billing.ProPlanKind.Lifetime, "$59.00"),
)

@ThemePreviews
@Composable
fun PaywallItemsPreview() = Frame {
    com.kurtz.homerecord.housemanual.paywall.PaywallScreen(
        com.kurtz.homerecord.housemanual.paywall.PaywallUiState(
            feature = com.kurtz.homerecord.core.billing.LimitedFeature.SixteenthItem, loading = false, plans = samplePlans,
        ), {},
    )
}

@ThemePreviews
@Composable
fun PaywallFailedPreview() = Frame {
    com.kurtz.homerecord.housemanual.paywall.PaywallScreen(com.kurtz.homerecord.housemanual.paywall.PaywallUiState(loading = false, loadFailed = true), {})
}

@ThemePreviews
@Composable
fun PaywallLoadingPreview() = Frame { com.kurtz.homerecord.housemanual.paywall.PaywallScreen(com.kurtz.homerecord.housemanual.paywall.PaywallUiState(), {}) }

@ThemePreviews
@Composable
fun MorePreview() = Frame { com.kurtz.homerecord.housemanual.more.MoreScreen({}) }

@ThemePreviews
@Composable
fun AccountSignedInPreview() = AccountSignedInFrame(com.kurtz.homerecord.housemanual.account.CloudCopyUiState())

/** Signed in, scrolled to the bottom so the Cloud copy section shows: the dialog asking first. */
@ThemePreviews
@Composable
fun AccountDeleteCloudConfirmPreview() = AccountSignedInFrame(com.kurtz.homerecord.housemanual.account.CloudCopyUiState(confirmOpen = true))

/** The delete never reached the server: the error line under the button. */
@ThemePreviews
@Composable
fun AccountDeleteCloudOfflinePreview() = AccountSignedInFrame(
    com.kurtz.homerecord.housemanual.account.CloudCopyUiState(failed = true, offline = true),
    syncPhase = com.kurtz.homerecord.core.sync.SyncPhase.Offline,
)

/** After the delete: signed out, the line above the sign-in form says the home is still here. */
@ThemePreviews
@Composable
fun AccountCloudDeletedPreview() = Frame {
    com.kurtz.homerecord.housemanual.account.AccountSyncScreen({}, cloudDeleted = true) { modifier ->
        com.kurtz.homerecord.coreui.auth.SignInScreen(com.kurtz.homerecord.coreui.auth.SignInUiState(email = "dana.ortiz@example.com"), {}, modifier)
    }
}

@Composable
private fun AccountSignedInFrame(
    cloud: com.kurtz.homerecord.housemanual.account.CloudCopyUiState,
    syncPhase: com.kurtz.homerecord.core.sync.SyncPhase = com.kurtz.homerecord.core.sync.SyncPhase.Idle,
) = Frame {
    com.kurtz.homerecord.housemanual.account.AccountSyncScreen({}) { modifier ->
        com.kurtz.homerecord.coreui.auth.AccountScreen(
            com.kurtz.homerecord.coreui.auth.AccountUiState(com.kurtz.homerecord.coreui.auth.AccountPhase.SignedIn, email = "dana.ortiz@example.com"),
            {},
            modifier,
            syncStatus = {
                com.kurtz.homerecord.coreui.components.SyncStatusChip(syncPhase, pending = 0, onClick = {})
                com.kurtz.homerecord.housemanual.account.SyncExplainer()
            },
            footer = { com.kurtz.homerecord.housemanual.account.CloudCopySection(cloud) {} },
        )
    }
}

@ThemePreviews
@Composable
fun AccountSignedOutPreview() = Frame {
    com.kurtz.homerecord.housemanual.account.AccountSyncScreen({}) { modifier ->
        com.kurtz.homerecord.coreui.auth.SignInScreen(com.kurtz.homerecord.coreui.auth.SignInUiState(email = "dana.whitfield@gmail.com"), {}, modifier)
    }
}

@ThemePreviews
@Composable
fun AccountSignOutConfirmPreview() = Frame {
    com.kurtz.homerecord.housemanual.account.AccountSyncScreen({}) { modifier ->
        com.kurtz.homerecord.coreui.auth.AccountScreen(
            com.kurtz.homerecord.coreui.auth.AccountUiState(com.kurtz.homerecord.coreui.auth.AccountPhase.SignedIn, email = "dana.ortiz@example.com", confirmSignOut = true),
            {},
            modifier,
        ) { com.kurtz.homerecord.housemanual.account.SyncExplainer() }
    }
}

/** A phone shot of a kitchen wall, a little off square, drawn with canvas so the outline previews show a real photo. */
private val sampleWallPhoto: androidx.compose.ui.graphics.painter.Painter by lazy {
    val w = 1200
    val h = 900
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint().apply { isAntiAlias = true }
    c.drawColor(android.graphics.Color.rgb(226, 224, 218))
    paint.color = android.graphics.Color.rgb(150, 112, 78)
    c.drawRect(0f, 780f, w.toFloat(), h.toFloat(), paint)
    fun quad(vararg p: Float) = android.graphics.Path().apply {
        moveTo(p[0], p[1]); lineTo(p[2], p[3]); lineTo(p[4], p[5]); lineTo(p[6], p[7]); close()
    }
    paint.color = android.graphics.Color.rgb(196, 207, 186)
    c.drawPath(quad(150f, 120f, 1080f, 170f, 1050f, 800f, 170f, 760f), paint)
    paint.color = android.graphics.Color.rgb(246, 244, 238)
    c.drawPath(quad(170f, 735f, 1052f, 772f, 1050f, 800f, 170f, 760f), paint)
    c.drawPath(quad(430f, 250f, 760f, 262f, 755f, 520f, 432f, 512f), paint)
    paint.color = android.graphics.Color.rgb(172, 200, 222)
    c.drawPath(quad(450f, 270f, 740f, 281f, 736f, 500f, 452f, 492f), paint)
    paint.color = android.graphics.Color.rgb(246, 244, 238)
    c.drawRect(590f, 270f, 600f, 500f, paint)
    c.drawRect(880f, 600f, 912f, 650f, paint)
    paint.color = android.graphics.Color.rgb(90, 90, 90)
    c.drawRect(893f, 612f, 899f, 622f, paint)
    c.drawRect(893f, 630f, 899f, 640f, paint)
    androidx.compose.ui.graphics.painter.BitmapPainter(bmp.asImageBitmap())
}

private val kitchenNorthWall = RoomWall("w1", WallSide.NORTH, null, 168, 96)

/** Handles dragged most of the way onto the wall's corners; the bottom right one still 10% in. */
private val outlineInProgress = com.kurtz.homerecord.housemodel.walls.WallOutline(
    com.kurtz.homerecord.housemodel.walls.PhotoPoint(0.125f, 0.133f),
    com.kurtz.homerecord.housemodel.walls.PhotoPoint(0.9f, 0.189f),
    com.kurtz.homerecord.housemodel.walls.PhotoPoint(0.9f, 0.9f),
    com.kurtz.homerecord.housemodel.walls.PhotoPoint(0.142f, 0.844f),
)

private val outlineState = com.kurtz.homerecord.housemodel.ui.WallOutlineUiState(
    loading = false, wall = kitchenNorthWall, stage = WallStage.FINISHED, photoPath = "/preview/kitchen-north.jpg",
    photoWidth = 1200, photoHeight = 900, outline = outlineInProgress,
)

/** HM-WallOutline as it opens after the shot, handles part way onto the corners (AC-C18). */
@ThemePreviews
@Composable
fun WallOutlinePreview() = Frame { com.kurtz.homerecord.housemodel.ui.WallOutlineScreen(outlineState, {}, sampleWallPhoto) }

/** Dragging the bottom right handle: the loupe shows the corner up close (AC-C18). */
@ThemePreviews
@Composable
fun WallOutlineDraggingPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.WallOutlineScreen(
        outlineState.copy(
            outline = outlineInProgress.moved(2, com.kurtz.homerecord.housemodel.walls.PhotoPoint(0.875f, 0.889f)),
            dragging = 2,
        ),
        {}, sampleWallPhoto,
    )
}

/** Use with the corners crossed: the reason sits above the buttons. */
@ThemePreviews
@Composable
fun WallOutlineCrossedPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.WallOutlineScreen(
        outlineState.copy(
            outline = outlineInProgress.moved(0, com.kurtz.homerecord.housemodel.walls.PhotoPoint(0.92f, 0.86f)),
            error = com.kurtz.homerecord.housemodel.ui.WallOutlineError.NotUsable,
        ),
        {}, sampleWallPhoto,
    )
}

/** Straightening and saving after Use. */
@ThemePreviews
@Composable
fun WallOutlineSavingPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.WallOutlineScreen(outlineState.copy(outline = outlineInProgress.moved(2, com.kurtz.homerecord.housemodel.walls.PhotoPoint(0.875f, 0.889f)), saving = true), {}, sampleWallPhoto)
}

/** The photo file is gone or not an image. */
@ThemePreviews
@Composable
fun WallOutlineUnreadablePreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.WallOutlineScreen(
        outlineState.copy(error = com.kurtz.homerecord.housemodel.ui.WallOutlineError.PhotoUnreadable, photoWidth = 0, photoHeight = 0), {},
    )
}

/** The kitchen's north wall straightened at rough-in: studs every 16 in, plates top and bottom, a window header (AC-C19). */
private val sampleRoughInTexture: androidx.compose.ui.graphics.painter.Painter by lazy {
    val w = 840
    val h = 480
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint()
    c.drawColor(android.graphics.Color.rgb(214, 196, 168))
    paint.color = android.graphics.Color.rgb(186, 150, 104)
    // 168 in wall at 5 px per inch: a stud every 16 in, plates top and bottom.
    var x = 0f
    while (x < w) { c.drawRect(x, 0f, x + 8f, h.toFloat(), paint); x += 80f }
    c.drawRect(0f, 0f, w.toFloat(), 14f, paint)
    c.drawRect(0f, h - 10f, w.toFloat(), h.toFloat(), paint)
    c.drawRect(420f, 150f, 640f, 168f, paint)
    paint.color = android.graphics.Color.rgb(120, 120, 120)
    c.drawRect(578f, 210f, 600f, 240f, paint)
    androidx.compose.ui.graphics.painter.BitmapPainter(bmp.asImageBitmap())
}

private fun sampleOverlay(id: String, kind: com.kurtz.homerecord.housemodel.overlays.OverlayKind, label: String, vararg xy: Float, depth: com.kurtz.homerecord.housemodel.overlays.OverlayDepth = com.kurtz.homerecord.housemodel.overlays.OverlayDepth.BEHIND_DRYWALL, wallId: String = "w1") =
    com.kurtz.homerecord.housemodel.overlays.Overlay(
        id, "h1", "r1", wallId, kind, label, null, depth,
        xy.toList().chunked(2).map { com.kurtz.homerecord.housemodel.overlays.OverlayPoint(it[0], it[1]) },
        WallStage.ROUGH_IN, null, null, 0L,
    )

/** The sink wall at rough-in: hot and cold supply up to the sink, the drain, and the counter outlet on its circuit. */
private val sinkWallOverlays = listOf(
    sampleOverlay("o1", com.kurtz.homerecord.housemodel.overlays.OverlayKind.WATER_HOT, "Sink supply", 0.30f, 1f, 0.30f, 0.62f, 0.42f, 0.62f),
    sampleOverlay("o2", com.kurtz.homerecord.housemodel.overlays.OverlayKind.WATER_COLD, "Sink supply", 0.35f, 1f, 0.35f, 0.68f, 0.42f, 0.68f),
    sampleOverlay("o3", com.kurtz.homerecord.housemodel.overlays.OverlayKind.DRAIN, "Sink drain", 0.39f, 1f, 0.39f, 0.76f, depth = com.kurtz.homerecord.housemodel.overlays.OverlayDepth.IN_SLAB),
    sampleOverlay("o4", com.kurtz.homerecord.housemodel.overlays.OverlayKind.ELECTRICAL_WIRE, "Counter circuit", 0.70f, 0f, 0.70f, 0.42f),
    sampleOverlay("o5", com.kurtz.homerecord.housemodel.overlays.OverlayKind.OUTLET, "Counter outlet", 0.70f, 0.46f),
)

private val wallOverlaysState = com.kurtz.homerecord.housemodel.ui.WallOverlaysUiState(
    loading = false, wall = kitchenWalls[0], roomName = "Kitchen", stage = WallStage.ROUGH_IN,
    picture = com.kurtz.homerecord.housemodel.ui.WallPicture("/preview/sink-rough-wall.jpg", null, 168f / 96f),
    overlays = sinkWallOverlays,
)

/** HM-Wall at rough-in with five overlays on the straightened photo and the list below (AC-C25, AC-C27). */
@ThemePreviews
@Composable
fun WallOverlaysPreview() = Frame { com.kurtz.homerecord.housemodel.ui.WallOverlaysScreen(wallOverlaysState, {}, sampleRoughInTexture) }

/** Drawing a new run: two points down, Undo, Cancel and Next (AC-C25). */
@ThemePreviews
@Composable
fun WallOverlaysDrawingPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.WallOverlaysScreen(
        wallOverlaysState.copy(drawing = listOf(com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.1f, 0.85f), com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.1f, 0.3f))),
        {}, sampleRoughInTexture,
    )
}

/** The hot water run picked: its points as handles, Edit, Delete and Done (AC-C28). */
@ThemePreviews
@Composable
fun WallOverlaysSelectedPreview() = Frame { com.kurtz.homerecord.housemodel.ui.WallOverlaysScreen(wallOverlaysState.copy(selectedId = "o1"), {}, sampleRoughInTexture) }

/** A wall with no framing photo and nothing drawn: the plain wall and how to start. */
@ThemePreviews
@Composable
fun WallOverlaysEmptyPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.WallOverlaysScreen(wallOverlaysState.copy(stage = WallStage.FRAMING, picture = null, overlays = emptyList()), {})
}

/** Delete asks first (rule 16). */
@ThemePreviews
@Composable
fun WallOverlaysDeletePreview() = Frame { com.kurtz.homerecord.housemodel.ui.WallOverlaysScreen(wallOverlaysState.copy(selectedId = "o5", confirmDelete = true), {}, sampleRoughInTexture) }

private val overlayForm = com.kurtz.homerecord.housemodel.ui.OverlayForm(
    points = sinkWallOverlays[0].points, kind = com.kurtz.homerecord.housemodel.overlays.OverlayKind.WATER_HOT,
    label = "Sink supply", note = "1/2 in PEX, stubbed out 18 in above the floor", stage = WallStage.ROUGH_IN, dirty = true,
)

/** The overlay form filled in for the hot water run (AC-C26). */
@ThemePreviews
@Composable
fun OverlayFormPreview() = Frame { com.kurtz.homerecord.housemodel.ui.OverlayFormScreen(overlayForm, {}, canAddPhoto = true) }

private val overlayItems = listOf(
    com.kurtz.homerecord.housemodel.overlays.OverlayItemChoice("i2", "Dishwasher", "r1", "Kitchen", "dishwasher"),
    com.kurtz.homerecord.housemodel.overlays.OverlayItemChoice("i3", "Kitchen sink faucet", "r1", "Kitchen", "faucet"),
    com.kurtz.homerecord.housemodel.overlays.OverlayItemChoice("i1", "Basement furnace", "r2", "Utility room", "furnace"),
    com.kurtz.homerecord.housemodel.overlays.OverlayItemChoice("i4", "Water heater", "r2", "Utility room", "water_heater"),
    com.kurtz.homerecord.housemodel.overlays.OverlayItemChoice("i5", "Main panel", null, null, "breaker_panel"),
)

/** The drain filled in with the dishwasher it serves and a close-up photo, Replace and Remove (AC-C26). */
@ThemePreviews
@Composable
fun OverlayFormLinkedPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.OverlayFormScreen(
        com.kurtz.homerecord.housemodel.ui.OverlayForm(
            editingId = "o3", points = sinkWallOverlays[2].points, kind = com.kurtz.homerecord.housemodel.overlays.OverlayKind.DRAIN,
            label = "Sink drain", depth = com.kurtz.homerecord.housemodel.overlays.OverlayDepth.IN_SLAB, stage = WallStage.ROUGH_IN,
            photoPath = "/preview/drain.jpg", itemId = "i2", note = "1-1/2 in ABS with a cleanout under the sink",
        ),
        {}, items = overlayItems, linked = overlayItems[0], canAddPhoto = true, photo = sampleRoughInTexture,
    )
}

/** The linked-item sheet's list: the kitchen's items first, the chosen one marked, "Nothing linked" on top (AC-C26). */
@ThemePreviews
@Composable
fun OverlayItemListPreview() = SheetFrame { com.kurtz.homerecord.housemodel.ui.ItemPickList("", "i2", overlayItems, houseHasItems = true) {} }

/** The item sheet when a search matches nothing: says so and offers Clear search (rule 11). */
@ThemePreviews
@Composable
fun OverlayItemListNoMatchPreview() = SheetFrame { com.kurtz.homerecord.housemodel.ui.ItemPickList("garage door", null, emptyList(), houseHasItems = true) {} }

/** The sink drain picked on the wall: the dishwasher it serves and its photo beside it (AC-C26). */
@ThemePreviews
@Composable
fun WallOverlaysSelectedLinkedPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.WallOverlaysScreen(
        wallOverlaysState.copy(
            overlays = sinkWallOverlays.map { if (it.id == "o3") it.copy(itemId = "i2", photoPath = "/preview/drain.jpg") else it },
            selectedId = "o3", items = overlayItems,
        ),
        {}, sampleRoughInTexture,
    )
}

/** Save pressed before a kind was picked: the kind field says what is missing (rule 21). */
@ThemePreviews
@Composable
fun OverlayFormMissingKindPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.OverlayFormScreen(com.kurtz.homerecord.housemodel.ui.OverlayForm(points = sinkWallOverlays[4].points, stage = WallStage.ROUGH_IN, triedSave = true), {})
}

/** The kind sheet's list: every kind with its icon in its layer color (AC-C25, rule 26). */
@ThemePreviews
@Composable
fun OverlayKindListPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.KindList(com.kurtz.homerecord.housemodel.overlays.OverlayKind.WATER_HOT, androidx.compose.foundation.isSystemInDarkTheme()) {}
}

/** The room page's Overlays section listing the sink wall's overlays; each opens its wall (AC-C25). */
@ThemePreviews
@Composable
fun RoomOverlaysPreview() = Frame {
    RoomScreen(RoomUiState(loading = false, room = houseRooms[0], floorName = "Main floor", homeName = "Maple St", walls = kitchenWalls, wallStage = WallStage.ROUGH_IN,
        canOpenWalls = true, overlays = sinkWallOverlays.take(3) + sinkWallOverlays[4]), {})
}

/** The Kitchen's "Breakers feeding this room": one confirmed, one likely, one not yet checked (AC-C41). */
@ThemePreviews
@Composable
fun RoomBreakersPreview() = Frame {
    fun fed(slot: Int, amps: Int, label: String, c: com.kurtz.homerecord.core.model.LinkConfidence) = com.kurtz.homerecord.housemodel.panel.FeedingBreaker(
        com.kurtz.homerecord.core.model.Breaker("b$slot", "p1", listOf(slot), 1, amps, com.kurtz.homerecord.core.model.BreakerType.STANDARD, label, c), "i-panel", "Main panel", c,
    )
    val breakers = listOf(
        fed(2, 20, "Kitchen counter", com.kurtz.homerecord.core.model.LinkConfidence.CONFIRMED),
        fed(4, 20, "Refrigerator", com.kurtz.homerecord.core.model.LinkConfidence.LIKELY),
        fed(12, 20, "Dishwasher", com.kurtz.homerecord.core.model.LinkConfidence.UNKNOWN),
    )
    RoomScreen(RoomUiState(loading = false, room = houseRooms[0], floorName = "Main floor", homeName = "Maple St", breakers = breakers), {})
}

/** The Kitchen's "Shutoffs here": the sink's cold and hot valves (AC-C45). */
@ThemePreviews
@Composable
fun RoomShutoffsPreview() = Frame {
    val shutoffs = listOf(
        com.kurtz.homerecord.housemodel.ui.RoomItem("v-cold", "Kitchen sink cold valve", "BrassCraft quarter-turn", "PowerOff", category = ItemCategory.SHUTOFFS, subtypeId = "fixture_shutoff"),
        com.kurtz.homerecord.housemodel.ui.RoomItem("v-hot", "Kitchen sink hot valve", "", "PowerOff", category = ItemCategory.SHUTOFFS, subtypeId = "fixture_shutoff"),
    )
    RoomScreen(RoomUiState(loading = false, room = houseRooms[0], floorName = "Main floor", homeName = "Maple St", shutoffs = shutoffs), {})
}

/** Place on plan (AC-C30 position): the refrigerator placed against the Kitchen's left wall on the Main floor, the floor's other items faint. */
private fun itemPlaceState(spot: com.kurtz.homerecord.housemodel.items.ItemPlace?, rooms: List<PlanRoom> = houseRooms) =
    com.kurtz.homerecord.housemodel.ui.ItemPlaceUiState(
        loading = false, itemId = "i9", itemName = "Refrigerator",
        iconName = ItemCategory.subtype("refrigerator")?.iconName ?: "Kitchen",
        floors = floors, floorId = "f2", rooms = rooms, spot = spot,
        walls = if (spot?.roomId == "r1") kitchenWalls else emptyList(),
        // The floor's other placed items, drawn faintly for reference (T055d).
        others = listOf(
            com.kurtz.homerecord.housemodel.scene.ItemSpot("i1", "r3", 130, 240, "Chest freezer", iconName = "Kitchen"),
            com.kurtz.homerecord.housemodel.scene.ItemSpot("i2", "r6", 262, 310, "Water heater", iconName = "WaterDrop"),
            com.kurtz.homerecord.housemodel.scene.ItemSpot("i3", "r2", 220, 150, "Thermostat", iconName = "Thermostat"),
        ),
    )

@ThemePreviews
@Composable
fun ItemPlacePreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.ItemPlaceScreen(itemPlaceState(com.kurtz.homerecord.housemodel.items.ItemPlace("r1", 24, 110)), {})
}

/** On a wall (AC-C30 position): the thermostat hung on the Kitchen's south wall, the wall drawn to its 14' x 8'. */
@ThemePreviews
@Composable
fun ItemPlaceOnWallPreview() = Frame {
    com.kurtz.homerecord.housemodel.ui.ItemPlaceScreen(
        com.kurtz.homerecord.housemodel.ui.ItemPlaceUiState(
            loading = false, itemId = "i12", itemName = "Hallway thermostat",
            iconName = ItemCategory.subtype("thermostat")?.iconName ?: "Thermostat",
            floors = floors, floorId = "f2", rooms = houseRooms,
            spot = com.kurtz.homerecord.housemodel.items.ItemPlace("r1", 84, 138),
            walls = kitchenWalls, wallMode = true, wallId = "w3",
            hungOn = com.kurtz.homerecord.housemodel.ui.WallSpot("w3", 0.5f, 0.45f),
        ),
        {},
    )
}

/** Place on plan before the item has a spot: the hint asks for a tap. */
@ThemePreviews
@Composable
fun ItemPlaceUnplacedPreview() = Frame { com.kurtz.homerecord.housemodel.ui.ItemPlaceScreen(itemPlaceState(null), {}) }

/** Place on plan on a floor with no rooms drawn: the editor is one tap away. */
@ThemePreviews
@Composable
fun ItemPlaceEmptyFloorPreview() = Frame { com.kurtz.homerecord.housemodel.ui.ItemPlaceScreen(itemPlaceState(null, emptyList()), {}) }

private val sampleImport = com.kurtz.homerecord.housemanual.importing.ImportUiState(
    loading = false,
    homeName = "Ortiz house",
    counts = listOf(
        com.kurtz.homerecord.housemanual.importing.ImportCount(com.kurtz.homerecord.housemanual.importing.ImportKind.Floors, 1),
        com.kurtz.homerecord.housemanual.importing.ImportCount(com.kurtz.homerecord.housemanual.importing.ImportKind.Rooms, 4),
        com.kurtz.homerecord.housemanual.importing.ImportCount(com.kurtz.homerecord.housemanual.importing.ImportKind.WallPhotos, 6),
        com.kurtz.homerecord.housemanual.importing.ImportCount(com.kurtz.homerecord.housemanual.importing.ImportKind.Overlays, 3),
        com.kurtz.homerecord.housemanual.importing.ImportCount(com.kurtz.homerecord.housemanual.importing.ImportKind.Items, 1),
        com.kurtz.homerecord.housemanual.importing.ImportCount(com.kurtz.homerecord.housemanual.importing.ImportKind.ChangeOrders, 2),
    ),
)

@ThemePreviews
@Composable
fun ImportPreviewPreview() = Frame { com.kurtz.homerecord.housemanual.importing.ImportScreen(sampleImport, {}) }

@ThemePreviews
@Composable
fun ImportConflictsPreview() = Frame {
    com.kurtz.homerecord.housemanual.importing.ImportScreen(
        sampleImport.copy(
            counts = sampleImport.counts.drop(2).take(2),
            conflicts = listOf(
                com.kurtz.homerecord.housemanual.importing.ImportConflict("room", "r1", com.kurtz.homerecord.housemanual.importing.ImportKind.Rooms, "Kitchen"),
                com.kurtz.homerecord.housemanual.importing.ImportConflict("item", "i1", com.kurtz.homerecord.housemanual.importing.ImportKind.Items, "Water heater", useImported = true),
            ),
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun ImportNothingNewPreview() = Frame { com.kurtz.homerecord.housemanual.importing.ImportScreen(sampleImport.copy(counts = emptyList()), {}) }

@ThemePreviews
@Composable
fun ImportImportingPreview() = Frame { com.kurtz.homerecord.housemanual.importing.ImportScreen(sampleImport.copy(importing = true), {}) }

@ThemePreviews
@Composable
fun ImportDonePreview() = Frame { com.kurtz.homerecord.housemanual.importing.ImportScreen(sampleImport.copy(done = true), {}) }

@ThemePreviews
@Composable
fun ImportErrorPreview() = Frame {
    com.kurtz.homerecord.housemanual.importing.ImportScreen(
        com.kurtz.homerecord.housemanual.importing.ImportUiState(loading = false, error = com.kurtz.homerecord.housemanual.importing.ImportError.NotABundle), {},
    )
}

@ThemePreviews
@Composable
fun ImportLoadingPreview() = Frame { com.kurtz.homerecord.housemanual.importing.ImportScreen(com.kurtz.homerecord.housemanual.importing.ImportUiState(), {}) }

// HM-Claim: the contractor's handoff link (AC-H17, AC-B40). The sign-in slot shows the real form's fields.
@Composable
private fun ClaimSignInForm(modifier: androidx.compose.ui.Modifier) = com.kurtz.homerecord.coreui.auth.SignInScreen(
    com.kurtz.homerecord.coreui.auth.SignInUiState(email = "dana.ortiz@example.com"), {}, modifier,
)

@ThemePreviews
@Composable
fun ClaimSignInPreview() = Frame {
    com.kurtz.homerecord.housemanual.importing.ClaimScreen(
        com.kurtz.homerecord.housemanual.importing.ClaimUiState(phase = com.kurtz.homerecord.housemanual.importing.ClaimPhase.SignIn), {},
    ) { ClaimSignInForm(it) }
}

@ThemePreviews
@Composable
fun ClaimDownloadingPreview() = Frame {
    com.kurtz.homerecord.housemanual.importing.ClaimScreen(
        com.kurtz.homerecord.housemanual.importing.ClaimUiState(
            phase = com.kurtz.homerecord.housemanual.importing.ClaimPhase.Downloading, companyName = "Hale Electric", homeName = "Ortiz house",
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun ClaimOfflinePreview() = Frame {
    com.kurtz.homerecord.housemanual.importing.ClaimScreen(
        com.kurtz.homerecord.housemanual.importing.ClaimUiState(
            phase = com.kurtz.homerecord.housemanual.importing.ClaimPhase.Failed, failure = com.kurtz.homerecord.core.bundle.HandoffClaimFailure.Offline,
        ),
        {},
    )
}

@ThemePreviews
@Composable
fun ClaimExpiredPreview() = Frame {
    com.kurtz.homerecord.housemanual.importing.ClaimScreen(
        com.kurtz.homerecord.housemanual.importing.ClaimUiState(
            phase = com.kurtz.homerecord.housemanual.importing.ClaimPhase.Failed, failure = com.kurtz.homerecord.core.bundle.HandoffClaimFailure.Expired,
        ),
        {},
    )
}

// HM-Panel (AC-C40): a 200 A Square D panel with the breakers a homeowner labels first.
private val samplePanel = com.kurtz.homerecord.core.model.Panel("p1", "i-panel", 40, com.kurtz.homerecord.core.model.PanelLayout.NORTH_AMERICAN, 200, "Square D QO", null)

private fun sampleBreaker(id: String, slots: List<Int>, amps: Int, label: String, type: com.kurtz.homerecord.core.model.BreakerType = com.kurtz.homerecord.core.model.BreakerType.STANDARD, at: Long = 1) =
    com.kurtz.homerecord.core.model.Breaker(id, "p1", slots, slots.size, amps, type, label, com.kurtz.homerecord.core.model.LinkConfidence.UNKNOWN, at)

private val sampleBreakers = listOf(
    sampleBreaker("b1", listOf(1, 3), 40, "Range"),
    sampleBreaker("b2", listOf(2), 20, "Kitchen counter", com.kurtz.homerecord.core.model.BreakerType.GFCI),
    sampleBreaker("b3", listOf(4), 20, "Refrigerator"),
    sampleBreaker("b4", listOf(5), 15, "Living room lights", com.kurtz.homerecord.core.model.BreakerType.AFCI),
    sampleBreaker("b5", listOf(6, 8), 30, "Water heater"),
    sampleBreaker("b6", listOf(7), 15, "Porch lights", at = 1),
    sampleBreaker("b7", listOf(7), 15, "Garage door opener", at = 2),
    sampleBreaker("b8", listOf(9), 20, "Bathroom", com.kurtz.homerecord.core.model.BreakerType.DUAL_FUNCTION),
    sampleBreaker("b9", listOf(12), 20, "Dishwasher"),
)

private fun panelState(panel: com.kurtz.homerecord.core.model.Panel, breakers: List<com.kurtz.homerecord.core.model.Breaker>) =
    com.kurtz.homerecord.housemanual.panel.PanelUiState(
        loading = false, name = "Main panel", panel = panel, breakers = breakers,
        grid = com.kurtz.homerecord.core.model.SlotOccupancy.grid(panel.layout, panel.slotCount, breakers),
    )

@ThemePreviews
@Composable
fun PanelPreview() = Frame { com.kurtz.homerecord.housemanual.panel.PanelScreen(panelState(samplePanel, sampleBreakers), {}) }

/** Opened from the refrigerator's "Breaker 4" chip (AC-H7): slot 4 is outlined for 2 s after the panel scrolls to it. */
@ThemePreviews
@Composable
fun PanelHighlightPreview() = Frame {
    com.kurtz.homerecord.housemanual.panel.PanelScreen(panelState(samplePanel, sampleBreakers).copy(highlightSlot = 4), {})
}

/** The furnace's Power chip: the breaker that feeds it (AC-C41). */
@ThemePreviews
@Composable
fun ItemDetailPowerPreview() = Frame {
    val feeding = listOf(
        com.kurtz.homerecord.housemodel.panel.FeedingBreaker(
            sampleBreaker("b14", listOf(14), 15, "Furnace"), "i-panel", "Main panel", com.kurtz.homerecord.core.model.LinkConfidence.CONFIRMED,
        ),
    )
    ItemDetailScreen(ItemDetailUiState(loading = false, detail = furnaceDetail(false), feeding = feeding), {})
}

private val furnaceShutoffs = listOf(
    com.kurtz.homerecord.housemanual.items.ShutoffChoice("s-gas", "Gas main", "Utility room"),
    com.kurtz.homerecord.housemanual.items.ShutoffChoice("s-furnace", "Furnace gas valve", "Basement"),
    com.kurtz.homerecord.housemanual.items.ShutoffChoice("s-disc", "Furnace service switch", "Basement"),
)

/** The furnace's Shutoff link: "Shutoff: Furnace gas valve" with Change (AC-C45). */
@ThemePreviews
@Composable
fun ItemDetailShutoffPreview() = Frame {
    ItemDetailScreen(ItemDetailUiState(loading = false, detail = furnaceDetail(false), shutoff = furnaceShutoffs[1], shutoffChoices = furnaceShutoffs), {})
}

/** The "Which shutoff turns this off?" sheet: the home's shutoffs, the linked one checked, and No shutoff (AC-C45). */
@ThemePreviews
@Composable
fun ItemShutoffPickerPreview() = Frame {
    com.kurtz.homerecord.housemanual.items.ShutoffPickerContent(ItemDetailUiState(loading = false, detail = furnaceDetail(false), shutoff = furnaceShutoffs[1], shutoffChoices = furnaceShutoffs), {})
}

private val furnaceMoveChoices = listOf(
    com.kurtz.homerecord.housemanual.items.MoveChoice("r-util", "Utility room", "Basement"),
    com.kurtz.homerecord.housemanual.items.MoveChoice("r-rec", "Rec room", "Basement"),
    com.kurtz.homerecord.housemanual.items.MoveChoice("r-kitchen", "Kitchen", "Main floor"),
    com.kurtz.homerecord.housemanual.items.MoveChoice("r-mud", "Mudroom", "Main floor"),
    com.kurtz.homerecord.housemanual.items.MoveChoice("r-hall", "Upstairs hall", "Second floor"),
    com.kurtz.homerecord.housemanual.items.MoveChoice(com.kurtz.homerecord.housemodel.items.PseudoRooms.EXTERIOR, "Exterior"),
    com.kurtz.homerecord.housemanual.items.MoveChoice(com.kurtz.homerecord.housemodel.items.PseudoRooms.WHOLE_HOUSE, "Whole house"),
)

/** Overflow "Move to room": the home's rooms by floor, the furnace's room checked, then Exterior and Whole house. */
@ThemePreviews
@Composable
fun ItemMoveRoomPreview() = Frame {
    val detail = furnaceDetail(false).let { it.copy(item = it.item.copy(roomId = "r-util")) }
    com.kurtz.homerecord.housemanual.items.MoveRoomContent(ItemDetailUiState(loading = false, detail = detail, showMove = true, moveChoices = furnaceMoveChoices), {})
}

@ThemePreviews
@Composable
fun PanelEmptyPreview() = Frame {
    com.kurtz.homerecord.housemanual.panel.PanelScreen(panelState(samplePanel.copy(slotCount = 20, mainAmps = null, brand = null), emptyList()), {})
}

@ThemePreviews
@Composable
fun PanelDinPreview() = Frame {
    val din = samplePanel.copy(slotCount = 12, layout = com.kurtz.homerecord.core.model.PanelLayout.DIN, mainAmps = 100, brand = "Hager")
    val breakers = listOf(
        sampleBreaker("d1", listOf(1, 2), 30, "Dryer"),
        sampleBreaker("d2", listOf(3), 16, "Kitchen sockets", com.kurtz.homerecord.core.model.BreakerType.GFCI),
        sampleBreaker("d3", listOf(4), 10, "Lights"),
    )
    com.kurtz.homerecord.housemanual.panel.PanelScreen(panelState(din, breakers), {})
}

@ThemePreviews
@Composable
fun PanelClashPreview() = Frame {
    // A second phone put a two-pole breaker over slots 2 and 4 while this one was offline: it lists under the grid.
    com.kurtz.homerecord.housemanual.panel.PanelScreen(panelState(samplePanel, sampleBreakers + sampleBreaker("b10", listOf(2, 4), 30, "Air conditioner", at = 9)), {})
}

@ThemePreviews
@Composable
fun PanelLoadingPreview() = Frame { com.kurtz.homerecord.housemanual.panel.PanelScreen(com.kurtz.homerecord.housemanual.panel.PanelUiState(), {}) }

@ThemePreviews
@Composable
fun PanelMissingPreview() = Frame { com.kurtz.homerecord.housemanual.panel.PanelScreen(com.kurtz.homerecord.housemanual.panel.PanelUiState(loading = false, missing = true), {}) }

// HM-Breaker (AC-C40): the editor over the panel, as the QA catalog and screenshots draw it (without the dialog window).
private val sampleFeedRooms = listOf("Bathroom", "Dining room", "Kitchen", "Living room", "Mudroom").mapIndexed { i, n -> com.kurtz.homerecord.housemodel.panel.FeedOption("r$i", n) }
private val sampleFeedItems = listOf("Dishwasher", "Garbage disposal", "Microwave", "Range", "Refrigerator").mapIndexed { i, n -> com.kurtz.homerecord.housemodel.panel.FeedOption("i$i", n) }

@Composable
private fun BreakerFrame(editor: com.kurtz.homerecord.housemanual.panel.BreakerEditorState) =
    FormScreenScaffold(com.kurtz.homerecord.housemanual.panel.breakerEditorTitle(editor), "hm_breaker_save", editor.canSave, {}, {}) {
        com.kurtz.homerecord.housemanual.panel.BreakerEditorContent(editor, {})
    }

@ThemePreviews
@Composable
fun PanelBreakerEditPreview() = Frame {
    BreakerFrame(
        com.kurtz.homerecord.housemanual.panel.BreakerEditorState(
            draft = com.kurtz.homerecord.housemodel.panel.BreakerDraft(
                id = "b2", startSlot = 2, amps = "20", type = com.kurtz.homerecord.core.model.BreakerType.GFCI, label = "Kitchen counter",
                roomIds = setOf("r2"), itemIds = setOf("i1", "i2"),
            ),
            dirty = true, placement = com.kurtz.homerecord.core.model.SlotPlacement.Ok(listOf(2)), rooms = sampleFeedRooms, items = sampleFeedItems,
        ),
    )
}

@ThemePreviews
@Composable
fun PanelBreakerNewPreview() = Frame {
    // Two poles picked on slot 10, but slot 12 below it holds the dishwasher: the form says so and Save stays off.
    BreakerFrame(
        com.kurtz.homerecord.housemanual.panel.BreakerEditorState(
            draft = com.kurtz.homerecord.housemodel.panel.BreakerDraft(startSlot = 10, poles = 2, amps = "30", label = "Dryer"),
            dirty = true, placement = com.kurtz.homerecord.core.model.SlotPlacement.Taken(listOf(12)), rooms = emptyList(), items = sampleFeedItems,
        ),
    )
}

@ThemePreviews
@Composable
fun PanelBreakerTandemPreview() = Frame {
    BreakerFrame(
        com.kurtz.homerecord.housemanual.panel.BreakerEditorState(
            draft = com.kurtz.homerecord.housemodel.panel.BreakerDraft(startSlot = 4, tandem = true, amps = "15", label = "Garage lights"),
            placement = com.kurtz.homerecord.core.model.SlotPlacement.Ok(listOf(4)), rooms = sampleFeedRooms, items = sampleFeedItems,
        ),
    )
}

@ThemePreviews
@Composable
fun PanelBreakerDeletePreview() = Frame {
    val editor = com.kurtz.homerecord.housemanual.panel.BreakerEditorState(
        draft = com.kurtz.homerecord.housemodel.panel.BreakerDraft(id = "b1", startSlot = 1, poles = 2, amps = "40", label = "Range", itemIds = setOf("i3")),
        placement = com.kurtz.homerecord.core.model.SlotPlacement.Ok(listOf(1, 3)), rooms = sampleFeedRooms, items = sampleFeedItems, confirmDelete = true,
    )
    BreakerFrame(editor)
    com.kurtz.homerecord.housemanual.panel.BreakerDeleteDialog(editor, {})
}

@ThemePreviews
@Composable
fun PanelFactsPreview() = Frame {
    FormScreenScaffold(stringResource(R.string.panel_facts_title), "hm_panel_facts_save", true, {}, {}) {
        com.kurtz.homerecord.housemanual.panel.PanelFactsContent(
            com.kurtz.homerecord.housemanual.panel.PanelFactsState(com.kurtz.homerecord.housemodel.panel.PanelDraft("40", mainAmps = "200", brand = "Square D QO"), dirty = true),
            {},
        )
    }
}

@ThemePreviews
@Composable
fun PanelFactsLockedPreview() = Frame {
    // Breakers run to slot 23, so 20 slots is refused and the layout stays two columns.
    FormScreenScaffold(stringResource(R.string.panel_facts_title), "hm_panel_facts_save", true, {}, {}) {
        com.kurtz.homerecord.housemanual.panel.PanelFactsContent(
            com.kurtz.homerecord.housemanual.panel.PanelFactsState(
                com.kurtz.homerecord.housemodel.panel.PanelDraft("20", mainAmps = "200", brand = "Square D QO"),
                dirty = true, showErrors = true, highestUsed = 23, layoutLocked = true,
            ),
            {},
        )
    }
}

@ThemePreviews
@Composable
fun PanelPhotoSheetPreview() = Frame {
    com.kurtz.homerecord.coreui.components.OptionsSheetContent(
        stringResource(R.string.panel_photo_sheet_title),
        listOf(
            com.kurtz.homerecord.coreui.components.SheetOption("take", stringResource(R.string.panel_photo_take), androidx.compose.material.icons.Icons.Rounded.PhotoCamera),
            com.kurtz.homerecord.coreui.components.SheetOption("choose", stringResource(R.string.panel_photo_choose), androidx.compose.material.icons.Icons.Rounded.PhotoLibrary),
        ),
        {},
    )
}

// More → Breaker panels (AC-C40): a main panel in the basement and a garage sub-panel.
private val samplePanelRows = listOf(
    com.kurtz.homerecord.housemodel.items.ItemRow("i-main", "Main panel", "Square D", "QO140M200", ItemCategory.ELECTRICAL, "breaker_panel", "r-u", "Utility room", WarrantyStatus.Unknown, createdAt = 1),
    com.kurtz.homerecord.housemodel.items.ItemRow("i-sub", "Garage sub-panel", "Siemens", "W0816ML1125CU", ItemCategory.ELECTRICAL, "sub_panel", "r-g", "Garage", WarrantyStatus.Unknown, createdAt = 2),
)

@ThemePreviews
@Composable
fun PanelsListPreview() = Frame {
    com.kurtz.homerecord.housemanual.panel.PanelsScreen(com.kurtz.homerecord.housemanual.panel.PanelsUiState(loading = false, panels = samplePanelRows), {})
}

@ThemePreviews
@Composable
fun PanelsEmptyPreview() = Frame { com.kurtz.homerecord.housemanual.panel.PanelsScreen(com.kurtz.homerecord.housemanual.panel.PanelsUiState(loading = false), {}) }

@ThemePreviews
@Composable
fun PanelsLoadingPreview() = Frame { com.kurtz.homerecord.housemanual.panel.PanelsScreen(com.kurtz.homerecord.housemanual.panel.PanelsUiState(), {}) }

private val sampleShutoffSections = com.kurtz.homerecord.core.model.ShutoffGroups.sections(
    listOf(
        com.kurtz.homerecord.core.model.Shutoff(
            "s1", "Water main shutoff", "water_main", "Utility room",
            "Turn the red handle on the meter side a quarter turn so it crosses the pipe. The whole house loses water.", "valve.jpg", 1L,
        ),
        com.kurtz.homerecord.core.model.Shutoff("s2", "Kitchen sink cold", "fixture_shutoff", "Kitchen", "Under the sink, left valve. Turn clockwise until it stops.", null, 2L),
        com.kurtz.homerecord.core.model.Shutoff(
            "s3", "Gas main shutoff", "gas_main", "Exterior",
            "Use the wrench on the meter bracket. Turn the tab a quarter turn until it crosses the pipe. Call the gas company to turn it back on.", "gas.jpg", 3L,
        ),
        com.kurtz.homerecord.core.model.Shutoff("s4", "Main breaker", "electrical_main", "Utility room", "", null, 4L),
        com.kurtz.homerecord.core.model.Shutoff("s5", "Furnace switch", "hvac_disconnect", "Utility room", "Red switch plate on the stairway wall. Flip down.", null, 5L),
    ),
)

/** A quarter-turn ball valve on a copper line against a basement wall, for the Emergency previews. */
private val sampleValvePhoto: androidx.compose.ui.graphics.painter.Painter by lazy {
    val bmp = android.graphics.Bitmap.createBitmap(800, 600, android.graphics.Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    c.drawColor(android.graphics.Color.rgb(196, 190, 178))
    paint.color = android.graphics.Color.rgb(184, 115, 51)
    c.drawRect(0f, 270f, 800f, 330f, paint)
    paint.color = android.graphics.Color.rgb(170, 160, 120)
    c.drawRoundRect(330f, 240f, 470f, 360f, 18f, 18f, paint)
    paint.color = android.graphics.Color.rgb(200, 30, 30)
    c.drawRoundRect(380f, 120f, 420f, 270f, 14f, 14f, paint)
    c.drawRoundRect(380f, 120f, 640f, 160f, 14f, 14f, paint)
    androidx.compose.ui.graphics.painter.BitmapPainter(bmp.asImageBitmap())
}

@ThemePreviews
@Composable
fun EmergencyListPreview() = Frame {
    com.kurtz.homerecord.housemanual.emergency.EmergencyScreen(
        com.kurtz.homerecord.housemanual.emergency.EmergencyUiState(loading = false, sections = sampleShutoffSections), {}, previewPhoto = sampleValvePhoto,
    )
}

@ThemePreviews
@Composable
fun EmergencyEmptyPreview() = Frame {
    com.kurtz.homerecord.housemanual.emergency.EmergencyScreen(com.kurtz.homerecord.housemanual.emergency.EmergencyUiState(loading = false), {})
}

@ThemePreviews
@Composable
fun EmergencyLoadingPreview() = Frame { com.kurtz.homerecord.housemanual.emergency.EmergencyScreen(com.kurtz.homerecord.housemanual.emergency.EmergencyUiState(), {}) }

private fun mappingState(phase: com.kurtz.homerecord.core.mapping.MappingPhase, plugged: Boolean = true, confirmed: Int = 0) =
    com.kurtz.homerecord.housemanual.mapping.MappingUiState(
        loading = false, panelName = "Main panel", phase = phase, plugged = plugged,
        breakers = sampleBreakers.sortedBy { it.number }.map { com.kurtz.homerecord.housemanual.mapping.MappingBreaker(it.id, it.number, it.label, it.amps) },
        rooms = sampleFeedRooms, items = sampleFeedItems, confirmed = confirmed,
    )

@ThemePreviews
@Composable
fun MappingIdlePreview() = Frame { com.kurtz.homerecord.housemanual.mapping.MappingScreen(mappingState(com.kurtz.homerecord.core.mapping.MappingPhase.IDLE, plugged = false), {}) }

@ThemePreviews
@Composable
fun MappingArmedPreview() = Frame { com.kurtz.homerecord.housemanual.mapping.MappingScreen(mappingState(com.kurtz.homerecord.core.mapping.MappingPhase.ARMED, confirmed = 2), {}) }

@ThemePreviews
@Composable
fun MappingAlarmPreview() = Frame { com.kurtz.homerecord.housemanual.mapping.MappingScreen(mappingState(com.kurtz.homerecord.core.mapping.MappingPhase.ALARM), {}) }

@ThemePreviews
@Composable
fun MappingPickPreview() = Frame {
    com.kurtz.homerecord.housemanual.mapping.MappingScreen(mappingState(com.kurtz.homerecord.core.mapping.MappingPhase.PICKING).copy(breakerId = "b3", roomId = "r2"), {})
}

@ThemePreviews
@Composable
fun MappingStoppedPreview() = Frame { com.kurtz.homerecord.housemanual.mapping.MappingScreen(mappingState(com.kurtz.homerecord.core.mapping.MappingPhase.STOPPED, confirmed = 3), {}) }

@ThemePreviews
@Composable
fun PanelLegendFailedPreview() = Frame { com.kurtz.homerecord.housemanual.panel.PanelScreen(panelState(samplePanel, sampleBreakers).copy(legendFailed = true), {}) }

private val reminderToday: java.time.LocalDate = java.time.LocalDate.of(2026, 11, 6)

private fun reminderRow(
    id: String, title: String, bucket: com.kurtz.homerecord.housemodel.reminders.ReminderBucket, due: String,
    item: String? = null, interval: Int? = null, done: String? = null, snoozed: String? = null, seasonal: Boolean = false,
) = com.kurtz.homerecord.housemodel.reminders.ReminderRow(
    id = id, title = title, itemId = item?.let { "item-$id" }, itemName = item, bucket = bucket, dueOn = java.time.LocalDate.parse(due),
    intervalMonths = interval, lastDoneOn = done?.let(java.time.LocalDate::parse), snoozedUntil = snoozed?.let(java.time.LocalDate::parse), seasonal = seasonal,
)

private val sampleReminderRows = run {
    listOf(
        reminderRow("r1", "Replace furnace filter", com.kurtz.homerecord.housemodel.reminders.ReminderBucket.DUE, "2026-10-28", item = "Basement furnace", interval = 3),
        reminderRow("r2", "Test sump pump", com.kurtz.homerecord.housemodel.reminders.ReminderBucket.DUE, "2026-11-06", item = "Sump pump", interval = 3),
        reminderRow("r3", "Check heating before winter", com.kurtz.homerecord.housemodel.reminders.ReminderBucket.UPCOMING, "2026-11-20", interval = 12, snoozed = "2026-11-20", seasonal = true),
        reminderRow("r4", "Warranty ends soon: Dishwasher", com.kurtz.homerecord.housemodel.reminders.ReminderBucket.UPCOMING, "2026-12-14", item = "Dishwasher"),
        reminderRow("r5", "Flush water heater", com.kurtz.homerecord.housemodel.reminders.ReminderBucket.UPCOMING, "2027-03-01", item = "Water heater", interval = 12),
        reminderRow("r6", "Clean dryer vent", com.kurtz.homerecord.housemodel.reminders.ReminderBucket.DONE, "2027-11-02", item = "Dryer", interval = 12, done = "2026-11-02"),
    )
}

@ThemePreviews
@Composable
fun RemindersListPreview() = Frame {
    com.kurtz.homerecord.housemanual.reminders.RemindersScreen(
        com.kurtz.homerecord.housemanual.reminders.RemindersUiState(
            loading = false, rows = sampleReminderRows, zone = com.kurtz.homerecord.housemodel.reminders.ClimateZone.COLD, today = reminderToday,
        ), {},
    )
}

@ThemePreviews
@Composable
fun RemindersEmptyPreview() = Frame {
    com.kurtz.homerecord.housemanual.reminders.RemindersScreen(com.kurtz.homerecord.housemanual.reminders.RemindersUiState(loading = false, today = reminderToday), {})
}

@ThemePreviews
@Composable
fun RemindersLoadingPreview() = Frame { com.kurtz.homerecord.housemanual.reminders.RemindersScreen(com.kurtz.homerecord.housemanual.reminders.RemindersUiState(), {}) }

@ThemePreviews
@Composable
fun ReminderFormPreview() = Frame {
    com.kurtz.homerecord.housemanual.reminders.ReminderFormContent(
        com.kurtz.homerecord.housemodel.reminders.ReminderDraft(title = "Reseal the deck", dueOn = java.time.LocalDate.of(2027, 5, 1), intervalMonths = 12), false, {},
    )
}

@ThemePreviews
@Composable
fun ReminderFormErrorsPreview() = Frame {
    com.kurtz.homerecord.housemanual.reminders.ReminderFormContent(com.kurtz.homerecord.housemodel.reminders.ReminderDraft(), true, {})
}

private val furnaceReminders = listOf(
    reminderRow("fr1", "Replace furnace filter", com.kurtz.homerecord.housemodel.reminders.ReminderBucket.DUE, "2026-10-28", item = "Basement furnace", interval = 3),
    reminderRow("fr2", "HVAC service", com.kurtz.homerecord.housemodel.reminders.ReminderBucket.UPCOMING, "2027-09-20", item = "Basement furnace", interval = 12),
)

/** The furnace page with its reminders; the section sits below Consumables (SPEC HM-Item, AC-C49). */
@ThemePreviews
@Composable
fun ItemDetailRemindersPagePreview() = Frame {
    ItemDetailScreen(ItemDetailUiState(loading = false, detail = furnaceDetail(false), reminders = furnaceReminders, today = reminderToday), {})
}

/** The item page's Reminders section on its own: an overdue filter change and next year's service, each with Done and Snooze. */
@ThemePreviews
@Composable
fun ItemDetailRemindersPreview() = Frame {
    androidx.compose.foundation.layout.Column(Modifier.padding(vertical = 16.dp)) {
        com.kurtz.homerecord.housemanual.items.ItemRemindersSection(ItemDetailUiState(loading = false, detail = furnaceDetail(false), reminders = furnaceReminders, today = reminderToday)) {}
    }
}

/** The Reminders section for an item with nothing to do yet: one line and Add reminder (AC-C49). */
@ThemePreviews
@Composable
fun ItemDetailRemindersEmptyPreview() = Frame {
    androidx.compose.foundation.layout.Column(Modifier.padding(vertical = 16.dp)) {
        com.kurtz.homerecord.housemanual.items.ItemRemindersSection(ItemDetailUiState(loading = false, detail = furnaceDetail(false), today = reminderToday)) {}
    }
}

private val sampleReportState = com.kurtz.homerecord.housemanual.report.ReportUiState(
    loading = false, homeName = "Maple Street house",
    contents = com.kurtz.homerecord.housemanual.report.ReportContents(floors = 3, rooms = 11, items = 38, shutoffs = 2, panels = 1),
)

@ThemePreviews
@Composable
fun ReportReadyPreview() = Frame { com.kurtz.homerecord.housemanual.report.ReportScreen(sampleReportState, {}) }

@ThemePreviews
@Composable
fun ReportFailedPreview() = Frame {
    com.kurtz.homerecord.housemanual.report.ReportScreen(
        sampleReportState.copy(contents = com.kurtz.homerecord.housemanual.report.ReportContents(floors = 1, rooms = 4, items = 1, shutoffs = 0, panels = 0), failed = true), {},
    )
}

@ThemePreviews
@Composable
fun ReportLoadingPreview() = Frame { com.kurtz.homerecord.housemanual.report.ReportScreen(com.kurtz.homerecord.housemanual.report.ReportUiState(), {}) }

private val sampleBackup = com.kurtz.homerecord.housemanual.backup.BackupUiState(homeName = "Maple Street house")

@ThemePreviews
@Composable
fun BackupEmptyPreview() = Frame { com.kurtz.homerecord.housemanual.backup.BackupScreen(sampleBackup, {}) }

@ThemePreviews
@Composable
fun BackupMismatchPreview() = Frame {
    com.kurtz.homerecord.housemanual.backup.BackupScreen(
        sampleBackup.copy(passphrase = "maple-street-1987", confirm = "maple-stret-1987", problem = com.kurtz.homerecord.housemanual.backup.PassphraseProblem.Mismatch), {},
    )
}

@ThemePreviews
@Composable
fun BackupMadePreview() = Frame {
    com.kurtz.homerecord.housemanual.backup.BackupScreen(
        sampleBackup.copy(passphrase = "maple-street-1987", confirm = "maple-street-1987", made = "Maple-Street-house-backup-2026-11-06.hrbackup"), {},
    )
}

@ThemePreviews
@Composable
fun BackupFailedPreview() = Frame {
    com.kurtz.homerecord.housemanual.backup.BackupScreen(sampleBackup.copy(passphrase = "maple-street-1987", confirm = "maple-street-1987", failed = true), {})
}

@ThemePreviews
@Composable
fun RestorePassphrasePreview() = Frame { com.kurtz.homerecord.housemanual.importing.RestoreScreen(com.kurtz.homerecord.housemanual.importing.RestoreUiState(), {}) }

@ThemePreviews
@Composable
fun RestoreWrongPassphrasePreview() = Frame {
    com.kurtz.homerecord.housemanual.importing.RestoreScreen(
        com.kurtz.homerecord.housemanual.importing.RestoreUiState(passphrase = "maple-street-1978", error = com.kurtz.homerecord.housemanual.importing.ImportError.WrongPassphrase), {},
    )
}

@ThemePreviews
@Composable
fun RestorePreviewPreview() = Frame { com.kurtz.homerecord.housemanual.importing.ImportScreen(sampleRestore, {}) }

private val sampleRestore = com.kurtz.homerecord.housemanual.importing.ImportUiState(
    loading = false,
    homeName = "Maple Street house",
    restore = true,
    counts = listOf(
        com.kurtz.homerecord.housemanual.importing.ImportKind.Floors to 3,
        com.kurtz.homerecord.housemanual.importing.ImportKind.Rooms to 12,
        com.kurtz.homerecord.housemanual.importing.ImportKind.WallPhotos to 10,
        com.kurtz.homerecord.housemanual.importing.ImportKind.Overlays to 15,
        com.kurtz.homerecord.housemanual.importing.ImportKind.Items to 25,
        com.kurtz.homerecord.housemanual.importing.ImportKind.Documents to 4,
    ).map { (kind, n) -> com.kurtz.homerecord.housemanual.importing.ImportCount(kind, n) },
)

@ThemePreviews
@Composable
fun RestoreDonePreview() = Frame { com.kurtz.homerecord.housemanual.importing.ImportScreen(sampleRestore.copy(done = true), {}) }
