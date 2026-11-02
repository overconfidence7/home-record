package com.kurtz.homerecord.fieldlog.tasks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.TaskPlanPin
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F35: a Crew member's pin opens the task, read only: what, who, when, where, no Save, Delete or pickers (R09g)
@AcceptanceCriterion("AC-F35")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class TaskViewScreenTest {
    @get:Rule val rule = createComposeRule()

    private val draft = TaskFormDraft(
        title = "Set the kitchen outlets", assigneeId = "m3", due = "2026-11-04", roomId = "r1", note = "Bring the 20 A receptacles.",
        punch = true, planPin = TaskPlanPin("main", 40, 40), filePin = FormFilePin("plan1", 1, 0.2f, 0.3f),
    )
    private val state = TaskFormUiState(
        isNew = false, loading = false, readOnly = true, draft = draft, initial = draft,
        people = listOf(PersonOption("m3", "Marcus Bell")), rooms = listOf(RoomOption("r1", "Kitchen")),
        plan = listOf(PinFloor("main", "Main floor", listOf(PlanRoom.rectangle("r1", "Kitchen", "KITCHEN", 0, 0, 144, 120)))),
        fileName = "Kitchen plan.pdf",
    )

    @Test fun showsTheTaskWithNothingToEdit() {
        val events = mutableListOf<TaskFormEvent>()
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TaskFormScreen(state, { events += it }) } }
        rule.onNodeWithTag("fl_task_view").assertIsDisplayed()
        rule.onNodeWithText("Set the kitchen outlets").assertIsDisplayed()
        rule.onNodeWithText("Not done yet").assertIsDisplayed()
        rule.onNodeWithText("Marcus Bell").assertIsDisplayed()
        rule.onNodeWithText("Main floor · Kitchen").assertIsDisplayed()
        rule.onNodeWithText("Kitchen plan.pdf · Page 2").assertExists()
        rule.onNodeWithText("Bring the 20 A receptacles.").assertExists()
        rule.onNodeWithTag("fl_task_form_title").assertDoesNotExist()
        rule.onNodeWithTag("fl_task_form_delete").assertDoesNotExist()
        rule.onNodeWithTag("fl_task_form_assignee").assertDoesNotExist()
        rule.onNodeWithTag("fl_task_form_file_pin_remove").assertDoesNotExist()
        rule.onNodeWithTag("fl_back").performClick()
        assertThat(events).containsExactly(TaskFormEvent.Close)
    }

    @Test fun aDoneTaskSaysDone() {
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TaskFormScreen(state.copy(isDone = true), {}) } }
        rule.onNodeWithText("Done").assertIsDisplayed()
    }
}
