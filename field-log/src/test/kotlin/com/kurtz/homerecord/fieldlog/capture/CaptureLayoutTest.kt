package com.kurtz.homerecord.fieldlog.capture

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.fieldlog.CaptureReadyPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
}
