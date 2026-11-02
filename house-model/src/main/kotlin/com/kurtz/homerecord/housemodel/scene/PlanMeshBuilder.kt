package com.kurtz.homerecord.housemodel.scene

import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.WallDeriver
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Extrudes one floor's room outlines into 3D geometry for the 3D view:
 *
 * - every room edge becomes an inside wall face set in from the plan line by half the wall's thickness, facing into
 *   the room, with its own [MeshPartKey.WallFace] part so the wall's stage photo can texture it (u along the wall from
 *   its first corner, v from the top down);
 * - an exterior edge (no other room shares it) also gets an outside face a further half thickness out, so the shell
 *   is a full wall seen from outside; a shared edge is half of a wall on each room's side;
 * - doors are openings from the floor up to the door's height; windows are openings whose head sits at
 *   [WINDOW_HEAD_IN] (or just under a low ceiling), filled with a lighter two-sided pane ([MeshPartKey.Glass]);
 *   a door or window drawn on the neighbouring room's side of a shared wall cuts this room's face too, so a door
 *   between two rooms is open from both sides;
 * - openings get jambs, head and sill across the wall's thickness and every wall gets a top cap, so the walls read
 *   as solid from above;
 * - each room's floor is its outline triangulated (convex, L-shaped or any simple polygon) at floor level.
 *
 * Plan inches are converted to meters (y up, plan y as world z) and the footprint is centered on the origin.
 */
