package com.kurtz.homerecord.housemodel.scene

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer
import com.kurtz.homerecord.housemodel.blueprint.LayerSet
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayDepth
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import com.kurtz.homerecord.housemodel.walls.WallKey
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Test
import kotlin.math.abs

// AC-C23: overlays and item markers sit on the wall or floor they belong to in 3D, under the blueprint's layer toggles.
class SceneMarkersTest {
    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)
    private val living = PlanRoom.rectangle("l", "Living room", "LIVING_ROOM", 168, 0, 180, 144)
    private val lShape = PlanRoom(
        "m", "Mudroom", "MUDROOM",
        listOf(Pt(0, 144), Pt(120, 144), Pt(120, 204), Pt(60, 204), Pt(60, 264), Pt(0, 264)),
    )

    private fun overlay(
        id: String,
        kind: OverlayKind,
        points: List<OverlayPoint>,
        roomId: String? = null,
        wallId: String? = null,
    ) = Overlay(id, "h", roomId, wallId, kind, "", null, OverlayDepth.BEHIND_DRYWALL, points, WallStage.ROUGH_IN, null, null, 0L)

    /**
     * Every corner of a wall face in the mesh carries the wall-space spot of the photo painted there (u, v = y down);
     * an overlay drawn at that spot must land on that corner, lifted toward the room.
     */
    private fun assertOverlaysMatchTheFace(rooms: List<PlanRoom>) {
        val scene = FloorScene.build(rooms, 96, emptyMap())
        var checked = 0
        for (part in scene.mesh.parts) {
            val key = part.key as? MeshPartKey.WallFace ?: continue
            val walls = mapOf("w" to WallKey(key.roomId, key.edgeIndex))
            for (v in 0 until part.vertexCount) {
                val u = part.uvs[v * 2]
                val down = part.uvs[v * 2 + 1]
                val marker = SceneMarkers.place(scene, listOf(overlay("o", OverlayKind.OUTLET, listOf(OverlayPoint(u, down)), wallId = "w")), walls).single()
                val at = marker.points.single()
                val x = part.positions[v * 3]
                val y = part.positions[v * 3 + 1]
                val z = part.positions[v * 3 + 2]
                assertThat(abs(at.y - y)).isLessThan(1e-4f)
                // Off the face by the lift only, toward the room.
                assertThat(abs(at.x - (x + marker.facing.x * SceneMarkers.LIFT_M))).isLessThan(1e-3f)
                assertThat(abs(at.z - (z + marker.facing.z * SceneMarkers.LIFT_M))).isLessThan(1e-3f)
                checked++
            }
        }
        assertThat(checked).isGreaterThan(0)
    }

    @Test
    @AcceptanceCriterion("AC-C23")
    fun aWallOverlayLandsWhereTheFacePaintsThatSpotOfThePhoto() {
        assertOverlaysMatchTheFace(listOf(kitchen, living, lShape))
    }

    @Test
    @AcceptanceCriterion("AC-C23")
    fun aCounterClockwiseRoomPutsWallOverlaysOnTheSameSideAsItsPhoto() {
        val reversed = kitchen.copy(points = kitchen.points.reversed())
        assertOverlaysMatchTheFace(listOf(reversed, living, lShape.copy(points = lShape.points.reversed())))
    }

    @Test
    @AcceptanceCriterion("AC-C23")
    fun aWallRunKeepsItsPointsInOrderAndFacesIntoTheRoom() {
        val scene = FloorScene.build(listOf(kitchen), 96, emptyMap())
        // Edge 0 of the kitchen runs west to east along the north wall; the room lies south of it (+z).
        val run = overlay("pipe", OverlayKind.WATER_COLD, listOf(OverlayPoint(0.1f, 0.9f), OverlayPoint(0.9f, 0.9f)), wallId = "n")
        val m = SceneMarkers.place(scene, listOf(run), mapOf("n" to WallKey("k", 0))).single()
        assertThat(m.isRun).isTrue()
        assertThat(m.onWall).isTrue()
        assertThat(m.layer).isEqualTo(BlueprintLayer.PLUMBING)
        assertThat(m.target).isEqualTo(MarkerTarget.OverlayMark("pipe"))
        assertThat(m.facing.z).isWithin(1e-5f).of(1f)
        val (a, b) = m.points
        assertThat(b.x).isGreaterThan(a.x)
        // 90% down a 96 in wall: 9.6 in above the floor.
        assertThat(a.y).isWithin(1e-4f).of(9.6f * 0.0254f)
    }

    @Test
    @AcceptanceCriterion("AC-C23")
    fun planOverlaysAndItemsSitOnTheirRoomsFloorOnTheirOwnStorey() {
        val bedroom = PlanRoom.rectangle("b", "Bedroom", "BEDROOM", 0, 0, 168, 144)
        val scene = FloorScene.stack(
            listOf(
                SceneLayer(StackedFloor("main", 96, 0, ghost = false), listOf(kitchen, living)),
                SceneLayer(StackedFloor("up", 96, 108, ghost = false), listOf(bedroom)),
            ),
            emptyMap(),
        )
        val gas = overlay("gas", OverlayKind.GAS, listOf(OverlayPoint(10f, 20f), OverlayPoint(100f, 20f)), roomId = "k")
        val markers = SceneMarkers.place(
            scene,
            listOf(gas),
            emptyMap(),
            items = listOf(ItemSpot("lamp", "b", 84, 72), ItemSpot("fridge", "k", 84, 72)),
        )
        val pipe = markers.first { it.target == MarkerTarget.OverlayMark("gas") }
        assertThat(pipe.onWall).isFalse()
        assertThat(pipe.points.map { it.y }.distinct()).containsExactly(SceneMarkers.LIFT_M)
        // Plan inches shift by the scene's plan origin like the mesh: 90 in apart stays 90 in apart.
        assertThat(pipe.points[1].x - pipe.points[0].x).isWithin(1e-4f).of(90 * 0.0254f)
        assertThat(pipe.points[0].x).isWithin(1e-4f).of((10f + scene.mesh.offsetXIn) * 0.0254f)

        val lamp = markers.first { it.target == MarkerTarget.ItemMark("lamp") }
        val fridge = markers.first { it.target == MarkerTarget.ItemMark("fridge") }
        assertThat(lamp.layer).isEqualTo(BlueprintLayer.ITEMS)
        assertThat(lamp.points.single().y).isWithin(1e-4f).of(108 * 0.0254f + SceneMarkers.LIFT_M)
        // Same plan spot, one storey apart.
        assertThat(lamp.points.single().x).isEqualTo(fridge.points.single().x)
        assertThat(fridge.points.single().y).isWithin(1e-4f).of(SceneMarkers.LIFT_M)
    }

    @Test
    @AcceptanceCriterion("AC-C23")
    fun nothingIsPlacedOnFadedFloorsUnknownWallsOrWithoutPoints() {
        val bedroom = PlanRoom.rectangle("b", "Bedroom", "BEDROOM", 0, 0, 168, 144)
        val scene = FloorScene.stack(
            listOf(
                SceneLayer(StackedFloor("main", 96, 0, ghost = true), listOf(kitchen)),
                SceneLayer(StackedFloor("up", 96, 108, ghost = false), listOf(bedroom)),
            ),
            emptyMap(),
        )
        val markers = SceneMarkers.place(
            scene,
            listOf(
                overlay("faded", OverlayKind.OUTLET, listOf(OverlayPoint(0.5f, 0.8f)), wallId = "kw"),
                overlay("gone", OverlayKind.OUTLET, listOf(OverlayPoint(0.5f, 0.8f)), wallId = "unknown"),
                overlay("empty", OverlayKind.OUTLET, emptyList(), wallId = "bw"),
                overlay("noRoom", OverlayKind.GAS, listOf(OverlayPoint(1f, 1f))),
                overlay("ok", OverlayKind.SWITCH, listOf(OverlayPoint(0.5f, 0.5f)), wallId = "bw"),
            ),
            mapOf("kw" to WallKey("k", 0), "bw" to WallKey("b", 0), "badEdge" to WallKey("b", 9)),
            items = listOf(ItemSpot("fridge", "k", 10, 10)),
        )
        assertThat(markers.map { it.target }).containsExactly(MarkerTarget.OverlayMark("ok"))
    }

    @Test
    @AcceptanceCriterion("AC-C23")
    fun layerTogglesShowTheSameMarkersAsTheBlueprint() {
        val scene = FloorScene.build(listOf(kitchen), 96, emptyMap())
        val walls = mapOf("n" to WallKey("k", 0))
        val markers = SceneMarkers.place(
            scene,
            listOf(
                overlay("outlet", OverlayKind.OUTLET, listOf(OverlayPoint(0.2f, 0.8f)), wallId = "n"),
                overlay("duct", OverlayKind.DUCT, listOf(OverlayPoint(0.1f, 0.1f), OverlayPoint(0.9f, 0.1f)), wallId = "n"),
                overlay("stud", OverlayKind.STUD, listOf(OverlayPoint(0.5f, 0f), OverlayPoint(0.5f, 1f)), wallId = "n"),
            ),
            walls,
            items = listOf(ItemSpot("fridge", "k", 30, 30)),
        )
        fun shown(layers: LayerSet) = SceneMarkers.visible(markers, layers).map { it.target }
        // Studs belong to no layer and always show, as on the plan.
        assertThat(shown(LayerSet())).containsExactly(MarkerTarget.OverlayMark("stud"))
        assertThat(shown(LayerSet(setOf(BlueprintLayer.ELECTRICAL, BlueprintLayer.ITEMS)))).containsExactly(
            MarkerTarget.OverlayMark("outlet"),
            MarkerTarget.OverlayMark("stud"),
            MarkerTarget.ItemMark("fridge"),
        )
        assertThat(shown(LayerSet(setOf(BlueprintLayer.HVAC)))).containsExactly(
            MarkerTarget.OverlayMark("duct"),
            MarkerTarget.OverlayMark("stud"),
        )
    }

    @Test
    @AcceptanceCriterion("AC-C23")
    fun anItemHungOnAWallSitsOnThatWallAtTheSameSpotAsAnOverlayThere() {
        val scene = FloorScene.build(listOf(kitchen, living), 96, emptyMap())
        val walls = mapOf("w-k0" to WallKey("k", 0))
        val pipe = overlay("o", OverlayKind.OUTLET, listOf(OverlayPoint(0.3f, 0.4f)), wallId = "w-k0")
        val markers = SceneMarkers.place(
            scene, listOf(pipe), walls,
            items = listOf(ItemSpot("tstat", "k", 50, 6, "Thermostat", wallId = "w-k0", wallX = 0.3f, wallY = 0.4f)),
        )
        val outlet = markers.first { it.target == MarkerTarget.OverlayMark("o") }
        val tstat = markers.first { it.target == MarkerTarget.ItemMark("tstat") }
        assertThat(tstat.onWall).isTrue()
        assertThat(tstat.layer).isEqualTo(BlueprintLayer.ITEMS)
        assertThat(tstat.points).isEqualTo(outlet.points)
        assertThat(tstat.facing).isEqualTo(outlet.facing)
        // Above the floor, not lying on it.
        assertThat(tstat.points.single().y).isGreaterThan(scene.rooms.getValue("k").floorY + 0.5f)
    }

    @Test
    @AcceptanceCriterion("AC-C23")
    fun anItemWhoseWallIsGoneOrInAnotherRoomFallsBackToItsFloorSpot() {
        val scene = FloorScene.build(listOf(kitchen, living), 96, emptyMap())
        val walls = mapOf("w-l0" to WallKey("l", 0))
        val markers = SceneMarkers.place(
            scene, emptyList(), walls,
            items = listOf(
                ItemSpot("gone", "k", 30, 30, wallId = "w-missing", wallX = 0.5f, wallY = 0.5f),
                ItemSpot("other", "k", 40, 40, wallId = "w-l0", wallX = 0.5f, wallY = 0.5f),
            ),
        )
        assertThat(markers).hasSize(2)
        markers.forEach { m ->
            assertThat(m.onWall).isFalse()
            assertThat(m.facing).isEqualTo(ScenePoint(0f, 1f, 0f))
        }
    }
}
