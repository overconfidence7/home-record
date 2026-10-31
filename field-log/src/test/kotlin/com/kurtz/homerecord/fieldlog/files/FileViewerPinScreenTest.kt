package com.kurtz.homerecord.fieldlog.files

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.pages.PageSource
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.tasks.FormFilePin
import com.kurtz.homerecord.fieldlog.tasks.TaskFormDraft
import com.kurtz.homerecord.fieldlog.tasks.TaskFormEvent
import com.kurtz.homerecord.fieldlog.tasks.TaskFormScreen
import com.kurtz.homerecord.fieldlog.tasks.TaskFormUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** AC-F35: pin a task on a plan file page in the viewer; a pin on the page opens its task (rules 13, 16). */
@AcceptanceCriterion("AC-F35")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class FileViewerPinScreenTest {
    @get:Rule val rule = createComposeRule()

    private val pages = object : PageSource {
        override val pageCount = 1
        private val page = Bitmap.createBitmap(600, 780, Bitmap.Config.ARGB_8888).asImageBitmap()
        override fun peek(index: Int): ImageBitmap = page
        override suspend fun render(index: Int, widthPx: Int): ImageBitmap = page
    }

    private val base = FileViewerUiState(
        loading = false, name = "Garage plan.pdf", path = "p", mime = "application/pdf", pages = pages, jobId = "j1", canPinTasks = true,
        pins = listOf(PagePin("t1", 1, "Move the subpanel", 0, 0.5f, 0.5f)),
    )

    private fun show(state: FileViewerUiState): MutableList<FileViewerEvent> {
        val events = mutableListOf<FileViewerEvent>()
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { FileViewerScreen(state, { events += it }) } }
        return events
    }

    @Test fun pinIsAFullTouchTargetThatOpensItsTask() {
        val events = show(base)
        rule.onNodeWithContentDescription("Task 1: Move the subpanel").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag("fl_file_pin_t1").performClick()
        assertThat(events).containsExactly(FileViewerEvent.OpenPin("t1"))
    }

    @Test fun pinATaskEntersPinModeAndATapOnThePageReportsTheSpot() {
        val events = show(base.copy(pins = emptyList(), pinMode = true))
        rule.onNodeWithText("Tap the page where the work is.").assertExists()
        rule.onNodeWithTag("fl_file_page_1").performTouchInput { click(percentOffset(0.25f, 0.75f)) }
        val tap = events.filterIsInstance<FileViewerEvent.TapPage>().single()
        assertThat(tap.page).isEqualTo(0)
        assertThat(tap.x).isWithin(0.02f).of(0.25f)
        assertThat(tap.y).isWithin(0.02f).of(0.75f)
        rule.onNodeWithTag("fl_file_pin_cancel").performClick()
        rule.onNodeWithTag("fl_file_pin").performClick()
        assertThat(events.takeLast(2)).containsExactly(FileViewerEvent.CancelPinning, FileViewerEvent.CancelPinning).inOrder()
    }

    @Test fun outsidePinModeATapOnThePageDoesNothingAndCrewHaveNoPinAction() {
        val events = show(base.copy(canPinTasks = false))
        rule.onNodeWithTag("fl_file_pin").assertDoesNotExist()
        rule.onNodeWithTag("fl_file_page_1").performTouchInput { click(percentOffset(0.1f, 0.1f)) }
        assertThat(events).isEmpty()
    }

    @Test fun chooserOffersNewTaskHereAndTheOpenTasks() {
        val events = mutableListOf<FileViewerEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                PinChooser(listOf(TaskChoice("t1", "Move the subpanel", pinnedHere = true), TaskChoice("t2", "Label breakers", pinnedHere = false))) { events += it }
            }
        }
        rule.onNodeWithText("Pinned on this file. Choosing it moves the pin.").assertExists()
        rule.onNodeWithTag("fl_file_pin_new").performClick()
        rule.onNodeWithTag("fl_file_pin_choice_t2").performClick()
        assertThat(events).containsExactly(FileViewerEvent.NewTaskHere, FileViewerEvent.ChooseTask("t2")).inOrder()
    }

    @Test fun taskFormShowsTheFilePinAndRemovesIt() {
        val draft = TaskFormDraft(title = "Move the subpanel", filePin = FormFilePin("plan1", 1, 0.5f, 0.5f))
        val events = mutableListOf<TaskFormEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                TaskFormScreen(TaskFormUiState(isNew = false, loading = false, draft = draft, initial = draft, fileName = "Garage plan.pdf"), { events += it })
            }
        }
        rule.onNodeWithText("Garage plan.pdf · Page 2").assertExists()
        rule.onNodeWithTag("fl_task_form_file_pin_remove").assertHeightIsAtLeast(48.dp).performClick()
        assertThat(events).containsExactly(TaskFormEvent.RemoveFilePin)
    }
}
