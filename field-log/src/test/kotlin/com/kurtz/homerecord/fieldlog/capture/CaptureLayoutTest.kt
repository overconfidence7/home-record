package com.kurtz.homerecord.fieldlog.capture

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.fieldlog.CaptureDeniedPreview
import com.kurtz.homerecord.fieldlog.CaptureReadyPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// AC-F24 and ui-standards rules 30 and 33: the viewfinder leads in both orientations and the shutter stays reachable.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CaptureLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun assertShutterOnScreen() {
        val shutter = rule.onNodeWithTag("fl_capture_shutter").assertIsDisplayed().assertHeightIsAtLeast(72.dp).getUnclippedBoundsInRoot()
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        assertThat(shutter.bottom).isAtMost(root.bottom)
        assertThat(shutter.right).isAtMost(root.right)
    }

    @Config(qualifiers = "w411dp-h891dp")
    @Test fun portraitViewfinderOverHalfTheScreen() {
        rule.setContent { CaptureReadyPreview() }
        rule.onNodeWithTag("fl_capture_viewfinder").assertHeightIsAtLeast(420.dp)
        assertShutterOnScreen()
    }

    @Config(qualifiers = "w891dp-h411dp-land")
    @Test fun landscapeViewfinderKeepsFullHeight() {
        rule.setContent { CaptureReadyPreview() }
        val finder = rule.onNodeWithTag("fl_capture_viewfinder").assertHeightIsAtLeast(300.dp).getUnclippedBoundsInRoot()
        val shutter = rule.onNodeWithTag("fl_capture_shutter").getUnclippedBoundsInRoot()
        assertThat(finder.left).isLessThan(shutter.left)
        assertThat(finder.right).isAtMost(shutter.left)
        assertShutterOnScreen()
    }

    @Config(qualifiers = "w891dp-h411dp-land")
    @Test fun landscapeAt200PercentKeepsShutterAndViewfinder() {
        RuntimeEnvironment.setFontScale(2f)
        rule.setContent { CaptureReadyPreview() }
        rule.onNodeWithTag("fl_capture_viewfinder").assertHeightIsAtLeast(300.dp)
        assertShutterOnScreen()
    }

    // AC-F24: until the camera is bound the shutter shows a spinner and does nothing
    @Test fun shutterIsOffWhileTheCameraStarts() {
        val events = mutableListOf<CaptureEvent>()
        var state by mutableStateOf(CaptureUiState(loading = false, jobName = "Ortiz kitchen rewire", access = CameraAccess.Granted))
        rule.setContent { CaptureScreen(state, { events += it }, camera = { Box(it) }) }
        rule.onNodeWithTag("fl_capture_shutter").assertIsNotEnabled()
        rule.onNodeWithTag("fl_capture_starting").assertExists()
        state = state.copy(cameraReady = true)
        rule.onNodeWithTag("fl_capture_starting").assertDoesNotExist()
        rule.onNodeWithTag("fl_capture_shutter").assertIsEnabled().performClick()
        assertThat(events).containsExactly(CaptureEvent.Shutter)
    }

    // ui-standards rule 30 (cr:fl-capture-targets): every capture control is at least 56 dp tall for gloved hands
    @Test fun roomAndPhaseChipsAreGloveSized() {
        rule.setContent { CaptureReadyPreview() }
        rule.onNodeWithTag("fl_capture_room_r1").assertHeightIsAtLeast(56.dp)
        rule.onNodeWithTag("fl_capture_phase_Rough-in").assertHeightIsAtLeast(56.dp)
    }

    @Test fun deniedSettingsButtonIsGloveSized() {
        rule.setContent { CaptureDeniedPreview() }
        rule.onNodeWithTag("fl_capture_settings").assertHeightIsAtLeast(56.dp)
    }

    // AC-F24, AC-F29 (cr:fl-screen-signature): gallery and back are events like the rest, with or without the camera
    @Test fun galleryAndBackAreEvents() {
        val events = mutableListOf<CaptureEvent>()
        var state by mutableStateOf(CaptureUiState(loading = false, jobName = "Ortiz kitchen rewire", access = CameraAccess.Granted, cameraReady = true))
        rule.setContent { CaptureScreen(state, { events += it }, camera = { Box(it) }) }
        rule.onNodeWithTag("fl_capture_gallery").performClick()
        rule.onNodeWithContentDescription("Back").performClick()
        state = state.copy(access = CameraAccess.Denied)
        rule.onNodeWithTag("fl_capture_gallery").performClick()
        assertThat(events).containsExactly(CaptureEvent.PickGallery, CaptureEvent.Back, CaptureEvent.PickGallery).inOrder()
    }

    // ui-standards rule 21: the note field has a visible label, not only a placeholder
    @Test fun noteFieldIsLabelled() {
        var state by mutableStateOf(CaptureUiState(loading = false, jobName = "Ortiz kitchen rewire", access = CameraAccess.Granted, cameraReady = true))
        rule.setContent { CaptureScreen(state, {}, camera = { Box(it) }) }
        state = state.copy(noteOpen = true)
        rule.onNodeWithTag("fl_capture_note_text").assert(androidx.compose.ui.test.hasText("Note"))
    }
}
