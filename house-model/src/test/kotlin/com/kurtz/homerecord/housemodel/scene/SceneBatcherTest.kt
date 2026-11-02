package com.kurtz.homerecord.housemodel.scene

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.PlanOpening
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.walls.WallKey
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Test

// AC-C24: the 3D view's performance pass. Parts merge into one renderable per room and material, wall pictures are
// sized to a texture budget, and the measurement line reports the first frame and the frame rate.
class SceneBatcherTest {
    private val ft = 12
    private fun room(id: String, kind: String, x: Int, y: Int, w: Int, l: Int) = PlanRoom.rectangle(id, id, kind, x * ft, y * ft, w * ft, l * ft)

    private val kitchen = room("k", "KITCHEN", 0, 0, 14, 12)
        .copy(openings = listOf(PlanOpening("w", 0, OpeningKind.WINDOW, 4 * ft, PlanRoom.DEFAULT_WINDOW_IN, 48)))
    private val living = room("l", "LIVING_ROOM", 14, 0, 18, 12)

    @Test
    @AcceptanceCriterion("AC-C24")
    fun oneBatchPerRoomAndMaterialWithEveryTriangleKept() {
        val pictures = mapOf(
            WallKey("k", 0) to mapOf(WallStage.FINISHED to "/m/k0.jpg"),
            WallKey("k", 2) to mapOf(WallStage.FINISHED to "/m/k2.jpg"),
        )
        val scene = FloorScene.build(listOf(kitchen, living), 96, pictures)
        val batches = SceneBatcher.batch(scene, WallStage.FINISHED)

        // Kitchen: tint (shell + two plain walls), floor, glass, two pictures. Living room: tint, floor.
        assertThat(batches.map { it.roomId to it.material }).containsExactly(
            "k" to DrawMaterial.Tint(RoomTint.of("KITCHEN")),
            "k" to DrawMaterial.Picture("/m/k0.jpg", RoomTint.of("KITCHEN")),
            "k" to DrawMaterial.Picture("/m/k2.jpg", RoomTint.of("KITCHEN")),
            "k" to DrawMaterial.Floor,
            "k" to DrawMaterial.Glass,
            "l" to DrawMaterial.Tint(RoomTint.of("LIVING_ROOM")),
            "l" to DrawMaterial.Floor,
        )
        assertThat(batches.size).isLessThan(scene.mesh.parts.size)
        assertThat(batches.sumOf { it.mesh.triangleCount }).isEqualTo(scene.mesh.triangleCount)
        // Each batch keeps its own bounds, so Filament can cull a room outside the view.
        val kitchenTint = batches.first { it.roomId == "k" && it.material is DrawMaterial.Tint }
        assertThat(kitchenTint.bounds.maxX).isLessThan(batches.first { it.roomId == "l" && it.material is DrawMaterial.Tint }.bounds.maxX)
    }

    @Test
    @AcceptanceCriterion("AC-C24")
    fun anotherStageTurnsPicturesBackIntoTheTint() {
        val pictures = mapOf(WallKey("k", 0) to mapOf(WallStage.ROUGH_IN to "/m/k0-rough.jpg"))
        val scene = FloorScene.build(listOf(kitchen), 96, pictures)
        assertThat(SceneBatcher.batch(scene, WallStage.FINISHED).map { it.material }).doesNotContain(DrawMaterial.Picture("/m/k0-rough.jpg", RoomTint.of("KITCHEN")))
        assertThat(SceneBatcher.batch(scene, WallStage.ROUGH_IN).map { it.material }).contains(DrawMaterial.Picture("/m/k0-rough.jpg", RoomTint.of("KITCHEN")))
    }

