package com.kurtz.homerecord.housemodel.scene

import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.PlanOpening
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/** The 3D view's geometry (AC-C20): walls extruded from the plan, openings, floors, units and orientation. */
class PlanMeshBuilderTest {
    private val builder = PlanMeshBuilder()
    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 144, 120)

    private fun m(inches: Double) = inches * 0.0254
    private fun sqm(sqIn: Double) = sqIn * 0.0254 * 0.0254

    @AcceptanceCriterion("AC-C20")
    @Test fun rectangleGivesFourWallFacesAShellAndAFloor() {
        val mesh = builder.build(listOf(kitchen), wallHeightIn = 96)
        (0..3).forEach { i ->
            assertEquals(2, mesh.part(MeshPartKey.WallFace("k", i))?.triangleCount)
        }
        assertNotNull(mesh.part(MeshPartKey.Shell("k")))
        assertEquals(2, mesh.part(MeshPartKey.Floor("k"))?.triangleCount)
        assertNull("no windows, no glass", mesh.part(MeshPartKey.Glass("k")))
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun wallsRiseToTheFloorsCeilingHeightInMetersAndTheFootprintIsCentered() {
        val mesh = builder.build(listOf(kitchen), wallHeightIn = 96)
        val b = mesh.bounds
        assertEquals(0.0, b.minY.toDouble(), 1e-5)
        assertEquals(m(96.0), b.maxY.toDouble(), 1e-5)
        // Exterior walls stand half their thickness outside the plan line.
        assertEquals(m(72.0 + 2.25), b.maxX.toDouble(), 1e-5)
        assertEquals(-m(72.0 + 2.25), b.minX.toDouble(), 1e-5)
        assertEquals(m(60.0 + 2.25), b.maxZ.toDouble(), 1e-5)
        assertEquals(0.0, b.centerX.toDouble(), 1e-5)
        assertEquals(0.0, b.centerZ.toDouble(), 1e-5)
        assertEquals(-72f, mesh.offsetXIn, 1e-4f)
        assertEquals(-60f, mesh.offsetZIn, 1e-4f)
    }

    @Test fun aRaisedFloorSitsAtItsElevation() {
        val mesh = builder.build(listOf(kitchen), wallHeightIn = 96, baseYIn = 108)
        assertEquals(m(108.0), mesh.bounds.minY.toDouble(), 1e-5)
        assertEquals(m(204.0), mesh.bounds.maxY.toDouble(), 1e-5)
    }

    @Test fun planYBecomesWorldZSoTheTopOfThePlanIsNorth() {
        // Edge 0 runs along the top of the plan (y = 0): its face sits at the most negative z.
        val mesh = builder.build(listOf(kitchen), wallHeightIn = 96)
        val north = mesh.part(MeshPartKey.WallFace("k", 0)).must()
        val zs = (2 until north.positions.size step 3).map { north.positions[it].toDouble() }
        zs.forEach { assertEquals(-m(60.0 - 2.25), it, 1e-5) }
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun insideFacesPointIntoTheRoomForEitherCornerOrder() {
        val clockwise = kitchen.copy(id = "cw", points = kitchen.points.reversed())
        listOf(kitchen, clockwise).forEach { room ->
            val mesh = builder.build(listOf(room), wallHeightIn = 96)
            room.points.indices.forEach { i ->
                val face = mesh.part(MeshPartKey.WallFace(room.id, i)).must()
                for (v in 0 until face.vertexCount) {
                    val x = face.positions[v * 3]
                    val z = face.positions[v * 3 + 2]
                    val towardCenter = -x * face.normals[v * 3] - z * face.normals[v * 3 + 2]
                    assertTrue("${room.id} wall $i faces into the room", towardCenter > 0)
                }
            }
        }
    }

    @Test fun everyTriangleWindsCounterClockwiseSeenFromItsNormal() {
        val house = listOf(
            kitchen.copy(
                openings = listOf(
                    PlanOpening("d", 2, OpeningKind.DOOR, 40, 32, 80),
                    PlanOpening("w", 0, OpeningKind.WINDOW, 30, 36, 48),
                ),
            ),
            PlanRoom("l", "Den", "OTHER", listOf(Pt(144, 0), Pt(300, 0), Pt(300, 200), Pt(220, 200), Pt(220, 120), Pt(144, 120))),
        )
        val mesh = builder.build(house, wallHeightIn = 96)
        mesh.parts.forEach { part ->
            val p = part.positions
            for (t in 0 until part.triangleCount) {
                val (a, b, c) = Triple(part.indices[t * 3], part.indices[t * 3 + 1], part.indices[t * 3 + 2])
                val ux = p[b * 3] - p[a * 3]; val uy = p[b * 3 + 1] - p[a * 3 + 1]; val uz = p[b * 3 + 2] - p[a * 3 + 2]
                val vx = p[c * 3] - p[a * 3]; val vy = p[c * 3 + 1] - p[a * 3 + 1]; val vz = p[c * 3 + 2] - p[a * 3 + 2]
                val cx = uy * vz - uz * vy; val cy = uz * vx - ux * vz; val cz = ux * vy - uy * vx
                val dot = cx * part.normals[a * 3] + cy * part.normals[a * 3 + 1] + cz * part.normals[a * 3 + 2]
                assertTrue("${part.key} triangle $t faces its normal", dot > 0)
            }
            for (v in 0 until part.vertexCount) {
                val n = part.normals
                val len = sqrt(n[v * 3] * n[v * 3] + n[v * 3 + 1] * n[v * 3 + 1] + n[v * 3 + 2] * n[v * 3 + 2])
                assertEquals(1f, len, 1e-4f)
            }
        }
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun wallPhotoMapsAcrossTheInsideFaceTopDownFromTheFirstCorner() {
        val mesh = builder.build(listOf(kitchen), wallHeightIn = 96)
        val face = mesh.part(MeshPartKey.WallFace("k", 0)).must()
        // Edge 0 runs west to east, so u grows with x; v is 0 at the top of the wall and 1 at the floor.
        for (v in 0 until face.vertexCount) {
            val x = face.positions[v * 3]
            val y = face.positions[v * 3 + 1]
            val u = face.uvs[v * 2]
            val tv = face.uvs[v * 2 + 1]
            val expectedU = (x + m(72.0 - 2.25)) / m(144.0 - 4.5)
            assertEquals(expectedU, u.toDouble(), 1e-4)
            assertEquals(1 - y / m(96.0), tv.toDouble(), 1e-4)
        }
        assertEquals(0f, face.uvs.filterIndexed { i, _ -> i % 2 == 0 }.min(), 1e-5f)
        assertEquals(1f, face.uvs.filterIndexed { i, _ -> i % 2 == 0 }.max(), 1e-5f)
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun wallPhotoIsNotMirroredWhenTheRoomsCornersRunCounterClockwise() {
        // R10b: the same kitchen stored the other way round; its north wall is edge 2, running east to west. Facing it
        // from inside, the photo's left edge is still the west end, so u grows with x.
        val ccw = kitchen.copy(points = kitchen.points.reversed())
        val face = builder.build(listOf(ccw), wallHeightIn = 96).part(MeshPartKey.WallFace("k", 2)).must()
        for (v in 0 until face.vertexCount) {
            val x = face.positions[v * 3]
            val z = face.positions[v * 3 + 2]
            assertTrue("north wall", z < 0)
            assertEquals((x + m(72.0 - 2.25)) / m(144.0 - 4.5), face.uvs[v * 2].toDouble(), 1e-4)
        }
        // Every wall of both windings reads left to right from inside: the east wall's u grows southward (+z).
        val east = builder.build(listOf(ccw), wallHeightIn = 96).part(MeshPartKey.WallFace("k", 1)).must()
        val eastCw = builder.build(listOf(kitchen), wallHeightIn = 96).part(MeshPartKey.WallFace("k", 1)).must()
        listOf(east, eastCw).forEach { f ->
            val byZ = (0 until f.vertexCount).sortedBy { f.positions[it * 3 + 2] }
            assertTrue(f.uvs[byZ.first() * 2] < f.uvs[byZ.last() * 2])
        }
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun aDoorIsAnOpeningFromTheFloor() {
        val withDoor = kitchen.copy(openings = listOf(PlanOpening("d", 0, OpeningKind.DOOR, 56, 32, 80)))
        val face = builder.build(listOf(withDoor), wallHeightIn = 96).part(MeshPartKey.WallFace("k", 0)).must()
        assertEquals(sqm((144 - 4.5) * 96 - 32.0 * 80), area(face), 1e-6)
        // Nothing of the face is below the head between the jambs.
        assertTrue(face.trianglesCentroids().none { (x, y) -> x in m(56.0 - 72)..m(88.0 - 72) && y < m(80.0) })
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun aWindowIsAnOpeningUnderTheHeadFilledWithALighterPane() {
        val withWindow = kitchen.copy(openings = listOf(PlanOpening("w", 0, OpeningKind.WINDOW, 54, 36, 48)))
        val mesh = builder.build(listOf(withWindow), wallHeightIn = 96)
        val face = mesh.part(MeshPartKey.WallFace("k", 0)).must()
        assertEquals(sqm((144 - 4.5) * 96 - 36.0 * 48), area(face), 1e-6)
        val glass = mesh.part(MeshPartKey.Glass("k")).must()
        assertEquals("two-sided pane", 4, glass.triangleCount)
        val ys = (1 until glass.positions.size step 3).map { glass.positions[it].toDouble() }
        assertEquals(m(80.0 - 48), ys.min(), 1e-5)
        assertEquals(m(80.0), ys.max(), 1e-5)
        assertEquals(sqm(36.0 * 48) * 2, area(glass), 1e-6)
    }

    @Test fun openingsStopUnderALowCeiling() {
        val withDoor = kitchen.copy(openings = listOf(PlanOpening("d", 0, OpeningKind.DOOR, 56, 32, 80)))
        val face = builder.build(listOf(withDoor), wallHeightIn = 72).part(MeshPartKey.WallFace("k", 0)).must()
        // The door is cut to 68" so a strip of wall stays above it.
        assertEquals(sqm((144 - 4.5) * 72 - 32.0 * 68), area(face), 1e-6)
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun aDoorOnASharedWallOpensBothRoomsFaces() {
        val a = PlanRoom.rectangle("a", "Hall", "HALL", 0, 0, 120, 120)
            .copy(openings = listOf(PlanOpening("d", 1, OpeningKind.DOOR, 40, 32, 80)))
        val b = PlanRoom.rectangle("b", "Den", "OTHER", 120, 0, 120, 120)
        val mesh = builder.build(listOf(a, b), wallHeightIn = 96)
        val full = sqm((120 - 4.5) * 96)
        val door = sqm(32.0 * 80)
        assertEquals(full - door, area(mesh.part(MeshPartKey.WallFace("a", 1)).must()), 1e-6)
        // Den's west wall is edge 3 (bottom-left to top-left).
        assertEquals(full - door, area(mesh.part(MeshPartKey.WallFace("b", 3)).must()), 1e-6)
        assertEquals(full, area(mesh.part(MeshPartKey.WallFace("b", 1)).must()), 1e-6)
    }

    @Test fun aSharedWallHasNoOutsideFaceAndIsHalfAWallFromEachSide() {
        val a = PlanRoom.rectangle("a", "Hall", "HALL", 0, 0, 120, 120)
        val b = PlanRoom.rectangle("b", "Den", "OTHER", 120, 0, 120, 120)
        val mesh = builder.build(listOf(a, b), wallHeightIn = 96)
        // The plan is 240" wide, centered: the shared line is at x = 0.
        val hallShellMaxX = mesh.part(MeshPartKey.Shell("a")).must().positions.filterIndexed { i, _ -> i % 3 == 0 }.max()
        assertEquals(0.0, hallShellMaxX.toDouble(), 1e-5)
        val denShellMinX = mesh.part(MeshPartKey.Shell("b")).must().positions.filterIndexed { i, _ -> i % 3 == 0 }.min()
        assertEquals(0.0, denShellMinX.toDouble(), 1e-5)
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun anLShapedRoomsFloorCoversItsWholeOutline() {
        val l = PlanRoom("l", "Den", "OTHER", listOf(Pt(0, 0), Pt(200, 0), Pt(200, 200), Pt(120, 200), Pt(120, 100), Pt(0, 100)))
        val mesh = builder.build(listOf(l), wallHeightIn = 96)
        val floor = mesh.part(MeshPartKey.Floor("l")).must()
        assertEquals(4, floor.triangleCount)
        assertEquals(sqm(l.areaSqIn().toDouble()), area(floor), 1e-6)
        (0 until floor.vertexCount).forEach { v ->
            assertEquals(0f, floor.positions[v * 3 + 1], 1e-6f)
            assertEquals(1f, floor.normals[v * 3 + 1], 1e-6f)
        }
        assertEquals(6, mesh.parts.count { it.key is MeshPartKey.WallFace })
    }

    @Test fun anInsideCornerWallIsItsLengthLessOneThicknessForEachOutsideCorner() {
        // The L's edge 3 (120,200)->(120,100) meets an outside corner at its first end and an inside corner at the other.
        val l = PlanRoom("l", "Den", "OTHER", listOf(Pt(0, 0), Pt(200, 0), Pt(200, 200), Pt(120, 200), Pt(120, 100), Pt(0, 100)))
        val face = builder.build(listOf(l), wallHeightIn = 96).part(MeshPartKey.WallFace("l", 3)).must()
        assertEquals(sqm((100 - 2.25 + 2.25) * 96), area(face), 1e-6)
    }

    @Test fun nothingToDrawGivesAnEmptyMesh() {
        val line = PlanRoom("x", "Line", "OTHER", listOf(Pt(0, 0), Pt(100, 0), Pt(200, 0)))
        val mesh = builder.build(listOf(line), wallHeightIn = 96)
        assertTrue(mesh.parts.isEmpty())
        assertEquals(MeshBounds.EMPTY, mesh.bounds)
        assertTrue(builder.build(emptyList(), 96).parts.isEmpty())
    }

    @Test fun boundsRadiusCoversTheHouse() {
        val b = builder.build(listOf(kitchen), wallHeightIn = 96).bounds
        val dx = b.maxX - b.minX
        val dy = b.maxY - b.minY
        val dz = b.maxZ - b.minZ
        assertEquals(sqrt(dx * dx + dy * dy + dz * dz) / 2, b.radius, 1e-5f)
    }

    private fun MeshPart?.must(): MeshPart {
        assertNotNull("part missing", this)
        return requireNotNull(this)
    }

    private fun area(part: MeshPart): Double {
        var sum = 0.0
        val p = part.positions
        for (t in 0 until part.triangleCount) {
            val a = part.indices[t * 3]; val b = part.indices[t * 3 + 1]; val c = part.indices[t * 3 + 2]
            val ux = (p[b * 3] - p[a * 3]).toDouble(); val uy = (p[b * 3 + 1] - p[a * 3 + 1]).toDouble(); val uz = (p[b * 3 + 2] - p[a * 3 + 2]).toDouble()
            val vx = (p[c * 3] - p[a * 3]).toDouble(); val vy = (p[c * 3 + 1] - p[a * 3 + 1]).toDouble(); val vz = (p[c * 3 + 2] - p[a * 3 + 2]).toDouble()
            val cx = uy * vz - uz * vy; val cy = uz * vx - ux * vz; val cz = ux * vy - uy * vx
            sum += sqrt(cx * cx + cy * cy + cz * cz) / 2
        }
        return abs(sum)
    }

    /** (x, y) of each triangle's centroid. */
    private fun MeshPart.trianglesCentroids(): List<Pair<Double, Double>> = (0 until triangleCount).map { t ->
        val ids = (0..2).map { indices[t * 3 + it] }
        ids.sumOf { positions[it * 3].toDouble() } / 3 to ids.sumOf { positions[it * 3 + 1].toDouble() } / 3
    }
}
