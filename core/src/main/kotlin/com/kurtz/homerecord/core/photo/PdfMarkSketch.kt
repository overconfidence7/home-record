package com.kurtz.homerecord.core.photo

/** The marks a PDF page takes (AC-F21). */
enum class PdfTool { Pen, Arrow, Rectangle, Text, Highlight }

/** What a finished gesture on a PDF page turns into; text needs its words typed first. */
sealed interface PdfSketchResult {
    data class Mark(val mark: PdfMark) : PdfSketchResult
    data class AskText(val at: NormPoint) : PdfSketchResult
    data object Nothing : PdfSketchResult
}

/**
 * Fixed mark colours as ARGB, shared by the editor and the PDF writer so the saved version looks like what was drawn:
 * a high-visibility red for lines and text, a see-through yellow for highlights. Not theme colours: a plan prints the
 * same in dark mode.
 */
object PdfMarkColors {
    const val INK: Int = 0xFFE53935.toInt()
    const val HIGHLIGHT: Int = 0x66FFEB3B
}

object PdfMarkSketch {
    /**
     * Smallest side of a box or highlight as a fraction of the page. A drag shorter than this both ways is a tap and
     * draws nothing; a straight stroke (a highlighter run along a line of text) keeps its length and gets this thickness.
     */
    const val MIN_EXTENT: Float = 0.02f

    /** The two ends in order, widened about their middle to [MIN_EXTENT] and kept on the page. */
    private fun span(a: Float, b: Float): Pair<Float, Float> {
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        if (hi - lo >= MIN_EXTENT) return lo to hi
        val start = ((lo + hi) / 2f - MIN_EXTENT / 2f).coerceIn(0f, 1f - MIN_EXTENT)
        return start to start + MIN_EXTENT
    }

    /** [points] are one gesture's touch points as fractions of [page]; boxes are stored with their corners in order. */
    fun finish(tool: PdfTool, page: Int, points: List<NormPoint>): PdfSketchResult {
        val first = points.firstOrNull() ?: return PdfSketchResult.Nothing
        val last = points.last()
        return when (tool) {
            PdfTool.Text -> PdfSketchResult.AskText(first)
            PdfTool.Pen -> if (points.size >= 2) PdfSketchResult.Mark(PdfMark.Pen(page, points)) else PdfSketchResult.Nothing
            PdfTool.Arrow -> if (first == last) PdfSketchResult.Nothing else PdfSketchResult.Mark(PdfMark.Arrow(page, first, last))
            PdfTool.Rectangle, PdfTool.Highlight -> {
                val w = kotlin.math.abs(first.x - last.x)
                val h = kotlin.math.abs(first.y - last.y)
                if (w < MIN_EXTENT && h < MIN_EXTENT) return PdfSketchResult.Nothing
                val (x0, x1) = span(first.x, last.x)
                val (y0, y1) = span(first.y, last.y)
                val tl = NormPoint(x0, y0)
                val br = NormPoint(x1, y1)
                if (tool == PdfTool.Rectangle) PdfSketchResult.Mark(PdfMark.Rect(page, tl, br))
                else PdfSketchResult.Mark(PdfMark.Highlight(page, tl, br))
            }
        }
    }
}
