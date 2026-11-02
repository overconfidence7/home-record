package com.kurtz.homerecord.housemodel.scene

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin

/** A point in the 3D scene, meters, y up (see [PlanMesh] for the axes). */
data class ScenePoint(val x: Float, val y: Float, val z: Float)

/**
 * A room as placed in the scene, for the room view (AC-C22): its footprint corners on the ground in world meters
 * (x, z pairs, plan order), the height of its floor and of its ceiling.
 */
data class SceneRoom(
    val id: String,
    val name: String,
    val footprint: List<Pair<Float, Float>>,
    val floorY: Float,
    val ceilingY: Float,
)

/** Where the room view's camera stands and what it looks at when it opens. */
data class Viewpoint(val eye: ScenePoint, val target: ScenePoint) {
    /** Heading in radians, 0 looking toward -z (north on the plan), growing clockwise seen from above (toward +x). */
    val yaw: Float get() = atan2(target.x - eye.x, -(target.z - eye.z))
}

/**
 * The room view's opening camera (AC-C22): inside the room at eye height (5 ft 6 in above its floor) looking straight
 * at the middle of its longest wall. The camera stands back from that wall along the wall's inward normal, near the
 * opposite side of the room (a little in front of it, [BACK_OFF_M]) so the whole wall is in view; the spot is always
 * inside the room, even in an L-shaped one, because it lies on the first stretch of that normal before it leaves the
 * footprint. A room too small to step back in puts the camera halfway across. Pure: tested on the JVM.
 */
object RoomViewpoint {
    const val EYE_HEIGHT_IN = 66
    const val BACK_OFF_M = 0.45f
    private const val M_PER_IN = 0.0254f

    fun inside(room: SceneRoom): Viewpoint? {
        val pts = room.footprint
        if (pts.size < 3) return null
        val eyeY = room.floorY + EYE_HEIGHT_IN * M_PER_IN
        val ccw = signedArea(pts) > 0 // in (x, z), counter-clockwise as the numbers go
        val longest = pts.indices.maxBy { i -> edgeLength(pts, i) }
        val (ax, az) = pts[longest]
        val (bx, bz) = pts[(longest + 1) % pts.size]
        val len = hypot(bx - ax, bz - az)
        if (len <= 0f) return null
        val mx = (ax + bx) / 2
        val mz = (az + bz) / 2
        // Left of a→b is inside for a counter-clockwise outline, right of it for a clockwise one.
        var nx = -(bz - az) / len
        var nz = (bx - ax) / len
        if (!ccw) { nx = -nx; nz = -nz }
        val across = distanceToExit(pts, mx, mz, nx, nz, skip = longest)
        val back = if (across == null) 0f else if (across > 2 * BACK_OFF_M) across - BACK_OFF_M else across / 2
        val eye = ScenePoint(mx + nx * back, eyeY, mz + nz * back)
        return Viewpoint(eye, ScenePoint(mx, eyeY, mz))
    }

    private fun edgeLength(pts: List<Pair<Float, Float>>, i: Int): Float {
        val (ax, az) = pts[i]
        val (bx, bz) = pts[(i + 1) % pts.size]
        return hypot(bx - ax, bz - az)
    }

    private fun signedArea(pts: List<Pair<Float, Float>>): Float {
        var a = 0f
        for (i in pts.indices) {
            val (x1, z1) = pts[i]
            val (x2, z2) = pts[(i + 1) % pts.size]
            a += x1 * z2 - x2 * z1
        }
        return a / 2
    }

    /** Distance along (dx, dz) from (ox, oz) to the nearest other edge of the outline, or null when none is hit. */
    private fun distanceToExit(pts: List<Pair<Float, Float>>, ox: Float, oz: Float, dx: Float, dz: Float, skip: Int): Float? {
        var best: Float? = null
        for (i in pts.indices) {
            if (i == skip) continue
            val (ax, az) = pts[i]
            val (bx, bz) = pts[(i + 1) % pts.size]
            val ex = bx - ax
            val ez = bz - az
            val denom = dx * ez - dz * ex
            if (abs(denom) < 1e-6f) continue
            val t = ((ax - ox) * ez - (az - oz) * ex) / denom
            val s = ((ax - ox) * dz - (az - oz) * dx) / denom
            if (t > 1e-4f && s >= -1e-4f && s <= 1 + 1e-4f) best = if (best == null) t else min(best, t)
        }
        return best
    }
}

