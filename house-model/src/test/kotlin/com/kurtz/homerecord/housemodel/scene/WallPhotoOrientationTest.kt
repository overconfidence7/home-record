package com.kurtz.homerecord.housemodel.scene

import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * [judge:hm-3d-wall-photo-mirrored]: seen from inside the room, the left edge of a wall photo lands on the left end
 * of the wall, for rooms whose corners run either way round. The camera stands in the middle of the room at eye
 * height facing the wall, with the same right-handed basis Filament's lookAt gives (right = forward x up).
 */
class WallPhotoOrientationTest {
    private val builder = PlanMeshBuilder()
    private val clockwise = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 120)
    private val counterClockwise = clockwise.copy(points = clockwise.points.reversed())

    @AcceptanceCriterion("AC-C20")
    @Test fun photoLeftIsWallLeftSeenFromInsideClockwiseRoom() = checkAllWalls(clockwise)

    @AcceptanceCriterion("AC-C20")
    @Test fun photoLeftIsWallLeftSeenFromInsideCounterClockwiseRoom() = checkAllWalls(counterClockwise)

    private fun checkAllWalls(room: PlanRoom) {
        val mesh = builder.build(listOf(room), wallHeightIn = 96)
        val face0 = (0..3).map { mesh.part(MeshPartKey.WallFace("k", it))!! }
        // Room centre in world meters: the mesh is centred on the origin.
        val eye = floatArrayOf(0f, 66 * 0.0254f, 0f)
        face0.forEachIndexed { i, face ->
            val n = face.vertexCount
            val cx = (0 until n).map { face.positions[it * 3] }.average().toFloat()
            val cz = (0 until n).map { face.positions[it * 3 + 2] }.average().toFloat()
            val fwd = norm(floatArrayOf(cx - eye[0], 0f, cz - eye[2]))
            val up = floatArrayOf(0f, 1f, 0f)
            val right = norm(cross(fwd, up))
            val screenX = (0 until n).map { v ->
                (face.positions[v * 3] - eye[0]) * right[0] + (face.positions[v * 3 + 2] - eye[2]) * right[2]
            }
            val us = (0 until n).map { face.uvs[it * 2] }
            val left = (0 until n).filter { us[it] < 0.01f }.map { screenX[it] }.average()
            val rightEnd = (0 until n).filter { us[it] > 0.99f }.map { screenX[it] }.average()
            assertTrue("wall $i: photo left (u=0) at $left must be left of photo right (u=1) at $rightEnd", left < rightEnd)
            // The texture's v runs from the top of the wall down.
            val top = (0 until n).filter { face.uvs[it * 2 + 1] < 0.01f }.map { face.positions[it * 3 + 1] }.average()
            val bottom = (0 until n).filter { face.uvs[it * 2 + 1] > 0.99f }.map { face.positions[it * 3 + 1] }.average()
            assertTrue("wall $i: v=0 at the top", top > bottom)
        }
    }

    private fun cross(a: FloatArray, b: FloatArray) =
        floatArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    private fun norm(v: FloatArray): FloatArray {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }
}

/** The GPU side of [judge:hm-3d-wall-photo-mirrored]: the photo's top row lands on the top of the wall. */
class TextureUvTest {
    private val builder = PlanMeshBuilder()

    @AcceptanceCriterion("AC-C20")
    @Test fun photoTopRowIsPaintedAtTheTopOfTheWall() {
        val room = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 120)
        val face = builder.build(listOf(room), wallHeightIn = 96).part(MeshPartKey.WallFace("k", 0))!!
        val rows = (0 until face.vertexCount).map { i ->
            val (_, v) = TextureUv.forFilament(face.uvs[i * 2], face.uvs[i * 2 + 1])
            face.positions[i * 3 + 1] to TextureUv.bitmapRow(TextureUv.sampled(v))
        }
        val top = rows.maxOf { it.first }
        val bottom = rows.minOf { it.first }
        rows.filter { it.first == top }.forEach { assertTrue("top of the wall shows the photo's top row", it.second < 0.01f) }
        rows.filter { it.first == bottom }.forEach { assertTrue("bottom of the wall shows its bottom row", it.second > 0.99f) }
    }

    @Test fun uIsLeftAsIs() {
        assertTrue(TextureUv.forFilament(0.25f, 0.1f).first == 0.25f)
        assertTrue(TextureUv.forFilament(0.25f, 0.1f).second == 0.9f)
    }
}
