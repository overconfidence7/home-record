package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.scene.FloorScene
import com.kurtz.homerecord.housemodel.scene.SceneLayer
import com.kurtz.homerecord.housemodel.scene.StackedFloor
import com.kurtz.homerecord.housemodel.ui.House3DEvent
import com.kurtz.homerecord.housemodel.ui.House3DScreen
import com.kurtz.homerecord.housemodel.ui.House3DUiState
import com.kurtz.homerecord.housemodel.ui.HouseScreen
import com.kurtz.homerecord.housemodel.ui.HouseUiState
import com.kurtz.homerecord.housemodel.walls.WallKey
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C20: HM-3D shows the floor with Reset view, a stage selector and floor chips; HM-House opens it with "3D view".
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class House3DScreenTest {
    @get:Rule val rule = createComposeRule()

    private val rooms = listOf(
        PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144),
        PlanRoom.rectangle("l", "Living room", "LIVING_ROOM", 168, 0, 240, 192),
    )
    private val floors = listOf(FloorSummary("b", "Basement", 96, false, 0), FloorSummary("m", "Main", 96, false, 2))
    private val scene = FloorScene.build(rooms, 96, mapOf(WallKey("k", 0) to mapOf(WallStage.FINISHED to "/m/k.jpg")))
    private val ready = House3DUiState(loading = false, homeName = "Maple St", floors = floors, floorId = "m", scene = scene)

    private fun show(state: House3DUiState, events: MutableList<House3DEvent>, plans: MutableList<String?> = mutableListOf()) = rule.setContent {
        CompositionLocalProvider(LocalTagPrefix provides "hm") {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { House3DScreen(state, { events += it }, {}, { plans += it }) }
        }
    }

    @Test
    @AcceptanceCriterion("AC-C20")
    fun showsTheFloorWithResetViewStagesAndFloors() {
        val events = mutableListOf<House3DEvent>()
        show(ready, events)
        rule.onNodeWithText("Main in 3D").assertExists()
        rule.onNodeWithContentDescription("3D view of Main: 2 rooms").assertExists()
        rule.onNodeWithText("Finished photos on 1 of 8 walls").assertExists()

        rule.onNodeWithTag("hm_3d_reset").performClick()
        rule.onNodeWithTag("hm_3d_stage_rough_in").performClick()
        rule.onNodeWithTag("hm_3d_floor_b").performClick()
        assertThat(events).containsExactly(
            House3DEvent.ResetView,
            House3DEvent.SelectStage(WallStage.ROUGH_IN),
            House3DEvent.SelectFloor("b"),
        ).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C20")
    fun anEmptyFloorOffersEditPlanAndAFailureOffersRetry() {
        val events = mutableListOf<House3DEvent>()
        val plans = mutableListOf<String?>()
        val state = ready.copy(floorId = "b", scene = FloorScene.build(emptyList(), 96, emptyMap()))
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                    House3DScreen(state, { events += it }, {}, { plans += it })
                }
            }
        }
        rule.onNodeWithText("Nothing to show in 3D yet").assertExists()
        rule.onNodeWithTag("hm_3d_reset").assertDoesNotExist()
        rule.onNodeWithTag("hm_3d_edit_plan").performClick()
        assertThat(plans).containsExactly("b")
    }

    @Test
    @AcceptanceCriterion("AC-C20")
    fun aFailedBuildOffersRetry() {
        val events = mutableListOf<House3DEvent>()
        show(ready.copy(scene = null, failed = true), events)
        rule.onNodeWithText("Retry").performClick()
        assertThat(events).containsExactly(House3DEvent.Retry)
    }

    @Test
    @AcceptanceCriterion("AC-C20")
    fun theHouseTabOpens3DOnlyWhenTheFloorHasRooms() {
        var opened = 0
        val house = HouseUiState(loading = false, homeName = "Maple St", floors = floors, floorId = "m", rooms = rooms)
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { HouseScreen(house, {}, {}, {}, {}, {}, on3D = { opened++ }) }
            }
        }
        rule.onNodeWithTag("hm_house_3d").performClick()
        assertThat(opened).isEqualTo(1)
    }

    @Test
    @AcceptanceCriterion("AC-C21")
    fun allFloorsChipShowsTheStackAndTheFadedFloorsAreNamed() {
        val events = mutableListOf<House3DEvent>()
        val stacked = FloorScene.stack(
            listOf(
                SceneLayer(StackedFloor("b", 96, -102, ghost = true), listOf(PlanRoom.rectangle("u", "Utility", "UTILITY", 0, 0, 168, 144))),
                SceneLayer(StackedFloor("m", 96, 0, ghost = false), rooms),
            ),
            emptyMap(),
        )
        show(ready.copy(scene = stacked), events)
        rule.onNodeWithText("Floors below are shown faded.").assertExists()
        rule.onNodeWithTag("hm_3d_floor_all").performClick()
        assertThat(events).containsExactly(House3DEvent.ShowAllFloors)
    }

    @Test
    @AcceptanceCriterion("AC-C21")
    fun allFloorsTitlesTheView() {
        show(ready.copy(allFloors = true), mutableListOf())
        rule.onNodeWithText("All floors in 3D").assertExists()
        rule.onNodeWithContentDescription("3D view of All floors: 2 rooms").assertExists()
    }
}
