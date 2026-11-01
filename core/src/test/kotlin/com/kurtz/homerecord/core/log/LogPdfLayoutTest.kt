package com.kurtz.homerecord.core.log

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

/** The daily log PDF's page layout (AC-F33), measured with a fixed width per character. */
class LogPdfLayoutTest {
    // half the type size per character, like a narrow sans
    private val measure = LogPdfMeasure { text, size, _ -> text.length * size * 0.5f }
    private val page = LogPdfPage()
    private val layout = LogPdfLayout(measure, { n, m -> "Page $n of $m" }, page)

    private val content = LogPdfContent(
        company = "Ortiz Electric",
        companyLine = "(610) 555-0142 · office@ortizelectric.com",
        logoPath = "/files/media/logo.png",
        title = "Daily log, Friday, October 30, 2026",
        job = "Hale kitchen",
        jobLine = "Dana Hale · 14 Maple Ave, Wayne, PA",
        sections = listOf(
            LogPdfSection("Crew on site", listOf("Maria Soto, 8h 30m", "Luis Ortiz, 6h")),
            LogPdfSection("Work done", listOf("Set the base cabinets and shimmed the sink base level with the window wall.")),
        ),
        photosTitle = "Photos",
        photos = listOf("/a.jpg", "/b.jpg", "/c.jpg", "/d.jpg"),
    )

    private fun texts(ops: List<LogPdfOp>) = ops.filterIsInstance<LogPdfOp.Text>()

    @AcceptanceCriterion("AC-F33")
    @Test fun firstPageHasTheLogoCompanyJobDateAndSections() {
        val pages = layout.layout(content)
        assertThat(pages).hasSize(1)
        val first = pages[0]
        val logo = first.filterIsInstance<LogPdfOp.Image>().first()
        assertThat(logo.path).isEqualTo("/files/media/logo.png")
        assertThat(logo.left).isEqualTo(page.marginPt)
        val words = texts(first).map { it.text }
        assertThat(words).containsAtLeast(
            "Ortiz Electric", "Daily log, Friday, October 30, 2026", "Hale kitchen", "Crew on site", "Maria Soto, 8h 30m", "Work done", "Photos",
        ).inOrder()
        // the company name sits beside the logo, not under it
        val name = texts(first).first { it.text == "Ortiz Electric" }
        assertThat(name.x).isGreaterThan(logo.right)
        assertThat(name.bold).isTrue()
        assertThat(words.last()).isEqualTo("Page 1 of 1")
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun withoutALogoTheCompanyNameStartsAtTheMargin() {
        val first = layout.layout(content.copy(logoPath = null))[0]
        assertThat(first.filterIsInstance<LogPdfOp.Image>().map { it.path }).containsNoneOf("/files/media/logo.png", null)
        assertThat(texts(first).first { it.text == "Ortiz Electric" }.x).isEqualTo(page.marginPt)
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun photosFillAThreeAcrossGridInsideTheMargins() {
        val photos = layout.layout(content)[0].filterIsInstance<LogPdfOp.Image>().drop(1)
        assertThat(photos.map { it.path }).containsExactly("/a.jpg", "/b.jpg", "/c.jpg", "/d.jpg").inOrder()
        assertThat(photos.take(3).map { it.top }.distinct()).hasSize(1)
        assertThat(photos[3].top).isGreaterThan(photos[0].bottom)
        assertThat(photos[3].left).isEqualTo(photos[0].left)
        photos.forEach {
            assertThat(it.left).isAtLeast(page.marginPt)
            assertThat(it.right).isAtMost(page.widthPt - page.marginPt + 0.01f)
            assertThat((it.right - it.left) / (it.bottom - it.top)).isWithin(0.02f).of(4f / 3f)
        }
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun aLongLogRunsOnToMorePagesAndNothingCrossesTheFooter() {
        val long = content.copy(
            sections = (1..12).map { n -> LogPdfSection("Section $n", (1..8).map { "Line $n.$it about the day's work on site, long enough to wrap onto a second line of the page." }) },
            photos = (1..14).map { "/p$it.jpg" },
        )
        val pages = layout.layout(long)
        assertThat(pages.size).isAtLeast(3)
        pages.forEachIndexed { i, ops ->
            val footer = texts(ops).last()
            assertThat(footer.text).isEqualTo("Page ${i + 1} of ${pages.size}")
            ops.filterNot { it === footer }.forEach { op ->
                when (op) {
                    is LogPdfOp.Text -> assertThat(op.y).isAtMost(page.bottom)
                    is LogPdfOp.Image -> assertThat(op.bottom).isAtMost(page.bottom)
                    is LogPdfOp.Rule -> assertThat(op.y).isAtMost(page.bottom)
                }
            }
            // no section title is the last thing on a page (a page of photos only has no text above its footer)
            texts(ops).dropLast(1).lastOrNull()?.let { assertThat(it.text).doesNotMatch("Section \\d+") }
        }
        // the header and logo only on the first page
        assertThat(texts(pages[1]).map { it.text }).doesNotContain("Ortiz Electric")
        assertThat(pages.flatten().filterIsInstance<LogPdfOp.Image>().count { it.path.startsWith("/p") }).isEqualTo(14)
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun wrapKeepsLinesInsideTheWidthAndBreaksAWordTooLongForOne() {
        val lines = layout.wrap("Ran 3/4 conduit https://example.com/a-very-long-link-that-cannot-fit-on-one-line-at-all", 10f, false, 100f)
        assertThat(lines.first()).isEqualTo("Ran 3/4 conduit")
        lines.forEach { assertThat(measure.width(it, 10f, false)).isAtMost(100f) }
        assertThat(lines.joinToString("").replace(" ", "")).isEqualTo("Ran3/4conduithttps://example.com/a-very-long-link-that-cannot-fit-on-one-line-at-all")
        assertThat(layout.wrap("Line one\nLine two", 10f, false, 400f)).containsExactly("Line one", "Line two").inOrder()
    }
}