/**
 * Look-around state of the room view (AC-C22). A one-finger drag turns the camera where it stands; the picture follows
 * the finger, so dragging left turns right and dragging up looks down, [degreesPerPx] per pixel, up or down stopping at
 * ±[MAX_PITCH_DEG]. A pinch zooms by stepping the camera forward or back along the way it faces ([zoom]); a step that
 * would bring it within [WALL_MARGIN_M] of a wall or out of the room is cut short, so it never walks through a wall.
 * Pure: the renderer reads [eye] and [target].
 */
class LookAround(
    private val start: Viewpoint,
    private val room: SceneRoom,
    private val degreesPerPx: Float = 0.15f,
) {
    var yaw: Float = start.yaw
        private set
    var pitch: Float = 0f
        private set
    var eye: ScenePoint = start.eye
        private set

    /** A point one meter ahead of the eye in the direction the camera faces. */
    val target: ScenePoint
        get() = ScenePoint(
            eye.x + sin(yaw) * cos(pitch),
            eye.y + sin(pitch),
            eye.z - cos(yaw) * cos(pitch),
        )

    /** A drag of ([dxPx], [dyPx]) screen pixels. */
    fun drag(dxPx: Float, dyPx: Float) {
        yaw = wrap(yaw - rad(dxPx * degreesPerPx))
        pitch = (pitch + rad(dyPx * degreesPerPx)).coerceIn(-rad(MAX_PITCH_DEG), rad(MAX_PITCH_DEG))
    }

    /**
     * A pinch from finger separation [fromPx] to [toPx]: spreading steps forward, pinching steps back,
     * [METERS_PER_DOUBLING] per doubling of the separation.
     */
    fun zoom(fromPx: Float, toPx: Float) {
        if (fromPx <= 0f || toPx <= 0f || fromPx.isNaN() || toPx.isNaN()) return
        val step = (ln(toPx / fromPx) / ln(2f)) * METERS_PER_DOUBLING
        if (abs(step) < 1e-4f) return
        val dx = sin(yaw) * step
        val dz = -cos(yaw) * step
        // Halve the step until it lands well inside the room; a step that cannot is dropped.
        var f = 1f
        repeat(6) {
            val x = eye.x + dx * f
            val z = eye.z + dz * f
            if (insideWithMargin(x, z)) {
                eye = ScenePoint(x, eye.y, z)
                return
            }
            f /= 2
        }
    }

    fun reset() {
        yaw = start.yaw
        pitch = 0f
        eye = start.eye
    }

    private fun insideWithMargin(x: Float, z: Float): Boolean {
        val pts = room.footprint
        var inside = false
        for (i in pts.indices) {
            val (ax, az) = pts[i]
            val (bx, bz) = pts[(i + 1) % pts.size]
            if ((az > z) != (bz > z) && x < (bx - ax) * (z - az) / (bz - az) + ax) inside = !inside
            if (distanceToSegment(x, z, ax, az, bx, bz) < WALL_MARGIN_M) return false
        }
        return inside
    }

    private fun distanceToSegment(px: Float, pz: Float, ax: Float, az: Float, bx: Float, bz: Float): Float {
        val ex = bx - ax
        val ez = bz - az
        val len2 = ex * ex + ez * ez
        val t = if (len2 <= 0f) 0f else (((px - ax) * ex + (pz - az) * ez) / len2).coerceIn(0f, 1f)
        return hypot(px - (ax + ex * t), pz - (az + ez * t))
    }

    private fun rad(deg: Float) = (deg * PI / 180).toFloat()

    private fun wrap(a: Float): Float {
        val twoPi = (2 * PI).toFloat()
        var r = a % twoPi
        if (r > PI) r -= twoPi
        if (r < -PI) r += twoPi
        return r
    }

    companion object {
        const val MAX_PITCH_DEG = 80f
        const val METERS_PER_DOUBLING = 1.5f
        const val WALL_MARGIN_M = 0.3f
    }
}
