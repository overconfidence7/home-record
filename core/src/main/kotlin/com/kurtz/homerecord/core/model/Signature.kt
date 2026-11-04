package com.kurtz.homerecord.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.hypot

/** One pen stroke on the signature pad: x, y pairs in 0..1 of the pad's width and height, in drawing order. */
@Serializable
data class SignatureStroke(val points: List<Float>) {
    val pointCount: Int get() = points.size / 2
    fun x(i: Int): Float = points[i * 2]
    fun y(i: Int): Float = points[i * 2 + 1]
}

/**
 * The rules for a signature (AC-F38): a tap or a stray dot is not a signature, so Sign stays off until the strokes
 * add up to [MIN_INK] of ink (a fraction of the pad's width). Strokes are stored normalised so the same signature
 * draws the same at any size, on the pad, in the PNG and in the PDF.
 */
object Signature {
    /** Ink needed before Sign turns on: a fifth of the pad's width. */
    const val MIN_INK: Float = 0.2f

    /** Total drawn length, in pad widths (y is scaled by [aspect] = height / width so a stroke's length is true). */
    fun inkLength(strokes: List<SignatureStroke>, aspect: Float = 1f): Float = strokes.sumOf { s ->
        (1 until s.pointCount).sumOf { i -> hypot(s.x(i) - s.x(i - 1), (s.y(i) - s.y(i - 1)) * aspect).toDouble() }
    }.toFloat()

    fun isSigned(strokes: List<SignatureStroke>, aspect: Float = 1f): Boolean = inkLength(strokes, aspect) >= MIN_INK

    /** A point clamped into the pad, so a finger that slides off the edge does not draw outside it. */
    fun clamp(v: Float): Float = v.coerceIn(0f, 1f)

    private val json = Json { ignoreUnknownKeys = true }
    private val LIST = ListSerializer(SignatureStroke.serializer())

    fun encode(strokes: List<SignatureStroke>): String = json.encodeToString(LIST, strokes)

    /** Saved strokes; an unreadable value reads as a blank pad. */
    fun decode(text: String?): List<SignatureStroke> =
        text?.let { runCatching { json.decodeFromString(LIST, it) }.getOrNull() }.orEmpty()
}
