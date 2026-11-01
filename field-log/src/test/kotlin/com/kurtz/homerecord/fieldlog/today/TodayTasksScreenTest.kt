package com.kurtz.homerecord.fieldlog.today

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.model.TaskDue
import com.kurtz.homerecord.core.punch.ClockStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.tasks.TaskUndo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** My tasks on FL-Today (AC-F34): due today and overdue, tick or swipe to done with Undo, tap opens the task. */
@AcceptanceCriterion("AC-F34")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TodayTasksScreenTest {
    @get:Rule val rule = createComposeRule()

    private val day = LocalDate.of(2026, 10, 25)
    private val state = TodayUiState(
        "Kurtz Electric", CompanyRole.Crew, hasJobs = true,
        clock = TodayClock(ClockStatus.Out, null, 0),
        tasks = listOf(
            TodayTask("t1", "j2", "Replace GFCI by the back door", "Deck Rebuild", day.minusDays(3), TaskDue.OVERDUE),
            TodayTask("t2", "j1", "Label the kitchen circuits", "Kitchen Remodel", day, TaskDue.TODAY),
        ),
    )

    private fun show(s: TodayUiState): MutableList<TodayEvent> {
        val events = mutableListOf<TodayEvent>()
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TodayScreen(s, { events += it }) } }
        return events
    }

    private fun scrollTo(tag: String) = rule.onNodeWithTag("fl_today_list").performScrollToNode(hasTestTag(tag))

    @Test fun showsMyTasksWithJobAndDueAndTickOrTapAct() {
        val events = show(state)
        scrollTo("fl_today_task_t2")
        rule.onNodeWithText("My tasks").assertExists()
        rule.onNodeWithText("Kitchen Remodel · Due today").assertExists()
        rule.onNodeWithTag("fl_today_task_check_t1").performClick()
        rule.onNodeWithTag("fl_today_task_t2").performClick()
        assertThat(events).containsExactly(TodayEvent.CompleteTask("t1"), TodayEvent.OpenTask("j1", "t2")).inOrder()
    }

    @Test fun swipeEitherWayCompletesTheTask() {
        val events = show(state)
        scrollTo("fl_today_task_swipe_t2")
        rule.onNodeWithTag("fl_today_task_swipe_t1").performTouchInput { swipeRight() }
        rule.onNodeWithTag("fl_today_task_swipe_t2").performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertThat(events).containsExactly(TodayEvent.CompleteTask("t1"), TodayEvent.CompleteTask("t2")).inOrder()
    }

    // AC-F34: a half-width swipe from the middle of the row (F11's Maestro swipe) is enough.
    @Test fun aShortSwipeFromTheMiddleCompletesTheTask() {
        val events = show(state)
        rule.onNodeWithTag("fl_today_task_swipe_t1").performTouchInput {
            swipe(center, Offset(width * 0.9f, centerY), durationMillis = 400)
        }
        rule.waitForIdle()
        assertThat(events).containsExactly(TodayEvent.CompleteTask("t1"))
    }

    // AC-F34: F11 on device: swipe a task off My tasks, Undo brings it back in place, and it swipes done again.
    @Test fun aTaskBackFromUndoSwipesDoneAgain() {
        val events = mutableListOf<TodayEvent>()
        var s by mutableStateOf(state)
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                TodayScreen(s, { e ->
                    events += e
                    when (e) {
                        is TodayEvent.CompleteTask -> s = state.copy(tasks = state.tasks.filter { it.id != e.taskId }, taskUndo = TaskUndo(e.taskId, "x"))
                        TodayEvent.UndoTask -> s = state
                        else -> Unit
                    }
                })
            }
        }
        scrollTo("fl_today_task_swipe_t1")
        val swipe: () -> Unit = {
            rule.onNodeWithTag("fl_today_task_swipe_t1").performTouchInput { swipe(center, Offset(width * 0.9f, centerY), durationMillis = 400) }
            rule.waitForIdle()
        }
        swipe()
        rule.onNodeWithText("Undo").performClick()
        rule.waitForIdle()
        scrollTo("fl_today_task_swipe_t1")
        rule.onNodeWithTag("fl_today_task_check_t1").assertIsDisplayed()
        swipe()
        assertThat(events.filterIsInstance<TodayEvent.CompleteTask>()).containsExactly(TodayEvent.CompleteTask("t1"), TodayEvent.CompleteTask("t1"))
    }

    @Test fun undoSnackbarSendsUndoTask() {
        val events = show(state.copy(taskUndo = TaskUndo("t1", "Replace GFCI by the back door")))
        rule.onNodeWithText("Undo").performClick()
        rule.waitForIdle()
        assertThat(events).contains(TodayEvent.UndoTask)
    }

    @Test fun noSectionWhenNothingIsDue() {
        show(state.copy(tasks = emptyList()))
        rule.onNodeWithText("My tasks").assertDoesNotExist()
    }
}
