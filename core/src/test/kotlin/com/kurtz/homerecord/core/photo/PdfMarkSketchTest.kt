package com.kurtz.homerecord.core.photo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

// AC-F21 (markup on a PDF page: pen, arrow, rectangle, text, highlight)
class PdfMarkSketchTest {
    private val a = NormPoint(0.6f, 0.7f)
    private val b = NormPoint(0.2f, 0.1f)

    @Test fun eachToolTurnsAGestureIntoItsMarkOnThatPage() {
        assertThat(PdfMarkSketch.finish(PdfTool.Pen, 2, listOf(a, b))).isEqualTo(PdfSketchResult.Mark(PdfMark.Pen(2, listOf(a, b))))
        assertThat(PdfMarkSketch.finish(PdfTool.Arrow, 0, listOf(a, NormPoint(0.4f, 0.4f), b))).isEqualTo(PdfSketchResult.Mark(PdfMark.Arrow(0, a, b)))
        assertThat(PdfMarkSketch.finish(PdfTool.Text, 1, listOf(a, b))).isEqualTo(PdfSketchResult.AskText(a))
    }

    @Test fun boxesAndHighlightsKeepTheirCornersInOrderWhicheverWayTheyWereDragged() {
        val tl = NormPoint(0.2f, 0.1f)
        val br = NormPoint(0.6f, 0.7f)
        assertThat(PdfMarkSketch.finish(PdfTool.Rectangle, 0, listOf(a, b))).isEqualTo(PdfSketchResult.Mark(PdfMark.Rect(0, tl, br)))
        assertThat(PdfMarkSketch.finish(PdfTool.Highlight, 0, listOf(NormPoint(0.2f, 0.7f), NormPoint(0.6f, 0.1f))))
            .isEqualTo(PdfSketchResult.Mark(PdfMark.Highlight(0, tl, br)))
    }

    @Test fun aTapDrawsNothing() {
        assertThat(PdfMarkSketch.finish(PdfTool.Pen, 0, listOf(a))).isEqualTo(PdfSketchResult.Nothing)
        assertThat(PdfMarkSketch.finish(PdfTool.Arrow, 0, listOf(a, a))).isEqualTo(PdfSketchResult.Nothing)
        assertThat(PdfMarkSketch.finish(PdfTool.Highlight, 0, listOf(a, NormPoint(a.x + 0.01f, a.y + 0.005f)))).isEqualTo(PdfSketchResult.Nothing)
        assertThat(PdfMarkSketch.finish(PdfTool.Rectangle, 0, listOf(a, a))).isEqualTo(PdfSketchResult.Nothing)
        assertThat(PdfMarkSketch.finish(PdfTool.Rectangle, 0, emptyList())).isEqualTo(PdfSketchResult.Nothing)
    }

    @Test fun aStraightStrokeHighlightsOrBoxesAThinStripAlongIt() {
        // a highlighter run along a line of text: full length, MIN_EXTENT tall, centred on the stroke
        val line = PdfMarkSketch.finish(PdfTool.Highlight, 0, listOf(NormPoint(0.1f, 0.5f), NormPoint(0.9f, 0.5f)))
        val hl = (line as PdfSketchResult.Mark).mark as PdfMark.Highlight
        assertThat(hl.topLeft.x).isEqualTo(0.1f)
        assertThat(hl.bottomRight.x).isEqualTo(0.9f)
        assertThat(hl.topLeft.y).isWithin(1e-6f).of(0.5f - PdfMarkSketch.MIN_EXTENT / 2)
        assertThat(hl.bottomRight.y).isWithin(1e-6f).of(0.5f + PdfMarkSketch.MIN_EXTENT / 2)
        // a straight drag down with the box tool, at the page edge: kept on the page
        val down = PdfMarkSketch.finish(PdfTool.Rectangle, 3, listOf(NormPoint(1f, 0.2f), NormPoint(1f, 0.8f)))
        val box = (down as PdfSketchResult.Mark).mark as PdfMark.Rect
        assertThat(box.page).isEqualTo(3)
        assertThat(box.bottomRight.x).isEqualTo(1f)
        assertThat(box.topLeft.x).isWithin(1e-6f).of(1f - PdfMarkSketch.MIN_EXTENT)
        assertThat(box.topLeft.y).isEqualTo(0.2f)
        assertThat(box.bottomRight.y).isEqualTo(0.8f)
    }
}