class PlanMeshBuilder(
    private val wallThicknessIn: Double = DEFAULT_WALL_THICKNESS_IN,
    private val sharedToleranceIn: Int = 4,
) {
    /**
     * @param wallHeightIn the floor's ceiling height (the floor record's height); at least 24 in is used.
     * @param baseYIn how far above the ground this floor sits (stacked floors).
     */
    fun build(rooms: List<PlanRoom>, wallHeightIn: Int, baseYIn: Int = 0): PlanMesh {
        val usable = rooms.filter { it.points.size >= 3 && it.signedArea2Of() != 0L }
        if (usable.isEmpty()) return PlanMesh(emptyList(), MeshBounds.EMPTY, 0f, 0f)
        val height = max(wallHeightIn, MIN_WALL_HEIGHT_IN).toDouble()
        val offX = -(usable.minOf { r -> r.points.minOf { it.x } } + usable.maxOf { r -> r.points.maxOf { it.x } }) / 2.0
        val offZ = -(usable.minOf { r -> r.points.minOf { it.y } } + usable.maxOf { r -> r.points.maxOf { it.y } }) / 2.0
        val frame = Frame(offX, offZ, baseYIn.toDouble())
        val exterior = WallDeriver(sharedToleranceIn).derive(usable).associate { (it.roomId to it.edgeIndex) to it.exterior }

        val parts = mutableListOf<MeshPart>()
        for (room in usable) {
            val shell = PartBuilder(MeshPartKey.Shell(room.id), frame)
            val glass = PartBuilder(MeshPartKey.Glass(room.id), frame)
            val outline = room.points.map { V2(it.x.toDouble(), it.y.toDouble()) }
            val n = outline.size
            val sign = if (room.signedArea2Of() > 0) 1.0 else -1.0
            val inward = List(n) { i -> (outline[(i + 1) % n] - outline[i]).unit().let { d -> V2(-d.y, d.x) * sign } }
            val half = wallThicknessIn / 2
            val inset = offset(outline, inward) { half }
            val outer = offset(outline, inward) { i -> if (exterior[room.id to i] != false) -half else 0.0 }

            for (i in 0 until n) {
                val a = outline[i]
                val b = outline[(i + 1) % n]
                val dir = (b - a).unit()
                if (dir.isZero()) continue
                val nIn = inward[i]
                val isExterior = exterior[room.id to i] ?: true
                val holes = holes(room, i, a, dir, usable, height)

                val face = PartBuilder(MeshPartKey.WallFace(room.id, i), frame)
                val s0 = (inset[i] - a).dot(dir)
                val s1 = (inset[(i + 1) % n] - a).dot(dir)
                val faceStart = inset[i]
                surface(face, faceStart, dir, s0, s1, holes, nIn, height, textured = true)
                if (face.hasTriangles()) parts += face.build()

                val depth = if (isExterior) wallThicknessIn else half
                if (isExterior) {
                    val o0 = (outer[i] - a).dot(dir)
                    val o1 = (outer[(i + 1) % n] - a).dot(dir)
                    surface(shell, outer[i], dir, o0, o1, holes, -nIn, height, textured = false)
                }
                // Top cap from the inside face to the back of this room's part of the wall.
                val back0 = if (isExterior) outer[i] else a
                val back1 = if (isExterior) outer[(i + 1) % n] else b
                shell.quad(
                    listOf(inset[i].at(height), inset[(i + 1) % n].at(height), back1.at(height), back0.at(height)),
                    V3.UP,
                )
                // Jambs, head, sill and glass for each opening within this face.
                for (h in if (s1 - s0 < EPS) emptyList() else holes) {
                    val hs0 = max(h.s0, s0)
                    val hs1 = min(h.s1, s1)
                    if (hs1 - hs0 < EPS) continue
                    val f0 = faceStart + dir * (hs0 - s0)
                    val f1 = faceStart + dir * (hs1 - s0)
                    val back = nIn * -depth
                    shell.quad(listOf(f0.at(h.v0), (f0 + back).at(h.v0), (f0 + back).at(h.v1), f0.at(h.v1)), V3(dir.x, 0.0, dir.y))
                    shell.quad(listOf(f1.at(h.v0), f1.at(h.v1), (f1 + back).at(h.v1), (f1 + back).at(h.v0)), V3(-dir.x, 0.0, -dir.y))
                    if (h.v1 < height - EPS) {
                        shell.quad(listOf(f0.at(h.v1), (f0 + back).at(h.v1), (f1 + back).at(h.v1), f1.at(h.v1)), V3.DOWN)
                    }
                    if (h.v0 > EPS) {
                        shell.quad(listOf(f0.at(h.v0), f1.at(h.v0), (f1 + back).at(h.v0), (f0 + back).at(h.v0)), V3.UP)
                    }
                    if (h.window) {
                        val mid = nIn * (-depth / 2)
                        val g0 = f0 + mid
                        val g1 = f1 + mid
                        val corners = listOf(g0.at(h.v0), g1.at(h.v0), g1.at(h.v1), g0.at(h.v1))
                        glass.quad(corners, V3(nIn.x, 0.0, nIn.y), uvs = PANE_UVS)
                        glass.quad(corners, V3(-nIn.x, 0.0, -nIn.y), uvs = PANE_UVS)
                    }
                }
            }
            if (shell.hasTriangles()) parts += shell.build()
            if (glass.hasTriangles()) parts += glass.build()
            floor(room, frame)?.let { parts += it }
        }
        return PlanMesh(parts, bounds(parts), offX.toFloat(), offZ.toFloat())
    }

    /** The openings that cut edge [edgeIndex], in along-edge inches from its first corner, merged where they overlap. */
    private fun holes(room: PlanRoom, edgeIndex: Int, a: V2, dir: V2, rooms: List<PlanRoom>, height: Double): List<Hole> {
        val raw = mutableListOf<Hole>()
        room.openings.filter { it.edgeIndex == edgeIndex }.forEach { o ->
            vertical(o.kind, o.heightIn, height)?.let { (v0, v1) ->
                raw += Hole(o.offsetIn.toDouble(), (o.offsetIn + o.widthIn).toDouble(), v0, v1, o.kind == OpeningKind.WINDOW)
            }
        }
        for (other in rooms) {
            if (other.id == room.id) continue
            val m = other.points.size
            for (j in 0 until m) {
                val c = other.points[j].let { V2(it.x.toDouble(), it.y.toDouble()) }
                val e = other.points[(j + 1) % m].let { V2(it.x.toDouble(), it.y.toDouble()) }
                if (!collinearOverlap(a, dir, c, e)) continue
                val od = (e - c).unit()
                other.openings.filter { it.edgeIndex == j }.forEach { o ->
                    val p = (c + od * o.offsetIn.toDouble() - a).dot(dir)
                    val q = (c + od * (o.offsetIn + o.widthIn).toDouble() - a).dot(dir)
                    vertical(o.kind, o.heightIn, height)?.let { (v0, v1) ->
                        raw += Hole(min(p, q), max(p, q), v0, v1, o.kind == OpeningKind.WINDOW)
                    }
                }
            }
        }
        val merged = mutableListOf<Hole>()
        raw.filter { it.s1 - it.s0 > EPS }.sortedBy { it.s0 }.forEach { h ->
            val last = merged.lastOrNull()
            if (last != null && h.s0 < last.s1 - EPS) {
                merged[merged.lastIndex] = Hole(last.s0, max(last.s1, h.s1), min(last.v0, h.v0), max(last.v1, h.v1), last.window && h.window)
            } else {
                merged += h
            }
        }
        return merged
    }

    /** Bottom and top of an opening: doors stand on the floor, windows hang from the head height. */
    private fun vertical(kind: OpeningKind, heightIn: Int, wallHeight: Double): Pair<Double, Double>? {
        val maxTop = wallHeight - HEAD_CLEARANCE_IN
        val (v0, v1) = when (kind) {
            OpeningKind.DOOR -> 0.0 to min(heightIn.toDouble(), maxTop)
            OpeningKind.WINDOW -> {
                val head = min(WINDOW_HEAD_IN.toDouble(), maxTop)
                max(head - heightIn, 0.0) to head
            }
        }
        return if (v1 - v0 > EPS) v0 to v1 else null
    }

    private fun collinearOverlap(a: V2, dir: V2, c: V2, d: V2): Boolean {
        fun perp(p: V2) = abs((p - a).cross(dir))
        if (perp(c) > sharedToleranceIn || perp(d) > sharedToleranceIn) return false
        return true
    }

    /**
     * A wall surface from [start] along [dir] covering along-edge coordinates [s0]..[s1], full [height], with the
     * [holes] left open. Textured surfaces get u along the wall and v from its top.
     */
    private fun surface(
        part: PartBuilder,
        start: V2,
        dir: V2,
        s0: Double,
        s1: Double,
        holes: List<Hole>,
        normal: V2,
        height: Double,
        textured: Boolean,
    ) {
        // A wall shorter than the wall thickness has its inside face folded over by the corners: nothing to draw.
        if (s1 - s0 < EPS) return
        val lo = s0
        val hi = s1
        val cuts = (listOf(lo, hi) + holes.flatMap { listOf(it.s0, it.s1) }.filter { it > lo && it < hi }).distinct().sorted()
        val n3 = V3(normal.x, 0.0, normal.y)
        fun point(s: Double) = start + dir * (s - s0)
        fun uv(s: Double, y: Double) = if (textured) floatArrayOf(((s - s0) / (s1 - s0)).toFloat(), (1 - y / height).toFloat()) else ZERO_UV
        fun band(sa: Double, sb: Double, ya: Double, yb: Double) {
            if (yb - ya < EPS) return
            part.quad(
                listOf(point(sa).at(ya), point(sb).at(ya), point(sb).at(yb), point(sa).at(yb)),
                n3,
                uvs = listOf(uv(sa, ya), uv(sb, ya), uv(sb, yb), uv(sa, yb)),
            )
        }
        for (k in 0 until cuts.size - 1) {
            val sa = cuts[k]
            val sb = cuts[k + 1]
            if (sb - sa < EPS) continue
            val mid = (sa + sb) / 2
            val hole = holes.firstOrNull { mid > it.s0 && mid < it.s1 }
            if (hole == null) {
                band(sa, sb, 0.0, height)
            } else {
                band(sa, sb, 0.0, hole.v0)
                band(sa, sb, hole.v1, height)
            }
        }
    }

    private fun floor(room: PlanRoom, frame: Frame): MeshPart? {
        val xs = DoubleArray(room.points.size) { room.points[it].x.toDouble() }
        val ys = DoubleArray(room.points.size) { room.points[it].y.toDouble() }
        val tris = PolygonTriangulator.triangulate(xs, ys)
        if (tris.isEmpty()) return null
        val part = PartBuilder(MeshPartKey.Floor(room.id), frame)
        val base = xs.indices.map { i ->
            part.vertex(V3(xs[i], 0.0, ys[i]), V3.UP, floatArrayOf((xs[i] / FLOOR_TILE_IN).toFloat(), (ys[i] / FLOOR_TILE_IN).toFloat()))
        }
        for (t in tris.indices step 3) part.triangle(base[tris[t]], base[tris[t + 1]], base[tris[t + 2]], V3.UP)
        return part.build()
    }

    /**
     * The outline with edge i moved by [distance] (i) along its inward normal (negative moves it out), each corner
     * where its two moved edges meet. A shared edge on the outside line keeps distance 0, so the outer face of the
     * exterior wall beside it stops at the shared wall's line instead of poking into the neighbouring room.
     */
    private fun offset(points: List<V2>, inward: List<V2>, distance: (Int) -> Double): List<V2> {
        val n = points.size
        return List(n) { i ->
            val pi = (i - 1 + n) % n
            val n1 = inward[pi].takeUnless { it.isZero() } ?: inward[i]
            val n2 = inward[i].takeUnless { it.isZero() } ?: n1
            val d1 = if (inward[pi].isZero()) distance(i) else distance(pi)
            val d2 = if (inward[i].isZero()) d1 else distance(i)
            val det = n1.cross(n2)
            if (abs(det) < PARALLEL_EPS) {
                points[i] + n2 * d2
            } else {
                val shift = V2((d1 * n2.y - d2 * n1.y) / det, (n1.x * d2 - n2.x * d1) / det)
                val limit = MITER_LIMIT * max(abs(d1), abs(d2))
                points[i] + if (shift.length() > limit) shift.unit() * limit else shift
            }
        }
    }

    private fun bounds(parts: List<MeshPart>): MeshBounds {
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE
        var any = false
        for (p in parts) {
            val pos = p.positions
            for (i in pos.indices step 3) {
                any = true
                minX = min(minX, pos[i]); maxX = max(maxX, pos[i])
                minY = min(minY, pos[i + 1]); maxY = max(maxY, pos[i + 1])
                minZ = min(minZ, pos[i + 2]); maxZ = max(maxZ, pos[i + 2])
            }
        }
        return if (any) MeshBounds(minX, minY, minZ, maxX, maxY, maxZ) else MeshBounds.EMPTY
    }

    private data class Hole(val s0: Double, val s1: Double, val v0: Double, val v1: Double, val window: Boolean)

    companion object {
        /** A 2×4 stud wall with drywall both sides, in inches. */
        const val DEFAULT_WALL_THICKNESS_IN = 4.5

        /** Window heads line up with a standard 6'8" door head. */
        const val WINDOW_HEAD_IN = 80

        /** Openings stop at least this far below the ceiling so the wall above them never vanishes. */
        const val HEAD_CLEARANCE_IN = 4.0
        const val MIN_WALL_HEIGHT_IN = 24
        const val INCH_M = 0.0254
        private const val FLOOR_TILE_IN = 48.0
        private const val MITER_LIMIT = 4.0
        private const val PARALLEL_EPS = 1e-9
        private const val EPS = 1e-6
        private val ZERO_UV = floatArrayOf(0f, 0f)
        private val PANE_UVS = listOf(floatArrayOf(0f, 1f), floatArrayOf(1f, 1f), floatArrayOf(1f, 0f), floatArrayOf(0f, 0f))
    }
}

