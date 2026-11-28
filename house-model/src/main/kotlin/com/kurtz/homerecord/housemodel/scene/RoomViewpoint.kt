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

    /** Up or down in radians, negative looking down. */
    val pitch: Float get() = atan2(target.y - eye.y, hypot(target.x - eye.x, target.z - eye.z))
}

/**
 * The room view's opening camera (AC-C22): inside the room at eye height (5 ft 6 in) above its floor looking at the
 * longest wall, at a point [TARGET_ALONG] of the way along it from the corner the camera steps toward, tilted [PITCH_DOWN_DEG] down so the floor shows. The camera stands back from that wall along
 * the wall's inward normal, near the opposite side of the room (a little in front of it, [BACK_OFF_M]), then steps
 * sideways toward one end of the room until it is [SIDE_CLEAR_M] from the side wall, so the view takes in the longest
 * wall, the corner and the side wall next to it and the floor rather than one flat wall face edge to edge
 * ([judge:3d-default-framing]). Every spot is checked to be inside the room, even in an L-shaped one: the step back lies
 * on the first stretch of that normal before it leaves the footprint, and a sideways step that would leave it is halved
 * until it does not. A room too small to step back in puts the camera halfway across. Pure: tested on the JVM.
 */
object RoomViewpoint {
    const val EYE_HEIGHT_IN = 66
    const val BACK_OFF_M = 0.45f
    const val SIDE_CLEAR_M = 0.6f
    const val PITCH_DOWN_DEG = 12f
    const val TARGET_ALONG = 0.3f
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
        val baseX = mx + nx * back
        val baseZ = mz + nz * back
        // Sideways toward the wall's first corner, stopping short of the side wall; halved while it would leave the room.
        val sx = (ax - mx) / (len / 2)
        val sz = (az - mz) / (len / 2)
        var slide = (len / 2 - SIDE_CLEAR_M).coerceAtLeast(0f)
        var eyeX = baseX
        var eyeZ = baseZ
        repeat(6) {
            if (slide <= 0f) return@repeat
            val x = baseX + sx * slide
            val z = baseZ + sz * slide
            if (insideClear(pts, x, z, LookAround.WALL_MARGIN_M)) {
                eyeX = x
                eyeZ = z
                return@repeat
            }
            slide /= 2
        }
        // Aim [TARGET_ALONG] of the way along the wall from that corner, so the corner is in the frame too.
        val tx = ax + (bx - ax) * TARGET_ALONG
        val tz = az + (bz - az) * TARGET_ALONG
        val reach = hypot(tx - eyeX, tz - eyeZ)
        val drop = reach * kotlin.math.tan(PITCH_DOWN_DEG * PI.toFloat() / 180f)
        return Viewpoint(ScenePoint(eyeX, eyeY, eyeZ), ScenePoint(tx, eyeY - drop, tz))
    }

    /** True when (x, z) is inside the outline and at least [clear] from every edge. */
    private fun insideClear(pts: List<Pair<Float, Float>>, x: Float, z: Float, clear: Float): Boolean {
        var inside = false
        for (i in pts.indices) {
            val (ax, az) = pts[i]
            val (bx, bz) = pts[(i + 1) % pts.size]
            if ((az > z) != (bz > z) && x < (bx - ax) * (z - az) / (bz - az) + ax) inside = !inside
            val ex = bx - ax
            val ez = bz - az
            val len2 = ex * ex + ez * ez
            val t = if (len2 <= 0f) 0f else (((x - ax) * ex + (z - az) * ez) / len2).coerceIn(0f, 1f)
            if (hypot(x - (ax + ex * t), z - (az + ez * t)) < clear - 1e-4f) return false
        }
        return inside
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
    var pitch: Float = start.pitch
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
        pitch = start.pitch
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
