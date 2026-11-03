package com.kurtz.homerecord.fieldlog.house

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.ui.BehindWallEvent
import com.kurtz.homerecord.housemodel.ui.BehindWallRoom
import com.kurtz.homerecord.housemodel.ui.BehindWallScreen
import com.kurtz.homerecord.housemodel.ui.BehindWallUiState
import com.kurtz.homerecord.housemodel.ui.HouseScreen
import com.kurtz.homerecord.housemodel.ui.HouseUiState
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallSide
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AC-F49: FL-House → Behind the wall → room → wall → camera sheet, with the stage the photo is saved at on each wall. */
@AcceptanceCriterion("AC-F49")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class BehindWallScreenTest {
    @get:Rule val rule = createComposeRule()

    private val kitchen = BehindWallRoom(
        "r1", "Kitchen", "Main floor",
        listOf(
            RoomWall("w1", WallSide.NORTH, null, 168, 96, mapOf(WallStage.ROUGH_IN to "/media/w1.jpg")),
            RoomWall("w2", WallSide.EAST, null, 144, 96),
        ),
    )
    private val rooms = listOf(kitchen, BehindWallRoom("r2", "Mudroom", "Main floor"))

    private fun show(state: BehindWallUiState): MutableList<BehindWallEvent> {
        val events = mutableListOf<BehindWallEvent>()
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { BehindWallScreen(state, { events += it }) } }
        return events
    }

    @Test fun theHouseTabOffersBehindTheWallOnceRoomsAreDrawn() {
        var opened = false
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                HouseScreen(
                    state = HouseUiState(
                        loading = false, homeName = "Hale kitchen", floors = listOf(FloorSummary("f1", "Main floor", 108, false, 1)),
                        floorId = "f1", rooms = listOf(PlanRoom.rectangle("r1", "Kitchen", "KITCHEN", 0, 0, 168, 144)),
                    ),
                    onEvent = {}, onOpenRoom = {}, onEditPlan = {}, onEditShape = {}, onOpenFloors = {}, onBehindWall = { opened = true },
                )
            }
        }
        rule.onNodeWithTag("fl_house_behind_wall").performClick()
        assertThat(opened).isTrue()
    }

    @Test fun roomsShowTheirWallProgressAndOpenTheirWalls() {
        val events = show(BehindWallUiState(loading = false, title = "Hale kitchen", rooms = rooms))
        rule.onNodeWithText("Rough-in 1 of 2 walls · Finished 0 of 2").assertExists()
        rule.onNodeWithText("Not on the plan yet").assertExists()
        rule.onNodeWithTag("fl_behind_room_r1").performClick()
        assertThat(events).containsExactly(BehindWallEvent.OpenRoom("r1"))
    }

    @Test fun eachWallSaysWhichPhotoComesNextAndOpensTheCameraSheet() {
        val events = show(BehindWallUiState(loading = false, rooms = rooms, roomId = "r1"))
        rule.onNodeWithText("Kitchen").assertExists()
        rule.onNode(hasTestTag("fl_behind_wall_next_w1"), true).assertExists()
        rule.onNodeWithText("Next photo: Finished, after drywall").assertExists()
        rule.onNodeWithText("Next photo: Rough-in").assertExists()
        rule.onNodeWithTag("fl_behind_wall_w2").performClick()
        rule.onNodeWithTag("fl_behind_walls").performScrollToNode(hasTestTag("fl_behind_open_room"))
        rule.onNodeWithTag("fl_behind_open_room").performClick()
        assertThat(events).containsExactly(BehindWallEvent.PickWall("w2"), BehindWallEvent.OpenRoomPage).inOrder()
    }

    @Test fun theSheetNamesTheWallAndTheStage() {
        val events = show(BehindWallUiState(loading = false, rooms = rooms, roomId = "r1", photoSheetFor = "w1"))
        rule.onNodeWithText("North wall · Finished photo").assertExists()
        rule.onNodeWithTag("fl_sheet_wall_take").performClick()
        assertThat(events).contains(BehindWallEvent.TakePhoto)
    }

    @Test fun noRoomsOffersEditPlan() {
        val events = show(BehindWallUiState(loading = false, title = "Hale kitchen"))
        rule.onNodeWithText("No rooms on the plan yet").assertExists()
        rule.onNodeWithTag("fl_behind_edit_plan").performClick()
        assertThat(events).containsExactly(BehindWallEvent.EditPlan)
    }
}
