package com.kurtz.homerecord.housemodel.pdf

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.pdf.PdfDocument
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import java.io.OutputStream

/** One floor of the export: its name and placed rooms. */
data class PdfFloor(val name: String, val rooms: List<PlanRoom>)

/** Writes one page per floor, to scale, with the home name, floor name, paper scale and a scale bar (AC-C16). */
class BlueprintPdfWriter(private val spec: PdfPageSpec = PdfPageSpec()) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.5f; color = BLACK }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = LIGHT }
    private val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; color = BLACK; isFakeBoldText = true }
    private val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; color = GRAY }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; color = BLACK; textAlign = Paint.Align.CENTER }

    /** Floors with no placed rooms still get a page so the export matches the House tab. */
    fun write(homeName: String, floors: List<PdfFloor>, out: OutputStream) {
        val doc = PdfDocument()
        try {
            floors.forEachIndexed { index, floor ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(spec.widthPt, spec.heightPt, index + 1).create())
                drawPage(page.canvas, homeName, floor)
                doc.finishPage(page)
            }
            if (floors.isEmpty()) {
                val page = doc.startPage(PdfDocument.PageInfo.Builder(spec.widthPt, spec.heightPt, 1).create())
                drawPage(page.canvas, homeName, PdfFloor("", emptyList()))
                doc.finishPage(page)
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    internal fun drawPage(c: Canvas, homeName: String, floor: PdfFloor) {
        c.drawText(homeName, spec.marginPt, spec.marginPt + 20f, title)
        c.drawText(floor.name, spec.marginPt, spec.marginPt + 40f, sub)
        val layout = BlueprintPageLayout.forRooms(floor.rooms, spec) ?: return
        for (room in floor.rooms) {
            val path = Path()
            room.points.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(layout.x(p.x), layout.y(p.y)) else path.lineTo(layout.x(p.x), layout.y(p.y))
            }
            path.close()
            c.drawPath(path, fill)
            c.drawPath(path, line)
            val cx = layout.x((room.minX + room.maxX) / 2)
            val cy = layout.y((room.minY + room.maxY) / 2)
            c.drawText(room.name, cx, cy, label)
        }
        val barY = spec.heightPt - spec.marginPt - 24f
        val x0 = spec.marginPt
        c.drawLine(x0, barY, x0 + layout.scaleBarLengthPt, barY, line)
        c.drawLine(x0, barY - 4f, x0, barY + 4f, line)
        c.drawLine(x0 + layout.scaleBarLengthPt, barY - 4f, x0 + layout.scaleBarLengthPt, barY + 4f, line)
        c.drawText("${layout.scaleBarFeet} ft", x0, barY + 16f, sub)
        c.drawText(layout.scale.label, x0 + layout.scaleBarLengthPt + 12f, barY + 4f, sub)
    }

    private companion object {
        const val BLACK = 0xFF000000.toInt()
        const val GRAY = 0xFF555555.toInt()
        const val LIGHT = 0xFFF2F2F2.toInt()
    }
}
