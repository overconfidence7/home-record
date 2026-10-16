package com.kurtz.homerecord.fieldlog.capture

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CaptureScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun shutterIsAtLeast72dpAndFiresEvent() {
        var events = 0
        rule.setContent { HomeRecordTheme { CaptureScreen(CaptureUiState(), onEvent = { events++ }) {} } }
        val shutter = rule.onNodeWithContentDescription("Take photo")
        shutter.assertWidthIsAtLeast(72.dp).assertHeightIsAtLeast(72.dp).performClick()
        assertEquals(1, events)
    }

    @Test fun permissionDeniedShowsExplanationAndSettingsButton() {
        var settings = 0
        rule.setContent { HomeRecordTheme { PermissionDenied(message = "Field Log needs camera access", onOpenSettings = { settings++ }, onBack = {}) } }
        rule.onNodeWithText("Field Log needs camera access", substring = true).assertExists()
        rule.onNodeWithText("Open Settings").performClick()
        assertEquals(1, settings)
    }

    @Test fun noteAndVoiceButtonsFireEventsAndRecordingShowsElapsed() {
        val events = mutableListOf<CaptureEvent>()
        rule.setContent {
            HomeRecordTheme { CaptureScreen(CaptureUiState(recording = true, elapsedMs = 65_000), onEvent = { events += it }) {} }
        }
        rule.onNodeWithText("Recording 1:05").assertExists()
        rule.onNodeWithText("Note").performClick()
        rule.onNodeWithText("Stop").performClick()
        assertEquals(listOf(CaptureEvent.OpenNote, CaptureEvent.ToggleVoice), events)
    }
}
