package com.kurtz.homerecord.housemodel.scene

/**
 * Ear clipping for a simple polygon (convex, L-shaped, any concave room outline) given as (x, y) pairs.
 * Returns index triples into the input, each wound the same way as a polygon with positive signed area
 * (sum of x_i * y_(i+1) - x_(i+1) * y_i). Collinear corners are clipped as flat ears so nothing is dropped.
 * A polygon with fewer than three corners or no area gives no triangles.
 */
object PolygonTriangulator {
    fun triangulate(xs: DoubleArray, ys: DoubleArray): IntArray {
        val n = xs.size
        if (n < 3 || ys.size != n) return IntArray(0)
        var area2 = 0.0
        for (i in 0 until n) {
            val j = (i + 1) % n
            area2 += xs[i] * ys[j] - xs[j] * ys[i]
        }
        if (area2 == 0.0) return IntArray(0)
        // Work on a positive-area ordering; triangles are emitted in that ordering.
        val ring = if (area2 > 0) MutableList(n) { it } else MutableList(n) { n - 1 - it }
        val out = ArrayList<Int>((n - 2) * 3)
        var guard = 0
        while (ring.size > 3 && guard < n * n) {
            guard++
            var clipped = false
            for (k in ring.indices) {
                val a = ring[(k - 1 + ring.size) % ring.size]
                val b = ring[k]
                val c = ring[(k + 1) % ring.size]
                val cross = cross(xs, ys, a, b, c)
                if (cross < 0) continue // reflex corner
                if (cross == 0.0) {
                    // A straight-through corner: drop it without a triangle.
                    ring.removeAt(k)
                    clipped = true
                    break
                }
                if (ring.any { p -> p != a && p != b && p != c && inside(xs, ys, a, b, c, p) }) continue
                out += a
                out += b
                out += c
                ring.removeAt(k)
                clipped = true
                break
            }
            if (!clipped) break // not simple; return what was clipped
        }
        if (ring.size == 3 && cross(xs, ys, ring[0], ring[1], ring[2]) > 0) {
            out += ring[0]
            out += ring[1]
            out += ring[2]
        }
        return out.toIntArray()
    }

    private fun cross(xs: DoubleArray, ys: DoubleArray, a: Int, b: Int, c: Int): Double =
        (xs[b] - xs[a]) * (ys[c] - ys[a]) - (ys[b] - ys[a]) * (xs[c] - xs[a])

    /** True when p is inside or on triangle abc (positive winding), other than at its corners. */
    private fun inside(xs: DoubleArray, ys: DoubleArray, a: Int, b: Int, c: Int, p: Int): Boolean {
        if ((xs[p] == xs[a] && ys[p] == ys[a]) || (xs[p] == xs[b] && ys[p] == ys[b]) || (xs[p] == xs[c] && ys[p] == ys[c])) {
            return false
        }
        return cross(xs, ys, a, b, p) >= 0 && cross(xs, ys, b, c, p) >= 0 && cross(xs, ys, c, a, p) >= 0
    }
}
