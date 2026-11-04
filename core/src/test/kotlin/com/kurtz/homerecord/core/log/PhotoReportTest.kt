package com.kurtz.homerecord.core.log

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

/** The photo report PDF (AC-F28): which photos go in, and four to a page with captions. */
class PhotoReportTest {
    private val zone = ZoneId.of("America/New_York")
    private val measure = LogPdfMeasure { text, size, _ -> text.length * size * 0.5f }
    private val page = LogPdfPage()
    private val layout = PhotoReportLayout(measure, { n, m -> "Page $n of $m" }, page)

    private fun at(day: String, hour: Int) = LocalDate.parse(day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val photos = listOf(
        ReportPhoto("p3", "j1", at("2026-10-28", 15), "/m/p3.jpg", roomId = "r1", phase = "Rough-in", note = "Box fill checked"),
        ReportPhoto("p1", "j1", at("2026-10-26", 0), "/m/p1.jpg"),
        ReportPhoto("p2", "j1", at("2026-10-27", 9), "/m/p2.jpg", roomId = "gone"),
        ReportPhoto("old", "j1", at("2026-10-25", 23), "/m/old.jpg"),
        ReportPhoto("late", "j1", at("2026-11-02", 0), "/m/late.jpg"),
        ReportPhoto("other", "j2", at("2026-10-28", 9), "/m/other.jpg"),
        ReportPhoto("del", "j1", at("2026-10-28", 9), "/m/del.jpg", deleted = true),
        ReportPhoto("away", "j1", at("2026-10-29", 9), ""),
        ReportPhoto("lost", "j1", at("2026-10-29", 10), "/m/lost.jpg"),
    )
    private val files = setOf("/m/p1.jpg", "/m/p2.jpg", "/m/p3.jpg", "/m/old.jpg", "/m/late.jpg", "/m/other.jpg", "/m/del.jpg")

    @AcceptanceCriterion("AC-F28")
    @Test fun picksTheJobsPhotosInTheDatesOldestFirstAndCountsTheOnesNotHere() {
        val r = PhotoReportPicker.pick(photos, "j1", LocalDate.parse("2026-10-26"), LocalDate.parse("2026-11-01"), zone, mapOf("r1" to "Kitchen"), files::contains)
        assertThat(r.photos.map { it.path }).containsExactly("/m/p1.jpg", "/m/p2.jpg", "/m/p3.jpg").inOrder()
        assertThat(r.leftOut).isEqualTo(2)
        val p3 = r.photos.last()
        assertThat(p3.room).isEqualTo("Kitchen")
        assertThat(p3.phase).isEqualTo("Rough-in")
        assertThat(p3.note).isEqualTo("Box fill checked")
        assertThat(p3.day).isEqualTo(LocalDate.parse("2026-10-28"))
        // a deleted room drops out of the caption
        assertThat(r.photos[1].room).isNull()
    }

    @AcceptanceCriterion("AC-F28")
    @Test fun countsEachJobsPhotosInTheDates() {
        val counts = PhotoReportPicker.countByJob(photos, LocalDate.parse("2026-10-26"), LocalDate.parse("2026-11-01"), zone)
        assertThat(counts).containsExactly("j1", 5, "j2", 1)
    }

    private fun content(n: Int) = PhotoReportContent(
        company = "Ortiz Electric", companyLine = "(610) 555-0142", title = "Photo report, Oct 26 – Nov 1, 2026",
        job = "Hale kitchen", jobLine = "Dana Hale · 14 Maple Ave", facts = listOf("$n photos"),
        photos = (1..n).map { PhotoReportCell("/m/$it.jpg", listOf("Kitchen · Rough-in", "Oct 28, 2026, 3:00 PM", "Note $it")) },
    )

    @AcceptanceCriterion("AC-F28")
    @Test fun putsAtMostFourPhotosOnAPageWithTheirCaptions() {
        val pages = layout.layout(content(9))
        assertThat(pages).hasSize(3)
        assertThat(pages.map { p -> p.filterIsInstance<LogPdfOp.Image>().size }).containsExactly(4, 4, 1).inOrder()
        val words = pages[0].filterIsInstance<LogPdfOp.Text>().map { it.text }
        assertThat(words).containsAtLeast("Ortiz Electric", "Photo report, Oct 26 – Nov 1, 2026", "Hale kitchen", "9 photos", "Note 1", "Note 4", "Page 1 of 3")
        assertThat(pages[2].filterIsInstance<LogPdfOp.Text>().map { it.text }).containsAtLeast("Hale kitchen · Photo report, Oct 26 – Nov 1, 2026", "Note 9", "Page 3 of 3")
    }

    @AcceptanceCriterion("AC-F28")
    @Test fun nothingButTheFooterGoesBelowTheBottomAndPhotosDoNotOverlap() {
        layout.layout(content(8)).forEach { ops ->
            val footer = ops.last()
            ops.dropLast(1).forEach { op ->
                when (op) {
                    is LogPdfOp.Text -> assertThat(op.y).isAtMost(page.bottom)
                    is LogPdfOp.Image -> assertThat(op.bottom).isAtMost(page.bottom)
                    is LogPdfOp.Rule -> assertThat(op.y).isAtMost(page.bottom)
                }
            }
            assertThat((footer as LogPdfOp.Text).text).startsWith("Page")
            val images = ops.filterIsInstance<LogPdfOp.Image>()
            images.forEachIndexed { i, a ->
                images.drop(i + 1).forEach { b ->
                    val overlap = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
                    assertThat(overlap).isFalse()
                }
                assertThat(a.right).isAtMost(page.widthPt - page.marginPt)
            }
        }
    }

    @AcceptanceCriterion("AC-F28")
    @Test fun aLongNoteIsCutToThreeLinesEndingInAnEllipsis() {
        val note = "Pulled the old knob and tube from the north wall and found two splices buried behind the plaster that need boxes"
        val lines = layout.caption(listOf("Kitchen · Rough-in", "Oct 28, 2026", note), 120f)
        assertThat(lines).hasSize(PhotoReportLayout.CAPTION_LINES)
        assertThat(lines.last()).endsWith("…")
        lines.forEach { assertThat(measure.width(it, PhotoReportLayout.SMALL, false)).isAtMost(120f) }
    }

    @Test fun noPhotosStillMakesOnePage() {
        val pages = layout.layout(content(0))
        assertThat(pages).hasSize(1)
        assertThat(pages[0].filterIsInstance<LogPdfOp.Image>()).isEmpty()
    }
}
