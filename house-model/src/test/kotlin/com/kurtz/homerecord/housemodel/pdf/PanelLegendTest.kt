package com.kurtz.homerecord.housemodel.pdf

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.LinkConfidence
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// AC-C43: the legend has one row per slot, a header with panel name and service size
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@AcceptanceCriterion("AC-C43")
class PanelLegendTest {
    private val words = LegendWords(
        open = "Open", amps = { "$it A" }, withSlot = { "with $it" }, feeds = { "feeds $it" },
        service = { "$it A service" }, slots = { "$it slots" },
    )
    private val panel = Panel("p", "pi", 12, PanelLayout.NORTH_AMERICAN, 200, "Square D QO", null)
    private fun b(id: String, slots: List<Int>, label: String, amps: Int? = 20, at: Long = 1) =
        Breaker(id, "p", slots, slots.size, amps, BreakerType.STANDARD, label, LinkConfidence.UNKNOWN, at)

    private val breakers = listOf(
        b("range", listOf(1, 3), "Range", 40),
        b("fridge", listOf(4), "Refrigerator"),
        b("porch", listOf(7), "Porch lights", 15, at = 1),
        b("garage", listOf(7), "Garage door", 15, at = 2),
        b("blank", listOf(10), "", null),
    )

    @Test fun oneRowPerSlotInOrderWithOpenSlotsSaid() {
        val rows = PanelLegend.rows(panel, breakers, mapOf("fridge" to listOf("Kitchen", "Refrigerator")), words)
        assertThat(rows.map { it.slot }).isEqualTo((1..12).toList())
        assertThat(rows[0].text).isEqualTo("Range · 40 A")
        assertThat(rows[2].text).isEqualTo("Range · 40 A (with 1)")
        assertThat(rows[3].text).isEqualTo("Refrigerator · 20 A · feeds Kitchen, Refrigerator")
        assertThat(rows[6].text).isEqualTo("Porch lights · 15 A / Garage door · 15 A")
        assertThat(rows[9].text).isEqualTo("—")
        assertThat(rows[1].open).isTrue()
        assertThat(rows[1].text).isEqualTo("Open")
        assertThat(rows.count { it.open }).isEqualTo(7)
    }

    @Test fun headerSaysServiceSizeBrandAndSlots() {
        val writer = PanelLegendPdfWriter(words)
        assertThat(writer.headerLine(panel)).isEqualTo("200 A service · Square D QO · 12 slots")
        assertThat(writer.headerLine(panel.copy(mainAmps = null, brand = null))).isEqualTo("12 slots")
    }

    // PdfDocument has no native backing on the JVM, so the page is drawn on a bitmap canvas.
    @Test fun aFullEightyFourSlotPageDrawsWithoutThrowing() {
        val bitmap = android.graphics.Bitmap.createBitmap(612, 792, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val writer = PanelLegendPdfWriter(words)
        val big = panel.copy(slotCount = 84)
        writer.drawPage(canvas, "Main panel", big, PanelLegend.rows(big, breakers, emptyMap(), words))
        val din = panel.copy(layout = PanelLayout.DIN)
        writer.drawPage(canvas, "Garage sub-panel", din, PanelLegend.rows(din, listOf(b("a", listOf(1, 2), "Dryer", 30)), emptyMap(), words))
    }
}
