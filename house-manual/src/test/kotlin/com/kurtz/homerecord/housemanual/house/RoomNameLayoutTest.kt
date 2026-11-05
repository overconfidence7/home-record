package com.kurtz.homerecord.housemanual.house

import com.kurtz.homerecord.housemodel.blueprint.roomNameLayout

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// [judge:hm-blueprint-names-font200]: at 200% text a room name wraps or shrinks to fit its room instead of vanishing
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RoomNameLayoutTest {
    @get:Rule val compose = createComposeRule()

    /** Measures every case inside one composition (a compose rule takes one setContent per test). */
    private fun layouts(vararg cases: Triple<String, Float, Float>): List<TextLayoutResult> {
        var result: List<TextLayoutResult> = emptyList()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density = 2.625f, fontScale = 2f)) {
                val measurer = rememberTextMeasurer()
                val full = roomNameLayout(measurer, cases.first().first, TextStyle(fontSize = 14.sp), 100_000f, 100_000f)
                result = listOf(full) + cases.drop(1).map { (name, w, h) ->
                    roomNameLayout(measurer, name, TextStyle(fontSize = 14.sp), if (w < 0) full.size.width * -w else w, h)
                }
            }
        }
        compose.waitForIdle()
        return result
    }

    @Test fun aTwoWordNameWrapsOntoTwoLinesAtFullSize() {
        // a negative width is that share of the one-line width
        val (wide, wrapped) = layouts(Triple("Living room", 0f, 0f), Triple("Living room", -0.7f, 100_000f))
        assertThat(wrapped.lineCount).isEqualTo(2)
        assertThat(wrapped.hasVisualOverflow).isFalse()
        assertThat(wrapped.layoutInput.style.fontSize).isEqualTo(wide.layoutInput.style.fontSize)
    }

    @Test fun aWordWiderThanTheRoomShrinksBeforeItIsEllipsized() {
        val (full, shrunk) = layouts(Triple("Mudroom", 0f, 0f), Triple("Mudroom", -0.8f, 100_000f))
        assertThat(shrunk.layoutInput.style.fontSize.value).isLessThan(full.layoutInput.style.fontSize.value)
        assertThat(shrunk.hasVisualOverflow).isFalse()
        assertThat(shrunk.size.width.toFloat()).isAtMost(full.size.width * 0.8f + 1f)
    }

    @Test fun aRoomTooSmallForAnyStepStillGetsANameThatFits() {
        val tiny = layouts(Triple("Dining room", 0f, 0f), Triple("Dining room", 40f, 100_000f))[1]
        assertThat(tiny.size.width).isAtMost(40)
        assertThat(tiny.layoutInput.text.text).isEqualTo("Dining room")
    }
}
