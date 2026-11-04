package com.kurtz.homerecord.core.changeorder

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.log.LogPdfMeasure
import com.kurtz.homerecord.core.log.LogPdfOp
import com.kurtz.homerecord.core.log.LogPdfPage
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

/** The change order PDF's layout (AC-F39), measured with a fixed width per character. */
class ChangeOrderPdfLayoutTest {
    private val measure = LogPdfMeasure { text, size, _ -> text.length * size * 0.5f }
    private val page = LogPdfPage()
    private val layout = ChangeOrderPdfLayout(measure, { n, m -> "Page $n of $m" }, page)

    private val signed = ChangeOrderPdfContent(
        company = "Ortiz Electric",
        companyLine = "(610) 555-0142 · office@ortizelectric.com",
        logoPath = "/files/media/logo.png",
        title = "Change order: Second outlet on the island",
        job = "Hale kitchen",
        jobLine = "Dana Hale · 14 Maple Ave, Wayne, PA",
        facts = listOf("Date: Nov 3, 2026", "Status: Signed"),
        descriptionTitle = "Description",
        description = "Add a GFCI outlet on the island's sink side, fed from the existing small-appliance circuit.",
        linesTitle = "Line items",
        heads = ChangeOrderPdfRow("Item", "Qty", "Price each", "Amount"),
        lines = listOf(
            ChangeOrderPdfRow("GFCI outlet, box and cover", "1", "$185.00", "$185.00"),
            ChangeOrderPdfRow("Labor", "1.5", "$96.00", "$144.00"),
        ),
        totalLabel = "Total",
        total = "$329.00",
        photosTitle = "Photos",
        photos = listOf("/p/island-1.jpg", "/p/island-2.jpg"),
        signatureTitle = "Signature",
        signaturePath = "/files/media/sig.png",
        signedLine = "Signed by Dana Hale, Nov 3, 2026, 2:15 PM",
    )

    private fun texts(ops: List<LogPdfOp>) = ops.filterIsInstance<LogPdfOp.Text>().map { it.text }

    @AcceptanceCriterion("AC-F39")
    @Test fun aSignedOrderPrintsCompanyJobDescriptionLinesTotalPhotosAndSignatureWithTime() {
        val pages = layout.layout(signed)
        assertThat(pages).hasSize(1)
        val all = texts(pages[0])
        assertThat(all).containsAtLeast(
            "Ortiz Electric", "Change order: Second outlet on the island", "Hale kitchen", "Dana Hale · 14 Maple Ave, Wayne, PA",
            "Description", "Line items", "GFCI outlet, box and cover", "$185.00", "Labor", "1.5", "$144.00", "Total", "$329.00",
            "Photos", "Signature", "Signed by Dana Hale, Nov 3, 2026, 2:15 PM", "Page 1 of 1",
        ).inOrder()
        val images = pages[0].filterIsInstance<LogPdfOp.Image>()
        assertThat(images.map { it.path }).containsExactly("/files/media/logo.png", "/p/island-1.jpg", "/p/island-2.jpg", "/files/media/sig.png").inOrder()
        // logo and signature are drawn whole; photos fill their cells
        assertThat(images.filter { it.fit }.map { it.path }).containsExactly("/files/media/logo.png", "/files/media/sig.png")
        // the signature sits below the photos, and the signer line below the signature
        val sig = images.last()
        assertThat(sig.top).isGreaterThan(images[2].bottom)
        val signer = pages[0].filterIsInstance<LogPdfOp.Text>().first { it.text.startsWith("Signed by") }
        assertThat(signer.y).isGreaterThan(sig.bottom)
    }

    @AcceptanceCriterion("AC-F39")
    @Test fun numbersAreRightAlignedOnTheirRowAndTheTotalLinesUpWithTheAmounts() {
        val ops = layout.layout(signed)[0].filterIsInstance<LogPdfOp.Text>()
        val right = page.widthPt - page.marginPt
        val amount = ops.first { it.text == "$144.00" }
        val total = ops.first { it.text == "$329.00" }
        assertThat(amount.x + measure.width(amount.text, amount.size, amount.bold)).isWithin(0.01f).of(right)
        assertThat(total.x + measure.width(total.text, total.size, total.bold)).isWithin(0.01f).of(right)
        val labor = ops.first { it.text == "Labor" }
        assertThat(ops.first { it.text == "1.5" }.y).isEqualTo(labor.y)
        assertThat(amount.y).isEqualTo(labor.y)
        assertThat(total.y).isGreaterThan(labor.y)
    }

    @Test fun anUnsignedOrderGetsABlankLineToSignOnAndNoDescriptionSectionWhenBlank() {
        val draft = signed.copy(signaturePath = null, signedLine = "Not signed yet", description = " ", photos = emptyList(), logoPath = null)
        val ops = layout.layout(draft).single()
        assertThat(texts(ops)).containsNoneOf("Description", "Photos")
        assertThat(texts(ops)).containsAtLeast("Signature", "X", "Not signed yet").inOrder()
        assertThat(ops.filterIsInstance<LogPdfOp.Image>()).isEmpty()
        val x = ops.filterIsInstance<LogPdfOp.Text>().first { it.text == "X" }
        assertThat(ops.filterIsInstance<LogPdfOp.Rule>().any { it.y >= x.y && it.x2 - it.x1 > 200f }).isTrue()
    }

    @AcceptanceCriterion("AC-F39")
    @Test fun manyLinesRunOnToMorePagesRowsStayWholeAndTheSignatureBlockIsNotSplit() {
        val many = signed.copy(
            lines = (1..60).map { ChangeOrderPdfRow("Line $it: run conduit along the basement joists to the new subpanel location", "2", "$40.00", "$80.00") },
        )
        val pages = layout.layout(many)
        assertThat(pages.size).isAtLeast(3)
        pages.forEachIndexed { i, ops ->
            assertThat(texts(ops)).contains("Page ${i + 1} of ${pages.size}")
            ops.forEach { op ->
                when (op) {
                    is LogPdfOp.Text -> if (!op.text.startsWith("Page ")) assertThat(op.y).isAtMost(page.bottom)
                    is LogPdfOp.Image -> assertThat(op.bottom).isAtMost(page.bottom)
                    is LogPdfOp.Rule -> assertThat(op.y).isAtMost(page.bottom)
                }
            }
        }
        // every row's amount sits on the same page as its first item line
        pages.forEach { ops ->
            val rows = ops.filterIsInstance<LogPdfOp.Text>().filter { Regex("^Line \\d+:").containsMatchIn(it.text) }
            val amounts = ops.filterIsInstance<LogPdfOp.Text>().filter { it.text == "$80.00" }
            assertThat(amounts.map { it.y }).containsExactlyElementsIn(rows.map { it.y })
        }
        assertThat(pages.flatten().filterIsInstance<LogPdfOp.Text>().count { it.text == "$80.00" }).isEqualTo(60)
        val sigPage = pages.indexOfFirst { ops -> ops.any { it is LogPdfOp.Image && it.path == "/files/media/sig.png" } }
        val signerPage = pages.indexOfFirst { ops -> texts(ops).any { it.startsWith("Signed by") } }
        val headingPage = pages.indexOfLast { ops -> texts(ops).contains("Signature") }
        assertThat(sigPage).isEqualTo(signerPage)
        assertThat(headingPage).isEqualTo(sigPage)
    }
}
