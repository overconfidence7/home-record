package com.kurtz.homerecord.housemodel.scene

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** One marker's triangles in world meters (AC-C23), every one facing [SceneMarker.facing], ready for a renderable. */
class MarkerMesh(val positions: FloatArray, val normals: FloatArray, val indices: IntArray) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3
}

/**
 * Turns a [SceneMarker] into flat geometry lying on its wall or floor (AC-C23): a run is a band [RUN_WIDTH_M] wide along
 * its points with a round cap at every point so bends and ends look like one pipe or wire; a spot is a disc, larger for an
 * installed item than for an outlet or valve so the two read apart. Every triangle winds counter-clockwise seen from the
 * marker's facing side, so the renderer's back-face culling keeps it.
 */
object MarkerMeshBuilder {
    const val RUN_WIDTH_M = 0.06f
    const val SPOT_RADIUS_M = 0.12f
    const val ITEM_RADIUS_M = 0.2f
    const val DISC_SEGMENTS = 16

    fun build(marker: SceneMarker): MarkerMesh {
        val out = Builder(marker.facing.unit())
        val pts = marker.points
        if (marker.isRun) {
            val half = RUN_WIDTH_M / 2
            for (i in 0 until pts.size - 1) out.band(pts[i], pts[i + 1], half)
            pts.forEach { out.disc(it, half) }
        } else if (pts.isNotEmpty()) {
            out.disc(pts[0], if (marker.target is MarkerTarget.ItemMark) ITEM_RADIUS_M else SPOT_RADIUS_M)
        }
        return out.mesh()
    }

    private class Builder(private val facing: ScenePoint) {
        private val positions = ArrayList<Float>()
        private val indices = ArrayList<Int>()

        /** Two basis directions lying on the surface the marker faces away from. */
        private val u: ScenePoint = cross(facing, if (abs(facing.y) > 0.9f) ScenePoint(0f, 0f, 1f) else ScenePoint(0f, 1f, 0f)).unit()
        private val v: ScenePoint = cross(facing, u).unit()

        fun band(a: ScenePoint, b: ScenePoint, half: Float) {
            val d = (b - a)
            if (d.length() == 0f) return
            val side = cross(facing, d.unit()).unit() * half
            val p0 = vertex(a - side)
            val p1 = vertex(a + side)
            val p2 = vertex(b + side)
            val p3 = vertex(b - side)
            triangle(p0, p1, p2)
            triangle(p0, p2, p3)
        }

        fun disc(c: ScenePoint, r: Float) {
            val center = vertex(c)
            val ring = List(DISC_SEGMENTS) { i ->
                val t = 2 * PI * i / DISC_SEGMENTS
                vertex(c + u * (r * cos(t).toFloat()) + v * (r * sin(t).toFloat()))
            }
            for (i in ring.indices) triangle(center, ring[i], ring[(i + 1) % ring.size])
        }

        private fun vertex(p: ScenePoint): Int {
            positions += p.x
            positions += p.y
            positions += p.z
            return positions.size / 3 - 1
        }

        /** Adds the triangle wound so its face normal points along [facing]. */
        private fun triangle(a: Int, b: Int, c: Int) {
            val n = cross(at(b) - at(a), at(c) - at(a))
            if (dot(n, facing) >= 0f) {
                indices += a; indices += b; indices += c
            } else {
                indices += a; indices += c; indices += b
            }
        }

        private fun at(i: Int) = ScenePoint(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2])

        fun mesh(): MarkerMesh {
            val count = positions.size / 3
            val normals = FloatArray(count * 3) { i -> when (i % 3) { 0 -> facing.x; 1 -> facing.y; else -> facing.z } }
            return MarkerMesh(positions.toFloatArray(), normals, indices.toIntArray())
        }
    }

    private operator fun ScenePoint.plus(o: ScenePoint) = ScenePoint(x + o.x, y + o.y, z + o.z)
    private operator fun ScenePoint.minus(o: ScenePoint) = ScenePoint(x - o.x, y - o.y, z - o.z)
    private operator fun ScenePoint.times(k: Float) = ScenePoint(x * k, y * k, z * k)
    private fun ScenePoint.length() = sqrt(x * x + y * y + z * z)
    private fun ScenePoint.unit(): ScenePoint = length().let { l -> if (l == 0f) ScenePoint(0f, 1f, 0f) else ScenePoint(x / l, y / l, z / l) }
    private fun cross(a: ScenePoint, b: ScenePoint) = ScenePoint(a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
    private fun dot(a: ScenePoint, b: ScenePoint) = a.x * b.x + a.y * b.y + a.z * b.z
}
