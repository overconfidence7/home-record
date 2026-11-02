package com.kurtz.homerecord.housemodel.scene

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot

// AC-C22: the room view stands the camera inside the room at eye height (5 ft 6 in) looking at its longest wall;
// dragging looks around, pinching steps closer without leaving the room.
class RoomViewpointTest {
    private val m = 0.0254f
    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)

    private fun inside(room: SceneRoom, x: Float, z: Float): Boolean {
        val pts = room.footprint
        var c = false
        for (i in pts.indices) {
            val (ax, az) = pts[i]
            val (bx, bz) = pts[(i + 1) % pts.size]
            if ((az > z) != (bz > z) && x < (bx - ax) * (z - az) / (bz - az) + ax) c = !c
        }
        return c
    }

    @Test
    @AcceptanceCriterion("AC-C22")
    fun standsAtEyeHeightAcrossFromTheLongestWallLookingStraightAtIt() {
        val scene = FloorScene.build(listOf(kitchen), 96, emptyMap())
        val room = scene.rooms.getValue("k")
        val view = checkNotNull(RoomViewpoint.inside(room))
        assertThat(view.eye.y).isWithin(0.001f).of(66 * m)
        assertThat(view.target.y).isWithin(0.001f).of(66 * m)
        // The 14' north wall (edge 0, z smallest) is the first longest wall; the camera looks at its middle.
        val minZ = room.footprint.minOf { it.second }
        val minX = room.footprint.minOf { it.first }
        assertThat(view.target.z).isWithin(0.001f).of(minZ)
        assertThat(view.target.x).isWithin(0.001f).of(minX + 84 * m)
        // Straight back from it, 18" short of the opposite wall, still inside the room.
        assertThat(view.eye.x).isWithin(0.001f).of(view.target.x)
        assertThat(view.eye.z - view.target.z).isWithin(0.001f).of(144 * m - RoomViewpoint.BACK_OFF_M)
        assertThat(inside(room, view.eye.x, view.eye.z)).isTrue()
        // Facing north is a heading of 0.
        assertThat(view.yaw).isWithin(0.001f).of(0f)
    }

    @Test
    @AcceptanceCriterion("AC-C22")
    fun anLShapedRoomKeepsTheCameraInsideIt() {
        // 20' along the north, the south-east part cut away; the centre of its bounds is outside the room.
        val l = PlanRoom("l", "Family room", "LIVING_ROOM", listOf(Pt(0, 0), Pt(240, 0), Pt(240, 72), Pt(96, 72), Pt(96, 240), Pt(0, 240)))
        val room = FloorScene.build(listOf(l), 96, emptyMap()).rooms.getValue("l")
        val view = checkNotNull(RoomViewpoint.inside(room))
        assertThat(inside(room, view.eye.x, view.eye.z)).isTrue()
        // Either 20' wall may be the longest; the camera faces it from inside.
        val wallLength = hypot(view.target.x - view.eye.x, view.target.z - view.eye.z)
        assertThat(wallLength).isGreaterThan(0.2f)
    }

    @Test
    @AcceptanceCriterion("AC-C22")
    fun aClosetTooSmallToStepBackInPutsTheCameraHalfwayAcross() {
        val closet = PlanRoom.rectangle("c", "Closet", "CLOSET", 0, 0, 30, 24)
        val room = FloorScene.build(listOf(closet), 96, emptyMap()).rooms.getValue("c")
        val view = checkNotNull(RoomViewpoint.inside(room))
        assertThat(abs(view.eye.z - view.target.z)).isWithin(0.001f).of(24 * m / 2)
    }

    @Test
    @AcceptanceCriterion("AC-C22")
    fun anUpperFloorRoomIsSeenFromItsOwnFloorAndFloorsBelowAreNotRooms() {
        val bedroom = PlanRoom.rectangle("b", "Bedroom", "BEDROOM", 0, 0, 144, 132)
        val scene = FloorScene.stack(
            listOf(
                SceneLayer(StackedFloor("m", 96, 0, ghost = true), listOf(kitchen)),
                SceneLayer(StackedFloor("u", 96, 106, ghost = false), listOf(bedroom)),
            ),
            emptyMap(),
        )
        assertThat(scene.rooms.keys).containsExactly("b")
        val room = scene.rooms.getValue("b")
        assertThat(room.floorY).isWithin(0.001f).of(106 * m)
        assertThat(room.ceilingY).isWithin(0.001f).of(202 * m)
        val view = checkNotNull(RoomViewpoint.inside(room))
        assertThat(view.eye.y).isWithin(0.001f).of((106 + 66) * m)
        // The footprint sits where the mesh drew the bedroom's floor.
        val floor = scene.mesh.parts.first { it.key == MeshPartKey.Floor("b") }.bounds()
        assertThat(room.footprint.minOf { it.first }).isWithin(0.001f).of(floor.minX)
        assertThat(room.footprint.maxOf { it.second }).isWithin(0.001f).of(floor.maxZ)
    }

    @Test
    @AcceptanceCriterion("AC-C22")
    fun dragLooksAroundWithThePictureFollowingTheFingerAndStopsShortOfStraightUp() {
        val room = FloorScene.build(listOf(kitchen), 96, emptyMap()).rooms.getValue("k")
        val look = LookAround(checkNotNull(RoomViewpoint.inside(room)), room, degreesPerPx = 0.1f)
        val start = look.eye
        // Dragging left by 900 px turns the camera 90° to the right: from north to east (+x).
        look.drag(-900f, 0f)
        assertThat(look.yaw).isWithin(0.001f).of((PI / 2).toFloat())
        assertThat(look.target.x - look.eye.x).isWithin(0.001f).of(1f)
        assertThat(look.eye).isEqualTo(start)
        // Dragging down looks up, never past 80°.
        look.drag(0f, 5_000f)
        assertThat(look.pitch).isWithin(0.001f).of((80 * PI / 180).toFloat())
        assertThat(look.target.y).isGreaterThan(look.eye.y)
        look.reset()
        assertThat(look.yaw).isWithin(0.001f).of(0f)
        assertThat(look.pitch).isEqualTo(0f)
    }

    @Test
    @AcceptanceCriterion("AC-C22")
    fun pinchStepsCloserButNeverThroughTheWall() {
        val room = FloorScene.build(listOf(kitchen), 96, emptyMap()).rooms.getValue("k")
        val view = checkNotNull(RoomViewpoint.inside(room))
        val look = LookAround(view, room)
        val wallZ = room.footprint.minOf { it.second }
        // Spreading the fingers to twice their distance steps 1.5 m toward the north wall.
        look.zoom(200f, 400f)
        assertThat(view.eye.z - look.eye.z).isWithin(0.001f).of(LookAround.METERS_PER_DOUBLING)
        // Keep spreading: the camera stops short of the wall and stays inside.
        repeat(20) { look.zoom(200f, 800f) }
        assertThat(look.eye.z - wallZ).isAtLeast(LookAround.WALL_MARGIN_M)
        assertThat(inside(room, look.eye.x, look.eye.z)).isTrue()
        // Pinching steps back.
        val near = look.eye.z
        look.zoom(400f, 200f)
        assertThat(look.eye.z).isGreaterThan(near)
        // Nonsense separations are ignored.
        val before = look.eye
        look.zoom(0f, 100f)
        look.zoom(Float.NaN, 100f)
        assertThat(look.eye).isEqualTo(before)
        look.reset()
        assertThat(look.eye).isEqualTo(view.eye)
    }
}
