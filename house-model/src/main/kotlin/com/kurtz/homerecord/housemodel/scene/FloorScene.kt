package com.kurtz.homerecord.housemodel.scene

import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.RoomKind
import com.kurtz.homerecord.housemodel.walls.WallKey
import com.kurtz.homerecord.housemodel.walls.WallStage

/** Extrudes a floor's rooms at a ceiling height; [PlanMeshBuilder] in the app, a stand-in in tests. */
typealias MeshMaker = (rooms: List<PlanRoom>, ceilingHeightIn: Int) -> PlanMesh

val DefaultMeshMaker: MeshMaker = { rooms, ceilingHeightIn -> PlanMeshBuilder().build(rooms, ceilingHeightIn) }

/** Groups room kinds into the 3D view's wall tints, so every kitchen looks alike and neighbours usually differ. */
object RoomTint {
    fun of(kind: String): Int = when (RoomKind.parse(kind)) {
        RoomKind.KITCHEN -> 0
        RoomKind.LIVING_ROOM, RoomKind.DINING_ROOM -> 1
        RoomKind.BEDROOM -> 2
        RoomKind.BATHROOM, RoomKind.LAUNDRY -> 3
        RoomKind.OFFICE -> 4
        RoomKind.GARAGE, RoomKind.BASEMENT, RoomKind.UTILITY, RoomKind.MECHANICAL -> 5
        RoomKind.HALLWAY, RoomKind.CLOSET, RoomKind.MUDROOM -> 6
        RoomKind.PORCH, RoomKind.DECK, RoomKind.ATTIC, RoomKind.OTHER -> 7
    }
}

/**
 * What the 3D view draws for one floor (AC-C20): the extruded [mesh], the pictures of the photographed walls per stage
 * (straightened copy first) and each room's tint for the faces without a picture.
 */
data class FloorScene(
    val mesh: PlanMesh,
    val pictures: Map<WallKey, Map<WallStage, String>> = emptyMap(),
    val tints: Map<String, Int> = emptyMap(),
    val roomCount: Int = 0,
) {
    val isEmpty: Boolean get() = mesh.parts.isEmpty()

    /** Inside wall faces the mesh has, one per room edge. */
    val wallCount: Int get() = mesh.parts.count { it.key is MeshPartKey.WallFace }

    /** The picture to paint on each wall face that has one at [stage]; the rest stay in the room's tint. */
    fun picturesAt(stage: WallStage): Map<MeshPartKey.WallFace, String> =
        mesh.parts.mapNotNull { part ->
            val key = part.key as? MeshPartKey.WallFace ?: return@mapNotNull null
            pictures[WallKey(key.roomId, key.edgeIndex)]?.get(stage)?.let { key to it }
        }.toMap()

    fun tintOf(roomId: String): Int = tints[roomId] ?: RoomTint.of(RoomKind.OTHER.name)

    companion object {
        /** Builds the floor's mesh at [ceilingHeightIn]; call off the main thread, a large floor takes a moment. */
        fun build(
            rooms: List<PlanRoom>,
            ceilingHeightIn: Int,
            pictures: Map<WallKey, Map<WallStage, String>>,
            buildMesh: MeshMaker = DefaultMeshMaker,
        ): FloorScene {
            val mesh = buildMesh(rooms, ceilingHeightIn)
            return FloorScene(mesh, pictures, rooms.associate { it.id to RoomTint.of(it.kind) }, rooms.size)
        }
    }
}
