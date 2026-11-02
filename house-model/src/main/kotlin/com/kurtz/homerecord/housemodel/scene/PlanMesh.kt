package com.kurtz.homerecord.housemodel.scene

/**
 * What one piece of the 3D house is, so the renderer can pick its material:
 * a room's inside wall face (textured by that wall's stage photo, else the room's color), the rest of a room's shell
 * (outside faces, wall tops, door and window jambs: the room's color), a room's floor (neutral), or its window glass
 * (a lighter panel).
 */
sealed interface MeshPartKey {
    val roomId: String

    /** The inside face of edge [edgeIndex] of the room: the surface a wall photo shows. */
    data class WallFace(override val roomId: String, val edgeIndex: Int) : MeshPartKey

    /** Outside faces of exterior walls, wall tops and opening jambs of one room. */
    data class Shell(override val roomId: String) : MeshPartKey

    data class Floor(override val roomId: String) : MeshPartKey

    /** Every window pane of one room, drawn from both sides. */
    data class Glass(override val roomId: String) : MeshPartKey
}

/**
 * One drawable piece: triangles with per-vertex position (x, y, z in meters, y up), unit normal and texture
 * coordinate. Wall faces map u 0..1 from the wall's first corner to its second and v 0..1 from the top of the wall to
 * the floor, the same orientation as the straightened wall photo bitmap (top-left origin); other parts have planar or
 * zero coordinates. Triangles wind counter-clockwise seen from the side their normal points to.
 */
class MeshPart(
    val key: MeshPartKey,
    val positions: FloatArray,
    val normals: FloatArray,
    val uvs: FloatArray,
    val indices: IntArray,
) {
    val vertexCount get() = positions.size / 3
    val triangleCount get() = indices.size / 3
}

/** Axis-aligned bounds of the whole mesh, in meters. */
data class MeshBounds(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float,
) {
    val centerX get() = (minX + maxX) / 2
    val centerY get() = (minY + maxY) / 2
    val centerZ get() = (minZ + maxZ) / 2

    /** Half the diagonal: the radius a "Reset view" camera keeps the whole floor inside. */
    val radius: Float
        get() {
            val dx = maxX - minX
            val dy = maxY - minY
            val dz = maxZ - minZ
            return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz) / 2
        }

    companion object {
        val EMPTY = MeshBounds(0f, 0f, 0f, 0f, 0f, 0f)
    }
}

/**
 * A floor's rooms as 3D geometry. World axes: x is the plan's x (east), y is up, z is the plan's y (south), so a
 * camera above the house looking down with -z as screen-up sees the blueprint unmirrored. The plan is moved so its
 * footprint is centered on the origin; [offsetXIn] / [offsetZIn] is that shift in plan inches (plan point
 * (x, y) sits at world ((x + offsetXIn) * 0.0254, _, (y + offsetZIn) * 0.0254)).
 */
data class PlanMesh(
    val parts: List<MeshPart>,
    val bounds: MeshBounds,
    val offsetXIn: Float,
    val offsetZIn: Float,
) {
    fun part(key: MeshPartKey): MeshPart? = parts.firstOrNull { it.key == key }
    val triangleCount get() = parts.sumOf { it.triangleCount }
}
