package com.kurtz.homerecord.core.log

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test

/** The photo report PDF (AC-F28): which photos go in, four to a page, captions under each photo. */
class PhotoReportTest {
    private val zone = ZoneId.of("America/New_York")
    private val measure = LogPdfMeasure { text, size, _ -> text.length * size * 0.5f }
    private val page = LogPdfPage()
    private val layout = PhotoReportLayout(measure, { n, m -> "Page $n of $m" }, page)

    private fun at(day: Int, hour: Int) = ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun content(n: Int) = PhotoReportContent(
        company = "Ortiz Electric",
        companyLine = "(610) 555-0142 · office@ortizelectric.com",
        title = "Photo report, Oct 19 – Oct 25, 2026",
        job = "Hale kitchen",
        jobLine = "Dana Hale · 14 Maple Ave, Wayne, PA",
        photos = (1..n).map { PhotoReportEntry("/p$it.jpg", listOf("Oct ${18 + it}, 2026, 9:30 AM", "Kitchen · Rough-in", "Panel cover off")) },
    )

    private fun images(ops: List<LogPdfOp>) = ops.filterIsInstance<LogPdfOp.Image>()
    private fun texts(ops: List<LogPdfOp>) = ops.filterIsInstance<LogPdfOp.Text>()

    // AC-F28
    @AcceptanceCriterion("AC-F28")
    @Test fun picksTheJobsPhotosInTheRangeOldestFirstCountingWholeLocalDays() {
        val photos = listOf(
            ReportPhoto("c", "hale", at(21, 15), "/c.jpg"),
            ReportPhoto("a", "hale", at(19, 0), "/a.jpg"),
            ReportPhoto("other", "garage", at(20, 9), "/x.jpg"),
            ReportPhoto("late", "hale", at(25, 23), ""),
            ReportPhoto("before", "hale", at(18, 23), "/b.jpg"),
            ReportPhoto("after", "hale", at(26, 0), "/d.jpg"),
        )
        val picked = PhotoReportPicker.inRange(photos, "hale", LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), zone)
        assertThat(picked.map { it.id }).containsExactly("a", "c", "late").inOrder()
        assertThat(PhotoReportPicker.countsByJob(photos, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25), zone))
            .containsExactly("hale", 3, "garage", 1)
    }

    @AcceptanceCriterion("AC-F28")
    @Test fun neverMoreThanFourPhotosToAPageAndEveryPageIsNumbered() {
        val pages = layout.layout(content(9))
        assertThat(pages.map { images(it).size }).containsExactly(4, 4, 1).inOrder()
        assertThat(pages.map { texts(it).last().text }).containsExactly("Page 1 of 3", "Page 2 of 3", "Page 3 of 3").inOrder()
        assertThat(images(pages.flatten()).map { it.path }).containsExactlyElementsIn((1..9).map { "/p$it.jpg" }).inOrder()
        // later pages carry the job name at the top
        assertThat(texts(pages[1]).first().text).isEqualTo("Hale kitchen")
    }

    @AcceptanceCriterion("AC-F28")
    @Test fun photosAndCaptionsStayInsideTheMarginsAboveTheFooterTwoAcross() {
        val pages = layout.layout(content(8).copy(note = "2 photos are still on another phone and are not in this report."))
        pages.forEach { ops ->
            val pics = images(ops)
            pics.forEach {
                assertThat(it.left).isAtLeast(page.marginPt)
                assertThat(it.right).isAtMost(page.widthPt - page.marginPt)
                assertThat(it.bottom).isAtMost(page.bottom)
            }
            assertThat(pics.map { it.left }.distinct()).hasSize(2)
            assertThat(pics.map { it.top }.distinct()).hasSize(2)
            texts(ops).dropLast(1).forEach { assertThat(it.y).isAtMost(page.bottom) }
        }
        val first = texts(pages[0]).map { it.text }
        assertThat(first).containsAtLeast(
            "Ortiz Electric", "Photo report, Oct 19 – Oct 25, 2026", "Hale kitchen", "Oct 19, 2026, 9:30 AM", "Kitchen · Rough-in", "Panel cover off",
        ).inOrder()
    }

    @AcceptanceCriterion("AC-F28")
    @Test fun aCaptionSitsUnderItsPhotoAndALongNoteIsCutToThreeLines() {
        val long = "Moved the panel cover to the garage and left the breakers labelled for the inspector who comes on Monday morning before nine"
        val pages = layout.layout(content(1).copy(photos = listOf(PhotoReportEntry("/p.jpg", listOf("Oct 19, 2026, 9:30 AM", "", long)))))
        val pic = images(pages[0]).single()
        val caption = texts(pages[0]).filter { it.y > pic.bottom && it.size == PhotoReportLayout.CAPTION }
        assertThat(caption).hasSize(PhotoReportLayout.CAPTION_LINES)
        assertThat(caption.first().text).isEqualTo("Oct 19, 2026, 9:30 AM")
        assertThat(caption.first().bold).isTrue()
        assertThat(caption.last().text).endsWith("…")
        caption.forEach {
            assertThat(it.x).isEqualTo(pic.left)
            assertThat(measure.width(it.text, it.size, false)).isAtMost(pic.right - pic.left)
        }
    }

    @AcceptanceCriterion("AC-F28")
    @Test fun aTallHeaderShrinksPageOnesPhotosSoFourStillFit() {
        val tall = content(4).copy(jobLine = List(6) { "Dana Hale · 14 Maple Ave, Wayne, PA 19087" }.joinToString(" · "))
        val pages = layout.layout(tall)
        assertThat(pages).hasSize(1)
        assertThat(images(pages[0])).hasSize(4)
        images(pages[0]).forEach { assertThat(it.bottom).isAtMost(page.bottom) }
    }
}
