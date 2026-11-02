package com.kurtz.homerecord.housemodel.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PolygonTriangulatorTest {
    private fun tri(vararg pts: Pair<Int, Int>) =
        PolygonTriangulator.triangulate(DoubleArray(pts.size) { pts[it].first.toDouble() }, DoubleArray(pts.size) { pts[it].second.toDouble() })

    private fun area(xs: DoubleArray, ys: DoubleArray, t: IntArray): Double =
        (t.indices step 3).sumOf { i ->
            val (a, b, c) = Triple(t[i], t[i + 1], t[i + 2])
            ((xs[b] - xs[a]) * (ys[c] - ys[a]) - (ys[b] - ys[a]) * (xs[c] - xs[a])) / 2
        }

    @Test fun squareIsTwoTriangles() {
        assertEquals(6, tri(0 to 0, 10 to 0, 10 to 10, 0 to 10).size)
    }

    @Test fun concaveOutlineIsCoveredExactlyAndEveryTriangleWindsTheSameWay() {
        // A U shape, clockwise.
        val pts = listOf(0 to 0, 0 to 30, 30 to 30, 30 to 0, 20 to 0, 20 to 20, 10 to 20, 10 to 0)
        val xs = DoubleArray(pts.size) { pts[it].first.toDouble() }
        val ys = DoubleArray(pts.size) { pts[it].second.toDouble() }
        val t = PolygonTriangulator.triangulate(xs, ys)
        assertEquals(6 * 3, t.size)
        assertEquals(30.0 * 30 - 10 * 20, area(xs, ys, t), 1e-9)
        (t.indices step 3).forEach { i ->
            val (a, b, c) = Triple(t[i], t[i + 1], t[i + 2])
            assertTrue((xs[b] - xs[a]) * (ys[c] - ys[a]) - (ys[b] - ys[a]) * (xs[c] - xs[a]) > 0)
        }
    }

    @Test fun aStraightThroughCornerAddsNoSliver() {
        val pts = listOf(0 to 0, 5 to 0, 10 to 0, 10 to 10, 0 to 10)
        val xs = DoubleArray(pts.size) { pts[it].first.toDouble() }
        val ys = DoubleArray(pts.size) { pts[it].second.toDouble() }
        val t = PolygonTriangulator.triangulate(xs, ys)
        assertEquals(100.0, abs(area(xs, ys, t)), 1e-9)
        (t.indices step 3).forEach { i -> assertTrue(abs(area(xs, ys, t.copyOfRange(i, i + 3))) > 0) }
    }

    @Test fun degenerateOutlinesGiveNothing() {
        assertEquals(0, tri(0 to 0, 10 to 0).size)
        assertEquals(0, tri(0 to 0, 10 to 0, 20 to 0).size)
    }
}