    @Test
    @AcceptanceCriterion("AC-C24")
    fun ghostRoomsDrawInOneBatchWithoutGlass() {
        val below = SceneLayer(StackedFloor("b", 96, 0, ghost = true), listOf(kitchen))
        val chosen = SceneLayer(StackedFloor("m", 96, 106, ghost = false), listOf(living.copy(id = "up")))
        val scene = FloorScene.stack(listOf(below, chosen), mapOf(WallKey("k", 0) to mapOf(WallStage.FINISHED to "/m/k0.jpg")))
        val batches = SceneBatcher.batch(scene, WallStage.FINISHED)
        assertThat(batches.filter { it.roomId == "k" }.map { it.material }).containsExactly(DrawMaterial.Ghost)
        val ghostTriangles = scene.mesh.parts.filter { it.key.roomId == "k" && it.key !is MeshPartKey.Glass }.sumOf { it.triangleCount }
        assertThat(batches.first { it.roomId == "k" }.mesh.triangleCount).isEqualTo(ghostTriangles)
    }

    @Test
    @AcceptanceCriterion("AC-C24")
    fun mergeOffsetsIndicesIntoTheJoinedBuffer() {
        val a = MeshPart(MeshPartKey.Shell("r"), floatArrayOf(0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f), FloatArray(9), FloatArray(6), intArrayOf(0, 1, 2))
        val b = MeshPart(MeshPartKey.WallFace("r", 1), floatArrayOf(5f, 0f, 0f, 6f, 0f, 0f, 5f, 1f, 0f), FloatArray(9), floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f), intArrayOf(0, 2, 1))
        val merged = SceneBatcher.merge(listOf(a, b))
        assertThat(merged.vertexCount).isEqualTo(6)
        assertThat(merged.indices.toList()).containsExactly(0, 1, 2, 3, 5, 4).inOrder()
        assertThat(merged.positions[9]).isEqualTo(5f)
        assertThat(merged.uvs[8]).isEqualTo(1f)
    }

    @Test
    @AcceptanceCriterion("AC-C24")
    fun texturesShrinkToFitTheBudget() {
        assertThat(TextureBudget.maxSidePx(0)).isEqualTo(1024)
        assertThat(TextureBudget.maxSidePx(12)).isEqualTo(1024)
        // The reference home: 20 photographed walls.
        assertThat(TextureBudget.maxSidePx(20)).isEqualTo(512)
        assertThat(20 * TextureBudget.bytesPerTexture(512)).isAtMost(TextureBudget.BUDGET_BYTES)
        assertThat(TextureBudget.maxSidePx(100)).isEqualTo(256)
        assertThat(TextureBudget.maxSidePx(10_000)).isEqualTo(TextureBudget.MIN_PX)

        assertThat(TextureBudget.sampleSize(4032, 3024, 512)).isEqualTo(8)
        assertThat(TextureBudget.sampleSize(1600, 1200, 1024)).isEqualTo(2)
        assertThat(TextureBudget.sampleSize(800, 600, 1024)).isEqualTo(1)
    }

    @Test
    @AcceptanceCriterion("AC-C24")
    fun frameStatsReportFirstFrameAndRate() {
        val ms = 1_000_000L
        val stats = FrameStats(startNanos = 0, windowNanos = 1_000 * ms)
        assertThat(stats.fps).isNull()
        assertThat(stats.summary(draws = 3, textures = 0, texturePx = 1024)).startsWith("first frame - ms; - fps")
        // First frame at 900 ms, then 60 frames a second for two seconds.
        var t = 900 * ms
        repeat(121) { stats.onFrame(t); t += 1_000 * ms / 60 }
        assertThat(stats.firstFrameMs).isEqualTo(900)
        assertThat(stats.frames).isEqualTo(121)
        assertThat(stats.fps).isWithin(0.5f).of(60f)
        // A slow stretch at 20 fps fills the window.
        repeat(30) { stats.onFrame(t); t += 50 * ms }
        assertThat(stats.fps).isWithin(0.5f).of(20f)
        assertThat(stats.summary(draws = 110, textures = 20, texturePx = 512))
            .isEqualTo("first frame 900 ms; 20.0 fps; 151 frames; 110 draws; 20 textures at 512 px")
    }
}
