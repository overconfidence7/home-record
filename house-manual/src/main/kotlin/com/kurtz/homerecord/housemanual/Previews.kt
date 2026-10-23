package com.kurtz.homerecord.housemanual

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
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
import com.kurtz.homerecord.housemodel.ui.RoomScreen
import com.kurtz.homerecord.housemodel.ui.ItemEditScreen
import com.kurtz.homerecord.housemodel.ui.ItemEditUiState
import com.kurtz.homerecord.housemodel.RoomChoice
import com.kurtz.homerecord.housemodel.items.ItemDraft
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.housemanual.items.ItemDetailScreen
import com.kurtz.homerecord.housemanual.items.ItemDetailUiState
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

private val houseRooms = planRoomsWithOpenings + PlanRoom(
    "r6", "Mudroom", "OTHER", listOf(Pt(240, 192), Pt(408, 192), Pt(408, 264), Pt(312, 264), Pt(312, 336), Pt(240, 336)),
)

private fun houseState(vararg changes: HouseUiState.() -> HouseUiState): HouseUiState =
    changes.fold(
        HouseUiState(loading = false, homeName = "Maple St", summary = "Colonial · Basement", floors = floors, floorId = "f2", rooms = houseRooms),
    ) { acc, f -> acc.f() }

@Composable
private fun houseScreen(state: HouseUiState) = HouseScreen(state, {}, {}, {}, {}, {})

@ThemePreviews
@Composable
fun HouseBlueprintPreview() = Frame { houseScreen(houseState()) }

@Preview(name = "Landscape", device = "spec:width=891dp,height=411dp")
@Composable
fun HouseBlueprintLandscapePreview() = Frame { houseScreen(houseState()) }

@ThemePreviews
@Composable
fun HouseLayersPreview() = Frame {
    houseScreen(houseState({ copy(layers = LayerSet(setOf(BlueprintLayer.PLUMBING, BlueprintLayer.ELECTRICAL))) }))
}

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
fun RoomPagePreview() = Frame { RoomScreen(RoomUiState(loading = false, room = houseRooms[0], floorName = "Main floor", homeName = "Maple St"), {}) }

@ThemePreviews
@Composable
fun RoomMissingPreview() = Frame { RoomScreen(RoomUiState(loading = false), {}) }

private val itemRooms = listOf(RoomChoice("r1", "Kitchen", "Main"), RoomChoice("r2", "Utility room", "Basement"), RoomChoice("r3", "Primary bedroom", "Upper"))

private val furnaceDraft = ItemDraft(
    subtypeId = "furnace", roomId = "r2", name = "Basement furnace", make = "Carrier", model = "59TP6A060S17",
    serial = "3416A12345", manufacturedOn = "2016-04-18", purchasedOn = "2016-09-02", price = "4850.00", vendor = "Harlan Heating",
)

@ThemePreviews
@Composable
fun ItemsEmptyPreview() = Frame { ItemsScreen(ItemsUiState(loading = false), {}) }

private fun itemRow(id: String, name: String, make: String?, model: String?, cat: String, sub: String, room: String, w: WarrantyStatus) =
    ItemRow(id, name, make, model, cat, ItemListBuilder.categoryOrder(cat), sub, "r-$room", room, w)

private val sampleItems = listOf(
    itemRow("i1", "Basement furnace", "Carrier", "59TP6A060S17", "Heating and cooling", "furnace", "Basement", WarrantyStatus.Expired("2026-04-18")),
    itemRow("i2", "Water heater", "Rheem", "XE50T10HD50U1", "Water", "water_heater", "Basement", WarrantyStatus.Expiring("2026-12-02", 40)),
    itemRow("i3", "Dishwasher", "Bosch", "SHPM65Z55N", "Appliances", "dishwasher", "Kitchen", WarrantyStatus.Active("2028-03-14")),
    itemRow("i4", "Refrigerator", "Whirlpool", null, "Appliances", "refrigerator", "Kitchen", WarrantyStatus.Unknown),
)

private fun itemsState(grouping: ItemGrouping = ItemGrouping.CATEGORY, filter: ItemFilter = ItemFilter()) = ItemsUiState(
    loading = false, totalCount = sampleItems.size, grouping = grouping, filter = filter,
    groups = ItemListBuilder.build(sampleItems, grouping, filter),
    categoryOptions = sampleItems.map { it.categoryLabel }.distinct(),
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

@ThemePreviews
@Composable
fun ItemEditTypeSheetPreview() = Frame { com.kurtz.homerecord.housemodel.ui.SubtypeList("furnace", {}) }

@ThemePreviews
@Composable
fun ItemEditPlateSheetPreview() = Frame {
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
    item = furnaceEntity, categoryLabel = "Heating and cooling", subtypeLabel = "Furnace", roomName = "Utility room",
    warranty = furnaceWarranty.takeIf { withWarranty },
    status = if (withWarranty) WarrantyStatus.Expired("2026-09-20") else WarrantyStatus.Unknown, photos = emptyList(),
)

@ThemePreviews
@Composable
fun ItemDetailPreview() = Frame { ItemDetailScreen(ItemDetailUiState(loading = false, detail = furnaceDetail()), {}) }

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
