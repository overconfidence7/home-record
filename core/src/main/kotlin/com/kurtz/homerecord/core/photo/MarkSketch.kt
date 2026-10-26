package com.kurtz.homerecord.core.photo

import kotlin.math.hypot

enum class AnnotationTool { Arrow, Circle, Rectangle, Freehand, Text, Measure }

/** What a finished gesture on the photo turns into (AC-F26). Text and measure need a typed value first. */
sealed interface SketchResult {
    data class Mark(val mark: Annotation) : SketchResult
    data class AskText(val at: NormPoint) : SketchResult
    data class AskMeasure(val from: NormPoint, val to: NormPoint) : SketchResult
    data object Nothing : SketchResult
}

object MarkSketch {
    /**
     * [points] are the touch points of one gesture as fractions of the photo; [aspect] is width over height, which keeps
     * a drawn circle round (its radius is a fraction of the shorter side, as the renderer reads it).
     */
    fun finish(tool: AnnotationTool, points: List<NormPoint>, aspect: Float): SketchResult {
        val first = points.firstOrNull() ?: return SketchResult.Nothing
        val last = points.last()
        return when (tool) {
            AnnotationTool.Text -> SketchResult.AskText(first)
            AnnotationTool.Freehand -> if (points.size >= 2) SketchResult.Mark(Annotation.Freehand(points)) else SketchResult.Nothing
            AnnotationTool.Arrow -> if (first == last) SketchResult.Nothing else SketchResult.Mark(Annotation.Arrow(first, last))
            AnnotationTool.Rectangle -> if (first == last) SketchResult.Nothing else SketchResult.Mark(Annotation.Rectangle(first, last))
            AnnotationTool.Measure -> if (first == last) SketchResult.Nothing else SketchResult.AskMeasure(first, last)
            AnnotationTool.Circle -> {
                val a = aspect.coerceAtLeast(0.01f)
                val dx = (last.x - first.x) * a
                val dy = last.y - first.y
                val shorter = minOf(a, 1f)
                val radius = hypot(dx, dy) / shorter
                if (radius <= 0f) SketchResult.Nothing else SketchResult.Mark(Annotation.Circle(first, radius))
            }
        }
    }
}