private fun PlanRoom.signedArea2Of(): Long {
    var sum = 0L
    for (i in points.indices) {
        val a = points[i]
        val b = points[(i + 1) % points.size]
        sum += a.x.toLong() * b.y - b.x.toLong() * a.y
    }
    return sum
}

/** A plan point in inches (x east, y south). */
private data class V2(val x: Double, val y: Double) {
    operator fun plus(o: V2) = V2(x + o.x, y + o.y)
    operator fun minus(o: V2) = V2(x - o.x, y - o.y)
    operator fun times(k: Double) = V2(x * k, y * k)
    operator fun unaryMinus() = V2(-x, -y)
    fun dot(o: V2) = x * o.x + y * o.y
    fun cross(o: V2) = x * o.y - y * o.x
    fun length() = hypot(x, y)
    fun isZero() = x == 0.0 && y == 0.0
    fun unit(): V2 = length().let { if (it == 0.0) V2(0.0, 0.0) else V2(x / it, y / it) }

    /** This plan point at height [y] inches: world inches (x, up, z). */
    fun at(y: Double) = V3(x, y, this.y)
}

/** World inches before centering: x east, y up, z south. */
private data class V3(val x: Double, val y: Double, val z: Double) {
    companion object {
        val UP = V3(0.0, 1.0, 0.0)
        val DOWN = V3(0.0, -1.0, 0.0)
    }
}

