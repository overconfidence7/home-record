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
    /** [points] are one gesture's touch points as fractions of [page]; boxes are stored with their corners in order. */
    fun finish(tool: PdfTool, page: Int, points: List<NormPoint>): PdfSketchResult {
        val first = points.firstOrNull() ?: return PdfSketchResult.Nothing
        val last = points.last()
        return when (tool) {
            PdfTool.Text -> PdfSketchResult.AskText(first)
            PdfTool.Pen -> if (points.size >= 2) PdfSketchResult.Mark(PdfMark.Pen(page, points)) else PdfSketchResult.Nothing
            PdfTool.Arrow -> if (first == last) PdfSketchResult.Nothing else PdfSketchResult.Mark(PdfMark.Arrow(page, first, last))
            PdfTool.Rectangle, PdfTool.Highlight -> {
                val tl = NormPoint(minOf(first.x, last.x), minOf(first.y, last.y))
                val br = NormPoint(maxOf(first.x, last.x), maxOf(first.y, last.y))
                when {
                    tl.x == br.x || tl.y == br.y -> PdfSketchResult.Nothing
                    tool == PdfTool.Rectangle -> PdfSketchResult.Mark(PdfMark.Rect(page, tl, br))
                    else -> PdfSketchResult.Mark(PdfMark.Highlight(page, tl, br))
                }
            }
        }
    }
}
