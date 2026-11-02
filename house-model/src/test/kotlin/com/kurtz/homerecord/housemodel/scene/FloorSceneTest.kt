package com.kurtz.homerecord.housemodel.scene

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.walls.WallKey
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Test

// AC-C20: wall faces show their photo at the chosen stage; the rest keep the room's tint.
class FloorSceneTest {
    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)
    private val living = PlanRoom.rectangle("l", "Living room", "LIVING_ROOM", 168, 0, 180, 144)
    private val pictures = mapOf(
        WallKey("k", 0) to mapOf(WallStage.FINISHED to "/m/k0-wall.jpg", WallStage.ROUGH_IN to "/m/k0-rough.jpg"),
        WallKey("l", 2) to mapOf(WallStage.ROUGH_IN to "/m/l2-rough.jpg"),
        // A wall the plan no longer has: no face to paint, so it is ignored.
        WallKey("gone", 0) to mapOf(WallStage.FINISHED to "/m/gone.jpg"),
    )

    @Test
    @AcceptanceCriterion("AC-C20")
    fun picturesFollowTheChosenStage() {
        val scene = FloorScene.build(listOf(kitchen, living), 96, pictures)
        assertThat(scene.picturesAt(WallStage.FINISHED)).containsExactly(MeshPartKey.WallFace("k", 0), "/m/k0-wall.jpg")
        assertThat(scene.picturesAt(WallStage.ROUGH_IN)).containsExactly(
            MeshPartKey.WallFace("k", 0), "/m/k0-rough.jpg",
            MeshPartKey.WallFace("l", 2), "/m/l2-rough.jpg",
        )
        assertThat(scene.picturesAt(WallStage.FRAMING)).isEmpty()
    }

    @Test
    @AcceptanceCriterion("AC-C20")
    fun countsWallsAndTintsRoomsByKind() {
        val bedroom = PlanRoom.rectangle("b", "Bedroom", "BEDROOM", 0, 144, 168, 120)
        val scene = FloorScene.build(listOf(kitchen, living, bedroom), 96, emptyMap())
        assertThat(scene.wallCount).isEqualTo(12)
        assertThat(scene.roomCount).isEqualTo(3)
        assertThat(scene.isEmpty).isFalse()
        assertThat(scene.tintOf("k")).isEqualTo(RoomTint.of("KITCHEN"))
        assertThat(setOf(scene.tintOf("k"), scene.tintOf("l"), scene.tintOf("b"))).hasSize(3)
        assertThat(scene.tintOf("unknown")).isEqualTo(RoomTint.of("OTHER"))
        // Kinds the picker offers that share a look.
        assertThat(RoomTint.of("LIVING_ROOM")).isEqualTo(RoomTint.of("DINING_ROOM"))
        assertThat(RoomTint.of("not a kind")).isEqualTo(RoomTint.of("OTHER"))
    }

    @Test
    @AcceptanceCriterion("AC-C20")
    fun aFloorWithNoRoomsIsEmpty() {
        val scene = FloorScene.build(emptyList(), 96, pictures)
        assertThat(scene.isEmpty).isTrue()
        assertThat(scene.wallCount).isEqualTo(0)
        assertThat(scene.picturesAt(WallStage.FINISHED)).isEmpty()
    }

    // The still picture used by previews and the screen catalog: faces turned away are dropped, glass is kept from
    // both sides, and triangles come far to near so the near walls are drawn last.
    @Test
    @AcceptanceCriterion("AC-C20")
    fun stillPictureDropsHiddenFacesAndDrawsFarToNear() {
        val mesh = PlanMeshBuilder().build(listOf(kitchen), 96)
        val triangles = SceneProjection.project(mesh)
        assertThat(triangles).isNotEmpty()
        assertThat(triangles.size).isLessThan(mesh.triangleCount)
        assertThat(triangles.map { it.depth }).isInOrder()
        assertThat(triangles.all { it.light in 0f..1f }).isTrue()
        // Looking from the south: the kitchen's north wall (edge 0) faces the camera, its south wall (edge 2) faces away.
        assertThat(triangles.any { it.key == MeshPartKey.WallFace("k", 0) }).isTrue()
        assertThat(triangles.none { it.key == MeshPartKey.WallFace("k", 2) }).isTrue()
        // North is up on the picture: the north wall's points sit above the south wall's.
        val north = triangles.filter { it.key == MeshPartKey.WallFace("k", 0) }.flatMap { it.ys.toList() }.average()
        val floor = triangles.filter { it.key == MeshPartKey.Floor("k") }.flatMap { it.ys.toList() }.average()
        assertThat(north).isLessThan(floor)
    }

    @Test
    @AcceptanceCriterion("AC-C21")
    fun stackedFloorsShareOneOriginAndTheFloorsBelowAreGhosts() {
        // The upper bedroom sits over the kitchen only; on its own it would be centered on itself.
        val bedroom = PlanRoom.rectangle("b", "Bedroom", "BEDROOM", 0, 0, 168, 144)
        val layers = listOf(
            SceneLayer(StackedFloor("main", 96, 0, ghost = true), listOf(kitchen, living)),
            SceneLayer(StackedFloor("up", 96, 108, ghost = false), listOf(bedroom)),
        )
        val pictures = mapOf(
            WallKey("k", 0) to mapOf(WallStage.FINISHED to "/m/k0.jpg"),
            WallKey("b", 0) to mapOf(WallStage.FINISHED to "/m/b0.jpg"),
        )
        val scene = FloorScene.stack(layers, pictures)
        assertThat(scene.ghostRooms).containsExactly("k", "l")
        assertThat(scene.isEmpty).isFalse()
        assertThat(scene.roomCount).isEqualTo(1)
        assertThat(scene.wallCount).isEqualTo(4)
        // Photos only on the chosen floor.
        assertThat(scene.picturesAt(WallStage.FINISHED)).containsExactly(MeshPartKey.WallFace("b", 0), "/m/b0.jpg")
        assertThat(scene.isGhost(MeshPartKey.Floor("k"))).isTrue()
        assertThat(scene.isGhost(MeshPartKey.Floor("b"))).isFalse()

        // The bedroom's floor sits exactly over the kitchen's, raised to the upper floor's base.
        val kitchenFloor = checkNotNull(scene.mesh.part(MeshPartKey.Floor("k"))).bounds()
        val bedroomFloor = checkNotNull(scene.mesh.part(MeshPartKey.Floor("b"))).bounds()
        assertThat(bedroomFloor.minX).isWithin(0.001f).of(kitchenFloor.minX)
        assertThat(bedroomFloor.maxZ).isWithin(0.001f).of(kitchenFloor.maxZ)
        assertThat(bedroomFloor.minY - kitchenFloor.minY).isWithin(0.001f).of(108 * 0.0254f)
        // The camera frames the chosen floor, not the faded one.
        assertThat(scene.focus.minY).isWithin(0.001f).of(108 * 0.0254f)
        assertThat(scene.lookAtY).isWithin(0.001f).of(scene.focus.minY)
        assertThat(scene.mesh.bounds.minY).isWithin(0.001f).of(0f)
    }

    @Test
    @AcceptanceCriterion("AC-C21")
    fun allFloorsFramesTheWholeStackAndAnEmptyChosenFloorIsEmpty() {
        val bedroom = PlanRoom.rectangle("b", "Bedroom", "BEDROOM", 0, 0, 168, 144)
        val all = FloorScene.stack(
            listOf(
                SceneLayer(StackedFloor("main", 96, 0, ghost = false), listOf(kitchen, living)),
                SceneLayer(StackedFloor("up", 96, 156, ghost = false), listOf(bedroom)),
            ),
            emptyMap(),
        )
        assertThat(all.ghostRooms).isEmpty()
        assertThat(all.roomCount).isEqualTo(3)
        assertThat(all.wallCount).isEqualTo(12)
        assertThat(all.solidFloors).isEqualTo(2)
        assertThat(all.lookAtY).isWithin(0.001f).of(all.focus.centerY)
        assertThat(all.focus.maxY).isWithin(0.01f).of((156 + 96) * 0.0254f)

        val upperEmpty = FloorScene.stack(
            listOf(
                SceneLayer(StackedFloor("main", 96, 0, ghost = true), listOf(kitchen)),
                SceneLayer(StackedFloor("up", 96, 108, ghost = false), emptyList()),
            ),
            emptyMap(),
        )
        assertThat(upperEmpty.isEmpty).isTrue()
        assertThat(upperEmpty.wallCount).isEqualTo(0)
    }
}
