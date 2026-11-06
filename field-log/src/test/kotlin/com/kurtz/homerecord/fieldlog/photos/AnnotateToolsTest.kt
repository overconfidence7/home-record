package com.kurtz.homerecord.fieldlog.photos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertWithMessage
import com.kurtz.homerecord.core.photo.AnnotationTool
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [judge:fl-annotate-tools-hidden], AC-F26: all six mark-up tools (Measure included) sit inside the screen at 100% and
 * 200% text; the row wraps instead of scrolling Text and Measure off the right edge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class AnnotateToolsTest {
    @get:Rule val rule = createComposeRule()

    private fun show(fontScale: Float) = rule.setContent {
        val d = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale), LocalTagPrefix provides "fl") {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                AnnotateScreen(AnnotateUiState(loading = false, imagePath = "/none/p1.jpg"), {}, {})
            }
        }
    }

    private fun assertAllToolsOnScreen() {
        val root = rule.onRoot().getBoundsInRoot()
        AnnotationTool.entries.forEach { t ->
            val b = rule.onNodeWithTag("fl_annotate_tool_${t.name.lowercase()}").getBoundsInRoot()
            assertWithMessage("${t.name} right edge").that(b.right.value).isAtMost(root.right.value)
            assertWithMessage("${t.name} left edge").that(b.left.value).isAtLeast(root.left.value)
        }
    }

    @Test fun everyToolFitsAtNormalText() {
        show(1f)
        assertAllToolsOnScreen()
    }

    @Test fun everyToolFitsAtDoubleText() {
        show(2f)
        assertAllToolsOnScreen()
    }
}