/** Plan inches to world meters: centered footprint, floor lifted by its elevation. */
private class Frame(val offX: Double, val offZ: Double, val baseY: Double)

private class PartBuilder(private val key: MeshPartKey, private val frame: Frame) {
    private val pos = ArrayList<Float>()
    private val nrm = ArrayList<Float>()
    private val tex = ArrayList<Float>()
    private val idx = ArrayList<Int>()

    fun hasTriangles() = idx.isNotEmpty()

    fun vertex(p: V3, n: V3, uv: FloatArray): Int {
        val k = PlanMeshBuilder.INCH_M
        pos += ((p.x + frame.offX) * k).toFloat()
        pos += ((p.y + frame.baseY) * k).toFloat()
        pos += ((p.z + frame.offZ) * k).toFloat()
        nrm += n.x.toFloat()
        nrm += n.y.toFloat()
        nrm += n.z.toFloat()
        tex += uv[0]
        tex += uv[1]
        return pos.size / 3 - 1
    }

    /** Adds triangle a, b, c wound so its front faces [n]. */
    fun triangle(a: Int, b: Int, c: Int, n: V3) {
        val ax = pos[a * 3]; val ay = pos[a * 3 + 1]; val az = pos[a * 3 + 2]
        val ux = pos[b * 3] - ax; val uy = pos[b * 3 + 1] - ay; val uz = pos[b * 3 + 2] - az
        val vx = pos[c * 3] - ax; val vy = pos[c * 3 + 1] - ay; val vz = pos[c * 3 + 2] - az
        val cx = uy * vz - uz * vy
        val cy = uz * vx - ux * vz
        val cz = ux * vy - uy * vx
        val facing = cx * n.x + cy * n.y + cz * n.z
        if (facing >= 0) {
            idx += a; idx += b; idx += c
        } else {
            idx += a; idx += c; idx += b
        }
    }

    /** A planar quad with corners in order around its edge. */
    fun quad(corners: List<V3>, n: V3, uvs: List<FloatArray>? = null) {
        val v = corners.mapIndexed { i, p -> vertex(p, n, uvs?.get(i) ?: floatArrayOf(0f, 0f)) }
        triangle(v[0], v[1], v[2], n)
        triangle(v[0], v[2], v[3], n)
    }

    fun build() = MeshPart(key, pos.toFloatArray(), nrm.toFloatArray(), tex.toFloatArray(), idx.toIntArray())
}
