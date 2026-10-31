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

    @Test fun aTapOrAFlatDragDrawsNothing() {
        assertThat(PdfMarkSketch.finish(PdfTool.Pen, 0, listOf(a))).isEqualTo(PdfSketchResult.Nothing)
        assertThat(PdfMarkSketch.finish(PdfTool.Arrow, 0, listOf(a, a))).isEqualTo(PdfSketchResult.Nothing)
        assertThat(PdfMarkSketch.finish(PdfTool.Highlight, 0, listOf(a, NormPoint(0.9f, a.y)))).isEqualTo(PdfSketchResult.Nothing)
        assertThat(PdfMarkSketch.finish(PdfTool.Rectangle, 0, emptyList())).isEqualTo(PdfSketchResult.Nothing)
    }
}
