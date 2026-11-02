package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.click
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayDepth
import com.kurtz.homerecord.housemodel.overlays.OverlayItemChoice
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.ui.OverlayForm
import com.kurtz.homerecord.housemodel.ui.OverlayFormSheet
import com.kurtz.homerecord.housemodel.ui.ItemPickList
import com.kurtz.homerecord.housemodel.ui.RoomEvent
import com.kurtz.homerecord.housemodel.ui.RoomScreen
import com.kurtz.homerecord.housemodel.ui.RoomUiState
import com.kurtz.homerecord.housemodel.ui.WallOverlaysEvent
import com.kurtz.homerecord.housemodel.ui.WallOverlaysScreen
import com.kurtz.homerecord.housemodel.ui.WallOverlaysUiState
import com.kurtz.homerecord.housemodel.ui.WallPicture
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallSide
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C25, AC-C26, AC-C28: HM-Wall draws overlays by tapping the wall, picks one with a tap, drags its points, edits and
// deletes it; the form asks for a kind; the room page lists the room's overlays and opens their wall.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WallOverlaysScreenTest {
    @get:Rule val rule = createComposeRule()

    private val wall = RoomWall("w1", WallSide.NORTH, null, 168, 96)
    private val pipe = Overlay(
        "o1", "h1", "k", "w1", OverlayKind.WATER_HOT, "Sink supply", null, OverlayDepth.BEHIND_DRYWALL,
        listOf(OverlayPoint(0.2f, 0.9f), OverlayPoint(0.2f, 0.5f), OverlayPoint(0.6f, 0.5f)), WallStage.ROUGH_IN, null, null, 0L,
    )
    private val base = WallOverlaysUiState(
        loading = false, wall = wall, roomName = "Kitchen", stage = WallStage.ROUGH_IN,
        picture = WallPicture("/missing.jpg", null, 168f / 96f), overlays = listOf(pipe),
    )

    private fun show(state: WallOverlaysUiState, events: MutableList<WallOverlaysEvent>) = rule.setContent {
        CompositionLocalProvider(LocalTagPrefix provides "hm") {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { WallOverlaysScreen(state, { events += it }, ColorPainter(Color.Gray)) }
        }
    }

    @Test
    @AcceptanceCriterion("AC-C25")
    fun addOverlayStartsDrawingAndTapsOnTheWallAddPointsInWallSpace() {
        val events = mutableListOf<WallOverlaysEvent>()
        show(base, events)
        rule.onNodeWithText("Sink supply").assertExists()
        rule.onNodeWithTag("hm_wall_add_overlay").performClick()
        assertThat(events).contains(WallOverlaysEvent.StartDrawing)
    }

    @Test
    @AcceptanceCriterion("AC-C25")
    fun whileDrawingATapAddsThePointUnderTheFinger() {
        val events = mutableListOf<WallOverlaysEvent>()
        show(base.copy(drawing = emptyList()), events)
        rule.onNodeWithTag("hm_wall_draw_next").assertExists()
        rule.onNodeWithTag("hm_wall_picture").performTouchInput { click(Offset(width * 0.25f, height * 0.75f)) }
        val point = events.filterIsInstance<WallOverlaysEvent.AddPoint>().single().point
        assertThat(point.x).isWithin(0.02f).of(0.25f)
        assertThat(point.y).isWithin(0.02f).of(0.75f)
    }

    @Test
    @AcceptanceCriterion("AC-C28")
    fun aTapOnARunPicksItAndATapOnBareWallClearsThePick() {
        val events = mutableListOf<WallOverlaysEvent>()
        show(base, events)
        rule.onNodeWithTag("hm_wall_picture").performTouchInput { click(Offset(width * 0.4f, height * 0.5f)) }
        rule.onNodeWithTag("hm_wall_picture").performTouchInput { click(Offset(width * 0.9f, height * 0.1f)) }
        assertThat(events.filterIsInstance<WallOverlaysEvent.Select>()).containsExactly(WallOverlaysEvent.Select("o1"), WallOverlaysEvent.Select(null)).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C28")
    fun draggingAPointOfThePickedRunMovesItAndEditAndDeleteAreThere() {
        val events = mutableListOf<WallOverlaysEvent>()
        show(base.copy(selectedId = "o1"), events)
        rule.onNodeWithTag("hm_wall_picture").performTouchInput {
            down(Offset(width * 0.6f, height * 0.5f))
            moveTo(Offset(width * 0.7f, height * 0.4f))
            moveTo(Offset(width * 0.8f, height * 0.3f))
            up()
        }
        val move = events.filterIsInstance<WallOverlaysEvent.MovePoint>().single()
        assertThat(move.id).isEqualTo("o1")
        assertThat(move.index).isEqualTo(2)
        assertThat(move.to.x).isWithin(0.02f).of(0.8f)
        assertThat(move.to.y).isWithin(0.02f).of(0.3f)
        rule.onNodeWithTag("hm_wall_overlay_edit").performClick()
        rule.onNodeWithTag("hm_wall_overlay_delete").performClick()
        assertThat(events).containsAtLeast(WallOverlaysEvent.EditSelected, WallOverlaysEvent.AskDelete)
    }

    @Test
    @AcceptanceCriterion("AC-C28")
    fun deleteAsksFirst() {
        val events = mutableListOf<WallOverlaysEvent>()
        show(base.copy(selectedId = "o1", confirmDelete = true), events)
        rule.onNodeWithText("Delete Sink supply?").assertExists()
        rule.onNodeWithTag("hm_wall_overlay_delete_confirm").performClick()
        assertThat(events).contains(WallOverlaysEvent.ConfirmDelete)
    }

    @Test
    @AcceptanceCriterion("AC-C26")
    fun theFormAsksForAKindAndOffersEveryFieldAndTheKindSheet() {
        val events = mutableListOf<WallOverlaysEvent>()
        show(base.copy(form = OverlayForm(points = pipe.points, stage = WallStage.ROUGH_IN, triedSave = true)), events)
        rule.onNodeWithContentDescription("Pick what this is.", substring = true).assertExists()
        rule.onNodeWithTag("hm_overlay_form_label").assertExists()
        rule.onNodeWithTag("hm_overlay_form_depth").assertExists()
        rule.onNodeWithTag("hm_overlay_form_stage_rough_in").assertExists()
        rule.onNodeWithTag("hm_overlay_form_note").performScrollTo().assertExists()
        rule.onNodeWithTag("hm_overlay_form_kind").performScrollTo().performClick()
        rule.onNodeWithTag("hm_save").performClick()
        assertThat(events).containsAtLeast(WallOverlaysEvent.OpenSheet(OverlayFormSheet.KIND), WallOverlaysEvent.Save)
    }

    @Test
    @AcceptanceCriterion("AC-C25")
    fun theRoomPageListsItsOverlaysAndOpensTheirWall() {
        val events = mutableListOf<RoomEvent>()
        val state = RoomUiState(
            loading = false, room = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144), walls = listOf(wall),
            canOpenWalls = true, overlays = listOf(pipe),
        )
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { RoomScreen(state) { events += it } }
            }
        }
        rule.onNodeWithTag("hm_room_overlay_o1").performScrollTo().performClick()
        rule.onNodeWithTag("hm_room_wall_w1").performScrollTo().performClick()
        assertThat(events).containsExactly(RoomEvent.OpenWall("w1", "o1"), RoomEvent.OpenWall("w1")).inOrder()
    }

    private val heater = OverlayItemChoice("i1", "Water heater", null, "Basement")
    private val sink = OverlayItemChoice("i2", "Kitchen sink", "k", "Kitchen")

    @Test
    @AcceptanceCriterion("AC-C26")
    fun theFormShowsTheLinkedItemAndPhotoWithReplaceAndRemove() {
        val events = mutableListOf<WallOverlaysEvent>()
        val form = OverlayForm(points = pipe.points, kind = OverlayKind.DRAIN, stage = WallStage.ROUGH_IN, itemId = "i1", photoPath = "/missing.jpg")
        show(base.copy(form = form, items = listOf(heater, sink), canAddPhoto = true), events)
        rule.onNodeWithContentDescription("Water heater", substring = true).performScrollTo().performClick()
        rule.onNodeWithTag("hm_overlay_form_photo").performScrollTo().assertExists()
        rule.onNodeWithTag("hm_overlay_form_photo_replace").performClick()
        rule.onNodeWithTag("hm_overlay_form_photo_remove").performClick()
        assertThat(events).containsExactly(
            WallOverlaysEvent.OpenSheet(OverlayFormSheet.ITEM), WallOverlaysEvent.OpenSheet(OverlayFormSheet.PHOTO), WallOverlaysEvent.RemovePhoto,
        ).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C26")
    fun withoutAPhotoTheFormOffersAddPhotoAndNothingLinked() {
        val events = mutableListOf<WallOverlaysEvent>()
        show(base.copy(form = OverlayForm(points = pipe.points, kind = OverlayKind.DRAIN, stage = WallStage.ROUGH_IN), canAddPhoto = true), events)
        rule.onNodeWithContentDescription("Nothing linked", substring = true).performScrollTo().assertExists()
        rule.onNodeWithTag("hm_overlay_form_photo_add").performScrollTo().performClick()
        assertThat(events).containsExactly(WallOverlaysEvent.OpenSheet(OverlayFormSheet.PHOTO))
    }

    @Test
    @AcceptanceCriterion("AC-C26")
    fun theItemSheetSearchesPicksAndOffersClearWhenNothingMatches() {
        val events = mutableListOf<WallOverlaysEvent>()
        var query by androidx.compose.runtime.mutableStateOf("")
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                    val rows = com.kurtz.homerecord.housemodel.overlays.OverlayItemPicker.filter(listOf(heater, sink), query, "k")
                    ItemPickList(query, "i1", rows, houseHasItems = true) { e -> events += e; if (e is WallOverlaysEvent.SetItemQuery) query = e.query }
                }
            }
        }
        rule.onNodeWithTag("hm_overlay_item_i2").performClick()
        rule.onNodeWithTag("hm_overlay_item_none").performClick()
        rule.onNodeWithTag("hm_overlay_item_search").performTextInput("garage")
        rule.onNodeWithText("No items match \"garage\".").assertExists()
        rule.onNodeWithTag("hm_overlay_item_clear").performClick()
        rule.onNodeWithTag("hm_overlay_item_i1").assertExists()
        assertThat(events).containsAtLeast(WallOverlaysEvent.SetItem("i2"), WallOverlaysEvent.SetItem(null), WallOverlaysEvent.SetItemQuery("garage"), WallOverlaysEvent.SetItemQuery("")).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C26")
    fun aHouseWithNoItemsSaysSoInsteadOfASearch() {
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { ItemPickList("", null, emptyList(), houseHasItems = false) {} }
            }
        }
        rule.onNodeWithTag("hm_overlay_item_empty").assertExists()
        rule.onNodeWithTag("hm_overlay_item_search").assertDoesNotExist()
    }

    @Test
    @AcceptanceCriterion("AC-C26")
    fun thePickedOverlayShowsTheItemItServesAndItsPhoto() {
        val linked = pipe.copy(itemId = "i1", photoPath = "/missing.jpg")
        show(base.copy(overlays = listOf(linked), selectedId = "o1", items = listOf(heater)), mutableListOf())
        rule.onNodeWithText("Serves Water heater", substring = true).assertExists()
        rule.onNodeWithTag("hm_wall_overlay_photo", useUnmergedTree = true).assertExists()
    }
}
