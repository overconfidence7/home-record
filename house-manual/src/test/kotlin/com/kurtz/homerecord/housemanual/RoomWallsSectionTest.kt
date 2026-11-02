package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.ui.RoomEvent
import com.kurtz.homerecord.housemodel.ui.RoomScreen
import com.kurtz.homerecord.housemodel.ui.RoomUiState
import com.kurtz.homerecord.housemodel.walls.RoomWall
import com.kurtz.homerecord.housemodel.walls.WallSide
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C17: HM-Room's Walls section names each wall by side and length, says which stages have photos, and switches stage.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RoomWallsSectionTest {
    @get:Rule val rule = createComposeRule()

    private val walls = listOf(
        RoomWall("w1", WallSide.NORTH, 1, 168, 96, mapOf(WallStage.ROUGH_IN to "/missing/r.jpg", WallStage.FINISHED to "/missing/f.jpg")),
        RoomWall("w2", WallSide.NORTH, 2, 60, 96),
        RoomWall("w3", WallSide.EAST, null, 144, 96),
    )

    private fun show(state: RoomUiState, events: MutableList<RoomEvent>) = rule.setContent {
        CompositionLocalProvider(LocalTagPrefix provides "hm") {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { RoomScreen(state) { events += it } }
        }
    }

    private val base = RoomUiState(loading = false, room = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144), walls = walls)

    @Test
    @AcceptanceCriterion("AC-C17")
    fun wallsShowSideLengthAndPhotographedStages() {
        val events = mutableListOf<RoomEvent>()
        show(base, events)
        rule.onNodeWithTag("hm_room_wall_w1").performScrollTo()
        rule.onNodeWithText("North wall 1").assertExists()
        rule.onNodeWithText("14' long · Photos: Rough-in, Finished").assertExists()
        rule.onNodeWithText("North wall 2").assertExists()
        rule.onNodeWithText("5' long · No photos yet").assertExists()
        rule.onNodeWithText("East wall").assertExists()
        rule.onNodeWithTag("hm_room_wall_stage_finished").assertIsSelected()
        // No capture yet in this state: no Photograph buttons.
        rule.onNodeWithTag("hm_room_wall_photograph_w1").assertDoesNotExist()

        rule.onNodeWithTag("hm_room_wall_stage_rough_in").performClick()
        assertThat(events).containsExactly(RoomEvent.SelectStage(WallStage.ROUGH_IN))
    }

    @Test
    @AcceptanceCriterion("AC-C17")
    fun photographSaysRetakeWhereTheStageHasAPhoto() {
        val events = mutableListOf<RoomEvent>()
        show(base.copy(canPhotograph = true), events)
        rule.onNodeWithTag("hm_room_wall_photograph_w2").performScrollTo().performClick()
        assertThat(events).containsExactly(RoomEvent.PhotographWall("w2"))
        rule.onNodeWithTag("hm_room_wall_photograph_w1").performScrollTo()
        rule.onNodeWithText("Retake").assertExists()
    }

    @Test fun aRoomWithNoWallsSaysHowToGetThem() {
        show(base.copy(walls = emptyList()), mutableListOf())
        rule.onNodeWithText("No walls yet. Draw this room on the plan and its walls list here.").performScrollTo()
        rule.onNodeWithTag("hm_room_wall_stage_finished").assertDoesNotExist()
    }
}
