package com.kurtz.homerecord.housemodel.pdf

import com.kurtz.homerecord.housemodel.plan.PlanRoom

/** US Letter in PDF points (1/72 inch) with the margins and bands the blueprint page keeps clear. */
data class PdfPageSpec(
    val widthPt: Int = 612,
    val heightPt: Int = 792,
    val marginPt: Float = 36f,
    val headerPt: Float = 64f,
    val footerPt: Float = 56f,
)

/** The paper scale printed on the page, e.g. 1/4" = 1'. */
data class PaperScale(val label: String, val paperInchesPerFoot: Double) {
    val ptPerPlanInch: Double get() = paperInchesPerFoot * 72.0 / 12.0
}

/** Where one floor lands on a page: scale, the plan's origin on paper, and a scale bar of whole feet. */
data class BlueprintPageLayout(
    val scale: PaperScale,
    val ptPerPlanInch: Float,
    val originX: Float,
    val originY: Float,
    val scaleBarFeet: Int,
    val scaleBarLengthPt: Float,
) {
    fun x(planX: Int) = planX * ptPerPlanInch + originX
    fun y(planY: Int) = planY * ptPerPlanInch + originY

    companion object {
        /** Common drafting scales, largest first. */
        val SCALES = listOf(
            PaperScale("1/2\" = 1'", 0.5),
            PaperScale("3/8\" = 1'", 0.375),
            PaperScale("1/4\" = 1'", 0.25),
            PaperScale("3/16\" = 1'", 0.1875),
            PaperScale("1/8\" = 1'", 0.125),
            PaperScale("3/32\" = 1'", 0.09375),
            PaperScale("1/16\" = 1'", 0.0625),
            PaperScale("1/32\" = 1'", 0.03125),
        )

        /** The largest drafting scale that fits every room in the drawing area, centred. Null when there are no rooms. */
        fun forRooms(rooms: List<PlanRoom>, spec: PdfPageSpec = PdfPageSpec()): BlueprintPageLayout? {
            if (rooms.isEmpty()) return null
            val minX = rooms.minOf { it.minX }
            val maxX = rooms.maxOf { it.maxX }
            val minY = rooms.minOf { it.minY }
            val maxY = rooms.maxOf { it.maxY }
            val spanX = (maxX - minX).coerceAtLeast(1)
            val spanY = (maxY - minY).coerceAtLeast(1)
            val availW = spec.widthPt - 2 * spec.marginPt
            val availH = spec.heightPt - spec.headerPt - spec.footerPt - 2 * spec.marginPt
            val scale = SCALES.firstOrNull { it.ptPerPlanInch * spanX <= availW && it.ptPerPlanInch * spanY <= availH }
                ?: SCALES.last()
            val pt = scale.ptPerPlanInch.toFloat()
            val originX = spec.marginPt + (availW - spanX * pt) / 2f - minX * pt
            val originY = spec.marginPt + spec.headerPt + (availH - spanY * pt) / 2f - minY * pt
            val feet = scaleBarFeet(availW / 3f, pt)
            return BlueprintPageLayout(scale, pt, originX, originY, feet, feet * 12 * pt)
        }

        /** Longest of 1, 2, 5, 10, 20, 50 ... feet that fits in [maxPt]; at least one foot. */
        internal fun scaleBarFeet(maxPt: Float, ptPerPlanInch: Float): Int {
            val maxFeet = maxPt / (12f * ptPerPlanInch)
            var best = 1
            var base = 1
            while (base <= 1000) {
                for (m in intArrayOf(1, 2, 5)) if (base * m <= maxFeet) best = base * m
                base *= 10
            }
            return best
        }
    }
}
