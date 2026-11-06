package com.kurtz.homerecord.housemodel.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeReportTest {
    private val words = ReportWords(
        title = "Home report", madeOn = { "Made $it" }, counts = { r, i -> "$r rooms · $i items" },
        roomsAndItems = "Rooms and items", noItems = "No items yet", shutoffs = "Shutoffs", noShutoffs = "No shutoffs recorded yet",
        separator = " · ", serial = { "Serial $it" }, inService = { "In service $it" },
        warrantyUnknown = "Warranty unknown", warrantyActive = { "Warranty until $it" },
        warrantyExpiring = { "Warranty ends $it" }, warrantyExpired = { "Warranty ended $it" },
        scaleFeet = { "$it ft" }, page = { "Page $it" },
    )
    private val legendWords = LegendWords("Open", { "$it A" }, { "with $it" }, { "feeds $it" }, { "$it A service" }, { "$it slots" })

    private fun item(name: String, warranty: WarrantyStatus = WarrantyStatus.Unknown) =
        ReportItem(name, "Water heater", "Rheem", "XE50T10H45U0", "Q4521", "2021-05-03", warranty)

    private fun rect(name: String, x: Int, w: Int) = PlanRoom(name, name, points = listOf(Pt(x, 0), Pt(x + w, 0), Pt(x + w, 120), Pt(x, 120)))

    // AC-H13
    @Test fun roomsGoFloorByFloorThenPseudoRoomsThenItemsWithNoRoom() {
        val rooms = listOf(
            ReportRoomRef("k", "Kitchen", "Main floor", 1), ReportRoomRef("b", "Bedroom", "Upper floor", 2),
            ReportRoomRef("u", "Utility room", "Basement", 0), ReportRoomRef("d", "Dining room", "Main floor", 1),
            ReportRoomRef("ext", "Exterior", null, 0), ReportRoomRef("whole", "Whole house", null, 1),
        )
        val items = listOf(
            ReportItemRef("k", item("Refrigerator")), ReportItemRef("k", item("dishwasher")), ReportItemRef("u", item("Furnace")),
            ReportItemRef("whole", item("Smoke detectors")), ReportItemRef(null, item("Spare filter")), ReportItemRef("gone", item("Old fan")),
        )
        val out = HomeReport.rooms(rooms, items, "No room")
        assertThat(out.map { it.name }).containsExactly("Utility room", "Dining room", "Kitchen", "Bedroom", "Whole house", "No room").inOrder()
        assertThat(out.first { it.name == "Kitchen" }.items.map { it.name }).containsExactly("dishwasher", "Refrigerator").inOrder()
        // Empty real rooms print; an empty pseudo-room (Exterior) does not.
        assertThat(out.first { it.name == "Dining room" }.items).isEmpty()
        assertThat(out.last().items.map { it.name }).containsExactly("Old fan", "Spare filter").inOrder()
    }

    // AC-H13
    @Test fun itemLinesCarryMakeModelSerialInServiceAndWarrantyStatus() {
        assertThat(HomeReport.detailLine(item("Water heater"), words))
            .isEqualTo("Water heater · Rheem XE50T10H45U0 · Serial Q4521 · In service 2021-05-03")
        assertThat(HomeReport.detailLine(ReportItem("Lamp", "", null, " ", null, null, WarrantyStatus.Unknown), words)).isEmpty()
        assertThat(HomeReport.warrantyText(WarrantyStatus.Active("2031-05-03"), words)).isEqualTo("Warranty until 2031-05-03")
        assertThat(HomeReport.warrantyText(WarrantyStatus.Expiring("2026-12-01", 25), words)).isEqualTo("Warranty ends 2026-12-01")
        assertThat(HomeReport.warrantyText(WarrantyStatus.Expired("2024-01-01"), words)).isEqualTo("Warranty ended 2024-01-01")
        assertThat(HomeReport.warrantyText(WarrantyStatus.Unknown, words)).isEqualTo("Warranty unknown")
    }

    // AC-H13: every floor's blueprint and every room's items with warranty status; a 40-item home in under 5 s.
    @Test fun aFortyItemHomePrintsEveryFloorAndEveryItemInUnderFiveSeconds() {
        val floors = listOf(
            PdfFloor("Basement", listOf(rect("Utility room", 0, 200))),
            PdfFloor("Main floor", listOf(rect("Kitchen", 0, 168), rect("Living room", 168, 180))),
            PdfFloor("Upper floor", listOf(rect("Bedroom", 0, 150))),
        )
        val roomNames = listOf("Utility room", "Kitchen", "Living room", "Bedroom")
        val items = (1..40).map { n ->
            val w = when (n % 4) { 0 -> WarrantyStatus.Active("2030-01-0${n % 9 + 1}"); 1 -> WarrantyStatus.Expired("2024-02-01"); 2 -> WarrantyStatus.Expiring("2026-12-01", 25); else -> WarrantyStatus.Unknown }
            ReportItemRef(roomNames[n % 4], item("Appliance $n", w))
        }
        val rooms = HomeReport.rooms(roomNames.mapIndexed { i, r -> ReportRoomRef(r, r, floors[minOf(i, 2)].name, minOf(i, 2)) }, items, "No room")
        val data = HomeReportData(
            homeName = "Maple House", address = "14 Maple St, Springfield", houseType = "Two-story",
            floors = floors, rooms = rooms,
            shutoffs = listOf(ReportShutoff("Water main shutoff", "Utility room", "Turn the red handle a quarter turn clockwise.", "/photos/main.jpg")),
            panels = listOf(ReportPanel("Breaker panel", "200 A service · 40 slots", 2, (1..40).map { LegendRow(it, if (it % 3 == 0) "Open" else "Kitchen · 20 A", it % 3 == 0) })),
        )
        val photo = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        val pages = RecordingPages()
        val writer = HomeReportPdfWriter(words, legendWords, loadPhoto = { path, _ -> if (path == "/photos/main.jpg") photo else null })

        val started = System.nanoTime()
        val count = writer.draw(data, "2026-11-06", pages)
        val ms = (System.nanoTime() - started) / 1_000_000

        assertThat(ms).isLessThan(5_000)
        assertThat(count).isEqualTo(pages.started)
        assertThat(pages.finished).isEqualTo(pages.started)
        val text = pages.texts
        assertThat(text).containsAtLeast("Maple House", "14 Maple St, Springfield", "Two-story", "Made 2026-11-06", "4 rooms · 40 items")
        floors.forEach { assertThat(text).contains(it.name) }
        roomNames.forEach { r -> assertThat(text.any { it.startsWith(r) }).isTrue() }
        (1..40).forEach { assertThat(text).contains("Appliance $it") }
        assertThat(text).containsAtLeast("Warranty until 2030-01-01", "Warranty ended 2024-02-01", "Warranty ends 2026-12-01", "Warranty unknown")
        assertThat(text).containsAtLeast("Water main shutoff · Utility room", "Breaker panel", "200 A service · 40 slots")
        assertThat(pages.bitmaps).isEqualTo(1)
        // 40 items need more than one page of rooms; every page is numbered.
        assertThat(count).isAtLeast(1 + floors.size + 2 + 1 + 1)
        (1..count).forEach { assertThat(text).contains("Page $it") }
    }

    // AC-H13
    @Test fun aHomeWithNoShutoffsSaysSoAndAMissingPhotoIsSkipped() {
        val data = HomeReportData("Maple House", null, "", emptyList(), emptyList(), listOf(ReportShutoff("Gas shutoff", "", "", "/missing.jpg")), emptyList())
        val pages = RecordingPages()
        HomeReportPdfWriter(words, legendWords, loadPhoto = { _, _ -> null }).draw(data, "2026-11-06", pages)
        assertThat(pages.texts).contains("Gas shutoff")
        assertThat(pages.bitmaps).isEqualTo(0)
        val empty = RecordingPages()
        HomeReportPdfWriter(words, legendWords).draw(data.copy(shutoffs = emptyList()), "2026-11-06", empty)
        assertThat(empty.texts).contains("No shutoffs recorded yet")
    }

    private class RecordingPages : ReportPages {
        val texts = mutableListOf<String>()
        var started = 0
        var finished = 0
        var bitmaps = 0
        private val page = Bitmap.createBitmap(612, 792, Bitmap.Config.ARGB_8888)

        override fun start(): Canvas {
            started++
            return object : Canvas(page) {
                override fun drawText(text: String, x: Float, y: Float, paint: Paint) { texts += text; super.drawText(text, x, y, paint) }
                override fun drawBitmap(bitmap: Bitmap, src: android.graphics.Rect?, dst: android.graphics.RectF, paint: Paint?) { bitmaps++; super.drawBitmap(bitmap, src, dst, paint) }
            }
        }

        override fun finish() { finished++ }
    }
}
