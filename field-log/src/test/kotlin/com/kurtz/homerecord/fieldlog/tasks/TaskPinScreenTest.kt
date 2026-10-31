package com.kurtz.homerecord.fieldlog.tasks

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.TaskPlanPin
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.blueprint.BlueprintPin
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.ui.HouseScreen
import com.kurtz.homerecord.housemodel.ui.HouseUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AC-F35: pin a task on the floor plan from its form; the pin on FL-House opens the task. */
@AcceptanceCriterion("AC-F35")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class TaskPinScreenTest {
    @get:Rule val rule = createComposeRule()

    private val kitchen = PlanRoom.rectangle("r1", "Kitchen", "KITCHEN", 0, 0, 168, 144)
    private val plan = listOf(PinFloor("f0", "Basement", listOf(PlanRoom.rectangle("r3", "Basement", "BASEMENT", 0, 0, 200, 200))), PinFloor("f1", "Main floor", listOf(kitchen)))
    private val draft = TaskFormDraft(title = "Move the outlet", roomId = "r1", planPin = TaskPlanPin("f1", 80, 70))

    private fun show(state: TaskFormUiState): MutableList<TaskFormEvent> {
        val events = mutableListOf<TaskFormEvent>()
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TaskFormScreen(state, { events += it }) } }
        return events
    }

    @Test fun formShowsWhereTheTaskIsPinnedAndOpensThePicker() {
        val events = show(TaskFormUiState(isNew = false, loading = false, draft = draft, initial = draft, rooms = listOf(RoomOption("r1", "Kitchen")), plan = plan))
        rule.onNodeWithText("Main floor · Kitchen").assertExists()
        rule.onNodeWithTag("fl_task_form_pin").performClick()
        assertThat(events).contains(TaskFormEvent.OpenPin)
    }

    @Test fun noPlanHidesThePinField() {
        show(TaskFormUiState(loading = false, draft = TaskFormDraft(title = "Order the meter base")))
        rule.onNodeWithTag("fl_task_form_pin").assertDoesNotExist()
    }

    @Test fun tapOnThePlanPlacesThePinAndDoneAndRemoveWork() {
        val events = show(TaskFormUiState(isNew = false, loading = false, draft = draft, initial = draft, plan = plan, pinning = true, pinFloorId = "f1"))
        rule.onNodeWithTag("fl_task_pin_plan").assertIsDisplayed().performTouchInput { click(center) }
        val placed = events.filterIsInstance<TaskFormEvent.PlacePin>().single()
        // The kitchen fills the plan, so the middle of the canvas is near the middle of the kitchen.
        assertThat(placed.xIn).isIn(60..108)
        assertThat(placed.yIn).isIn(40..104)
        rule.onNodeWithTag("fl_task_pin_floor_f0").performClick()
        assertThat(events).contains(TaskFormEvent.PinFloor("f0"))
        rule.onNodeWithTag("fl_task_pin_remove").performClick()
        assertThat(events).contains(TaskFormEvent.RemovePin)
        rule.onNodeWithTag("fl_task_pin_done").performClick()
        assertThat(events).contains(TaskFormEvent.ClosePin)
    }

    @Test fun pickerWithoutAPinSaysWhereToTapAndOffersNoRemove() {
        show(TaskFormUiState(loading = false, draft = TaskFormDraft(title = "Add a GFCI"), plan = plan, pinning = true, pinFloorId = "f1"))
        rule.onNodeWithText("Tap the plan where the work is.").assertExists()
        rule.onNodeWithTag("fl_task_pin_remove").assertDoesNotExist()
    }

    @Test fun pinOnTheHouseTabOpensItsTask() {
        val opened = mutableListOf<String>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { CompositionLocalProvider(LocalTagPrefix provides "fl") {
                HouseScreen(
                    state = HouseUiState(loading = false, homeName = "Hale kitchen", floors = listOf(FloorSummary("f1", "Main floor", 108, false, 1)), floorId = "f1", rooms = listOf(kitchen)),
                    onEvent = {}, onOpenRoom = {}, onEditPlan = {}, onEditShape = {}, onOpenFloors = {},
                    pins = listOf(BlueprintPin("t1", "Task 1: Move the outlet", 80, 70, 1)),
                    onOpenPin = { opened += it },
                    modifier = Modifier.size(411.dp, 800.dp),
                )
            } }
        }
        rule.onNodeWithText("Tap a pin to open its task, or a room to open the room.").assertExists()
        rule.onNodeWithTag("fl_plan_pin_t1", useUnmergedTree = true).performClick()
        assertThat(opened).containsExactly("t1")
    }
}
