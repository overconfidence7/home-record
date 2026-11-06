package com.kurtz.homerecord.housemodel.blueprint

import android.content.Context
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// AC-C13 [judge:hm-blueprint-names-font200]: at 200% font a room name shrinks to fit its room before it is dropped
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = android.app.Application::class)
class RoomNameLayoutTest {
    private val measurer = TextMeasurer(
        createFontFamilyResolver(ApplicationProvider.getApplicationContext<Context>()),
        Density(density = 2.625f, fontScale = 2f),
        LayoutDirection.Ltr,
    )
    private val name = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    private val small = TextStyle(fontSize = 11.sp, lineHeight = 16.sp)

    private fun room(widthIn: Int, lengthIn: Int, label: String) =
        PlanRoom("r", label, points = listOf(Pt(0, 0), Pt(widthIn, 0), Pt(widthIn, lengthIn), Pt(0, lengthIn)))

    @Test fun shrinkStepsEndAtHalfTheSmallSize() {
        val steps = nameShrinkSteps(small)
        assertThat(steps.first()).isEqualTo(small)
        assertThat(steps.map { it.fontSize.value }).containsExactly(11f, 11f * 0.85f, 11f * 0.7f, 5.5f).inOrder()
        assertThat(steps.last().lineHeight.value).isEqualTo(8f)
    }

    @Test fun aNameTooWideAtTheSmallSizeShrinksInsteadOfDisappearing() {
        val full = measurer.measure("Living room", small)
        val half = measurer.measure("Living room", small.copy(fontSize = 5.5.sp, lineHeight = 8.sp))
        // A room whose 90% width holds the name at half size on one line, but whose height holds one line only.
        val z = 1f
        val widthIn = (half.size.width * 1.02f / 0.9f / z).toInt() + 1
        val lengthIn = ((half.size.height + 2) / 0.9f / z).toInt() + 1
        assertThat(full.size.width.toFloat()).isGreaterThan(widthIn * z * 0.9f)

        val layout = roomNameLayout(room(widthIn, lengthIn, "Living room"), z, measurer, name, small)

        assertThat(layout).isNotNull()
        val l = checkNotNull(layout)
        assertThat(l.size.width.toFloat()).isAtMost(widthIn * z * 0.9f)
        assertThat(l.size.height.toFloat()).isAtMost(lengthIn * z * 0.9f)
        assertThat(l.layoutInput.style.fontSize.value).isLessThan(11f)
    }

    @Test fun aNameThatFitsKeepsTheFullSize() {
        val layout = checkNotNull(roomNameLayout(room(2000, 2000, "Kitchen"), 1f, measurer, name, small))
        assertThat(layout.layoutInput.style.fontSize).isEqualTo(14.sp)
    }

    @Test fun aRoomTooSmallForEvenHalfSizeShowsNoName() {
        assertThat(roomNameLayout(room(10, 10, "Living room"), 1f, measurer, name, small)).isNull()
    }
}
