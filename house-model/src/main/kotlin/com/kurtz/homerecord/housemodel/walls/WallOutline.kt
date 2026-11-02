package com.kurtz.homerecord.housemodel.walls

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** A point in the upright photo, 0..1 across and 0..1 down. */
data class PhotoPoint(val x: Float, val y: Float) {
    fun clamped(): PhotoPoint = PhotoPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
}

/**
 * The wall's four corners in the photo, in the order top-left, top-right, bottom-right, bottom-left (AC-C18). Stored as
 * the `[{"x":..,"y":..},...]` JSON in `wall_photo.cornersJson`.
 */
data class WallOutline(val topLeft: PhotoPoint, val topRight: PhotoPoint, val bottomRight: PhotoPoint, val bottomLeft: PhotoPoint) {
    val corners: List<PhotoPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /** The outline with corner [index] (0..3, in [corners] order) moved to [to], kept inside the photo. */
    fun moved(index: Int, to: PhotoPoint): WallOutline {
        val p = to.clamped()
        return when (index) {
            0 -> copy(topLeft = p)
            1 -> copy(topRight = p)
            2 -> copy(bottomRight = p)
            3 -> copy(bottomLeft = p)
            else -> this
        }
    }

    /**
     * True when the corners make a convex four-sided shape, wound the way they are named and at least 1% of the photo,
     * so the warp has a real wall to straighten. A crossed or collapsed outline is not usable.
     */
    fun isUsable(): Boolean {
        val c = corners
        val turns = (0 until 4).map { i ->
            val a = c[i]
            val b = c[(i + 1) % 4]
            val d = c[(i + 2) % 4]
            (b.x - a.x) * (d.y - b.y) - (b.y - a.y) * (d.x - b.x)
        }
        // Screen y grows downward, so top-left → top-right → bottom-right turns clockwise on screen: every cross is positive.
        if (turns.any { it <= 0f }) return false
        return area() >= 0.01f
    }

    /** Area of the outline as a share of the photo (shoelace formula). */
    fun area(): Float {
        val c = corners
        var sum = 0f
        for (i in 0 until 4) {
            val a = c[i]
            val b = c[(i + 1) % 4]
            sum += a.x * b.y - b.x * a.y
        }
        return kotlin.math.abs(sum) / 2f
    }

    fun encode(): String {
        val array = JSONArray()
        corners.forEach { array.put(JSONObject().put("x", it.x.toDouble()).put("y", it.y.toDouble())) }
        return array.toString()
    }

    companion object {
        /** Where the handles start: each corner inset 10% from the photo's edges (AC-C18). */
        val Initial = WallOutline(PhotoPoint(0.1f, 0.1f), PhotoPoint(0.9f, 0.1f), PhotoPoint(0.9f, 0.9f), PhotoPoint(0.1f, 0.9f))

        fun of(corners: List<PhotoPoint>): WallOutline? =
            if (corners.size == 4) WallOutline(corners[0].clamped(), corners[1].clamped(), corners[2].clamped(), corners[3].clamped()) else null

        /** Null when the JSON is missing, damaged or not four points. */
        fun decode(json: String?): WallOutline? {
            if (json.isNullOrBlank()) return null
            return try {
                val array = JSONArray(json)
                of((0 until array.length()).map { i -> array.getJSONObject(i).let { PhotoPoint(it.getDouble("x").toFloat(), it.getDouble("y").toFloat()) } })
            } catch (_: JSONException) {
                null
            }
        }
    }
}
