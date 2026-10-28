package com.kurtz.homerecord.fieldlog.photos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.photo.Annotation
import com.kurtz.homerecord.core.photo.NormPoint
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F26: FL-Annotate is a full-screen editor with Close and Save; closing with marks drawn asks before discarding them.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AnnotateDiscardTest {
    @get:Rule val rule = createComposeRule()

    private val arrow = Annotation.Arrow(NormPoint(.1f, .1f), NormPoint(.4f, .4f))

    private fun screen(marks: List<Annotation>, onEvent: (AnnotateEvent) -> Unit = {}, onClose: () -> Unit) = rule.setContent {
        CompositionLocalProvider(LocalTagPrefix provides "fl") {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                AnnotateScreen(AnnotateUiState(loading = false, imagePath = "/none/p1.jpg", marks = marks), onEvent, onClose)
            }
        }
    }

    @Test fun closeWithNoMarksLeavesAtOnce() {
        var closed = 0
        screen(emptyList()) { closed++ }
        rule.onNodeWithTag("fl_save").assertIsNotEnabled()
        rule.onNodeWithTag("fl_close").performClick()
        assertThat(closed).isEqualTo(1)
    }

    @Test fun closeWithMarksAsksAndKeepEditingStays() {
        var closed = 0
        screen(listOf(arrow)) { closed++ }
        rule.onNodeWithTag("fl_close").performClick()
        rule.onNodeWithText("Discard changes?").assertExists()
        rule.onNodeWithText("Keep editing").performClick()
        rule.onNodeWithText("Discard changes?").assertDoesNotExist()
        assertThat(closed).isEqualTo(0)
    }

    @Test fun discardConfirmedCloses() {
        var closed = 0
        screen(listOf(arrow)) { closed++ }
        rule.onNodeWithTag("fl_close").performClick()
        rule.onNodeWithText("Discard").performClick()
        assertThat(closed).isEqualTo(1)
    }

    @Test fun saveSendsTheSaveEvent() {
        val events = mutableListOf<AnnotateEvent>()
        screen(listOf(arrow), onEvent = { events += it }) {}
        rule.onNodeWithTag("fl_save").assertIsEnabled().performClick()
        rule.onNodeWithTag("fl_annotate_undo").assertIsEnabled()
        assertThat(events).containsExactly(AnnotateEvent.Save)
    }
}
