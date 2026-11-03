package com.kurtz.homerecord.housemodel.scene

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

// AC-C23: overlay runs and spots and item markers become flat geometry on their wall or floor, facing into the room.
class MarkerMeshBuilderTest {
    private fun marker(points: List<ScenePoint>, facing: ScenePoint, item: Boolean = false) = SceneMarker(
        target = if (item) MarkerTarget.ItemMark("i") else MarkerTarget.OverlayMark("o"),
        roomId = "k",
        layer = if (item) BlueprintLayer.ITEMS else BlueprintLayer.PLUMBING,
        overlayKind = if (item) null else OverlayKind.WATER_COLD,
        points = points,
        facing = facing,
        onWall = facing.y == 0f,
    )

    private fun MarkerMesh.point(i: Int) = ScenePoint(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2])

    /** Every triangle's face normal points the way the marker faces. */
    private fun assertFaces(mesh: MarkerMesh, facing: ScenePoint) {
        for (t in 0 until mesh.triangleCount) {
            val a = mesh.point(mesh.indices[t * 3])
            val b = mesh.point(mesh.indices[t * 3 + 1])
            val c = mesh.point(mesh.indices[t * 3 + 2])
            val ux = b.x - a.x; val uy = b.y - a.y; val uz = b.z - a.z
            val vx = c.x - a.x; val vy = c.y - a.y; val vz = c.z - a.z
            val nx = uy * vz - uz * vy
            val ny = uz * vx - ux * vz
            val nz = ux * vy - uy * vx
            assertThat(nx * facing.x + ny * facing.y + nz * facing.z).isAtLeast(0f)
        }
        for (i in 0 until mesh.vertexCount) {
            assertThat(mesh.normals[i * 3]).isEqualTo(facing.x)
            assertThat(mesh.normals[i * 3 + 1]).isEqualTo(facing.y)
            assertThat(mesh.normals[i * 3 + 2]).isEqualTo(facing.z)
        }
    }

    @Test
    @AcceptanceCriterion("AC-C23")
    fun aRunOnAWallIsABandInTheWallPlaneFacingTheRoom() {
        // A wall along x at z = 0, seen from the room at +z: every vertex stays on the wall plane.
        val facing = ScenePoint(0f, 0f, 1f)
        val mesh = MarkerMeshBuilder.build(marker(listOf(ScenePoint(0f, 1f, 0.01f), ScenePoint(2f, 1f, 0.01f), ScenePoint(2f, 2f, 0.01f)), facing))
        for (i in 0 until mesh.vertexCount) assertThat(mesh.point(i).z).isWithin(1e-6f).of(0.01f)
        // Two bands (2 triangles each) and three round caps.
        assertThat(mesh.triangleCount).isEqualTo(2 * 2 + 3 * MarkerMeshBuilder.DISC_SEGMENTS)
        // The band is RUN_WIDTH_M tall around the horizontal leg.
        val ys = (0 until 4).map { mesh.point(it).y }
        assertThat(ys.max() - ys.min()).isWithin(1e-5f).of(MarkerMeshBuilder.RUN_WIDTH_M)
        assertFaces(mesh, facing)
    }

    @Test
    @AcceptanceCriterion("AC-C23")
    fun aRunOnTheFloorLiesFlatAndFacesUp() {
        val up = ScenePoint(0f, 1f, 0f)
        val mesh = MarkerMeshBuilder.build(marker(listOf(ScenePoint(0f, 0.01f, 0f), ScenePoint(1f, 0.01f, 1f)), up))
        for (i in 0 until mesh.vertexCount) assertThat(mesh.point(i).y).isWithin(1e-6f).of(0.01f)
        assertFaces(mesh, up)
    }

    @Test
    @AcceptanceCriterion("AC-C23")
    fun spotsAreDiscsAndAnItemReadsLargerThanAnOutlet() {
        val facing = ScenePoint(-1f, 0f, 0f)
        val c = ScenePoint(3f, 1.2f, 2f)
        val outlet = MarkerMeshBuilder.build(marker(listOf(c), facing))
        val item = MarkerMeshBuilder.build(marker(listOf(ScenePoint(0f, 0.01f, 0f)), ScenePoint(0f, 1f, 0f), item = true))
        assertThat(outlet.triangleCount).isEqualTo(MarkerMeshBuilder.DISC_SEGMENTS)
        fun radius(m: MarkerMesh, center: ScenePoint) = (1 until m.vertexCount).maxOf { i ->
            val p = m.point(i)
            sqrt((p.x - center.x) * (p.x - center.x) + (p.y - center.y) * (p.y - center.y) + (p.z - center.z) * (p.z - center.z))
        }
        assertThat(radius(outlet, c)).isWithin(1e-5f).of(MarkerMeshBuilder.SPOT_RADIUS_M)
        assertThat(radius(item, ScenePoint(0f, 0.01f, 0f))).isWithin(1e-5f).of(MarkerMeshBuilder.ITEM_RADIUS_M)
        // The outlet disc lies in the wall plane x = 3.
        for (i in 0 until outlet.vertexCount) assertThat(abs(outlet.point(i).x - 3f)).isLessThan(1e-6f)
        assertFaces(outlet, facing)
    }

    @Test
    fun aRunWithRepeatedPointsSkipsTheEmptyLegAndAnEmptyMarkerHasNoTriangles() {
        val facing = ScenePoint(0f, 0f, 1f)
        val p = ScenePoint(0f, 1f, 0f)
        val mesh = MarkerMeshBuilder.build(marker(listOf(p, p), facing))
        assertThat(mesh.triangleCount).isEqualTo(2 * MarkerMeshBuilder.DISC_SEGMENTS)
        assertThat(MarkerMeshBuilder.build(marker(emptyList(), facing)).triangleCount).isEqualTo(0)
    }
}
