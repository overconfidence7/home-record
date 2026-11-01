package com.kurtz.homerecord.fieldlog.tasks

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.TaskFilter
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** FL-Tasks (AC-F34): ticking, the Undo snackbar, the punch filter, and what Crew may do (AC-F3). */
@AcceptanceCriterion("AC-F34")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TasksScreenTest {
    @get:Rule val rule = createComposeRule()

    private val ready = TasksUiState(
        loading = false,
        open = listOf(
            TaskRow("t1", "Set the kitchen outlets", assignee = "Marcus Bell"),
            TaskRow("t2", "Other crew's task", canTick = false),
        ),
        done = listOf(TaskRow("t3", "Order the meter base", done = true)),
        total = 3, canManage = true,
    )

    private fun show(state: TasksUiState): MutableList<TasksEvent> {
        val events = mutableListOf<TasksEvent>()
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TasksScreen(state, { events += it }) } }
        return events
    }

    @Test fun tickSendsSetDoneAndRowOpensTheTask() {
        val events = show(ready)
        rule.onNodeWithTag("fl_tasks_check_t1").performClick()
        rule.onNodeWithTag("fl_tasks_row_t1").performClick()
        rule.onNodeWithTag("fl_tasks_add").performClick()
        assertThat(events).containsExactly(TasksEvent.SetDone("t1", true), TasksEvent.Open("t1"), TasksEvent.Add).inOrder()
        rule.onNodeWithText("Done (1)").assertExists()
        rule.onNodeWithTag("fl_tasks_check_t2").assertIsNotEnabled()
    }

    @Test fun undoSnackbarSendsUndo() {
        val events = show(ready.copy(undo = TaskUndo("t1", "Set the kitchen outlets")))
        rule.onNodeWithText("Done: Set the kitchen outlets").assertExists()
        rule.onNodeWithText("Undo").performClick()
        rule.waitForIdle()
        assertThat(events).contains(TasksEvent.Undo)
    }

    @Test fun crewGetNoAddAndRowsDoNotOpen() {
        val events = show(ready.copy(canManage = false, open = listOf(TaskRow("t1", "Set the kitchen outlets")), done = emptyList(), total = 1))
        rule.onNodeWithTag("fl_tasks_add").assertDoesNotExist()
        rule.onNodeWithTag("fl_tasks_row_t1").performClick()
        assertThat(events).isEmpty()
    }

    @Test fun emptyPunchFilterOffersShowAll() {
        val events = show(ready.copy(filter = TaskFilter.PUNCH, open = emptyList(), done = emptyList()))
        rule.onNodeWithTag("fl_tasks_show_all").performClick()
        rule.onNodeWithTag("fl_tasks_filter_punch").performClick()
        assertThat(events).containsExactly(TasksEvent.SetFilter(TaskFilter.ALL), TasksEvent.SetFilter(TaskFilter.PUNCH)).inOrder()
    }

    @Test fun emptyJobOffersAddTaskOnlyToTheOffice() {
        val events = show(TasksUiState(loading = false, canManage = true))
        rule.onNodeWithTag("fl_tasks_empty_add").performClick()
        assertThat(events).containsExactly(TasksEvent.Add)
    }

    @Test fun swipingAnOpenTaskTicksItDone() {
        val events = show(ready)
        rule.onNodeWithTag("fl_tasks_swipe_t1").performTouchInput { swipeRight() }
        rule.waitForIdle()
        assertThat(events).contains(TasksEvent.SetDone("t1", true))
        // Someone else's task does not swipe, as it cannot be ticked.
        rule.onNodeWithTag("fl_tasks_swipe_t2").assertDoesNotExist()
        rule.onNodeWithTag("fl_tasks_swipe_t3").assertDoesNotExist()
    }

    // AC-F34: a half-width swipe from the middle of the row, as a thumb (and Maestro's F11 swipe) makes it, completes the task.
    @Test fun aShortSwipeFromTheMiddleTicksItDone() {
        val events = show(ready)
        rule.onNodeWithTag("fl_tasks_swipe_t1").performTouchInput {
            swipe(center, Offset(width * 0.9f, centerY), durationMillis = 400)
        }
        rule.waitForIdle()
        assertThat(events).contains(TasksEvent.SetDone("t1", true))
    }

    /** The Tasks tab over a live state: SetDone moves the row to Done with Undo, Undo moves it back (F11's sequence). */
    private fun showLive(): MutableList<TasksEvent> {
        val events = mutableListOf<TasksEvent>()
        val t1 = TaskRow("t1", "Set the kitchen outlets", note = "GFCI by the sink")
        val t2 = TaskRow("t2", "Replace cracked cover plate", punch = true)
        val openState = TasksUiState(loading = false, open = listOf(t1, t2), total = 2, canManage = true)
        var state by mutableStateOf(openState)
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                TasksScreen(state, { e ->
                    events += e
                    when (e) {
                        is TasksEvent.SetDone -> state = TasksUiState(
                            loading = false, open = listOf(t2), done = listOf(t1.copy(done = true)), total = 2, canManage = true,
                            undo = TaskUndo("t1", t1.title),
                        )
                        TasksEvent.Undo -> state = openState
                        else -> Unit
                    }
                })
            }
        }
        return events
    }

    private fun swipeT1() {
        rule.onNodeWithTag("fl_tasks_swipe_t1").performTouchInput { swipe(center, Offset(width * 0.9f, centerY), durationMillis = 400) }
        rule.waitForIdle()
    }

    // AC-F34: F11 on device: tick, Undo, then swipe the same row; the swipe must still complete it.
    @Test fun aRowBackFromUndoAfterATickStillSwipesDone() {
        val events = showLive()
        rule.onNodeWithTag("fl_tasks_check_t1").performClick()
        rule.onNodeWithText("Undo").performClick()
        rule.waitForIdle()
        swipeT1()
        assertThat(events.filterIsInstance<TasksEvent.SetDone>()).hasSize(2)
        rule.onNodeWithText("Done: Set the kitchen outlets").assertExists()
    }

    // AC-F34: swipe, Undo, swipe again: the second swipe completes it too and the row comes back in place.
    @Test fun aRowBackFromUndoAfterASwipeSwipesDoneAgain() {
        val events = showLive()
        swipeT1()
        rule.onNodeWithText("Undo").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("fl_tasks_check_t1").assertIsDisplayed()
        swipeT1()
        assertThat(events.filterIsInstance<TasksEvent.SetDone>()).hasSize(2)
    }

    @Test fun swipingTheOtherWayTicksItToo() {
        val events = show(ready)
        rule.onNodeWithTag("fl_tasks_swipe_t1").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertThat(events).contains(TasksEvent.SetDone("t1", true))
    }

    @Test fun managersGetDragHandlesAndMoveActions() {
        val events = show(ready)
        rule.onAllNodesWithTag("fl_tasks_handle", useUnmergedTree = true).assertCountEquals(2)
        val row = rule.onNodeWithTag("fl_tasks_row_t1")
        row.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions))
        val down = row.fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "Move down" }
        rule.runOnIdle { down.action() }
        assertThat(events).containsExactly(TasksEvent.Move("t1", 1))
    }

    @Test fun noHandlesOnThePunchListOrForCrew() {
        show(ready.copy(filter = TaskFilter.PUNCH))
        rule.onNodeWithTag("fl_tasks_handle", useUnmergedTree = true).assertDoesNotExist()
    }
}
