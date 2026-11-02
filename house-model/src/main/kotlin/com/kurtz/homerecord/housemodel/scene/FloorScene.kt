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

/** One floor's rooms placed in the stack ([FloorStack]). */
data class SceneLayer(val floor: StackedFloor, val rooms: List<PlanRoom>)

/**
 * What the 3D view draws (AC-C20, AC-C21): the extruded [mesh] of one floor or of several stacked floors, the pictures
 * of the photographed walls per stage (straightened copy first) and each room's tint for the faces without a picture.
 * Rooms in [ghostRooms] belong to floors below the chosen one and are drawn faded, without photos or glass; [focus] is
 * the solid part the camera frames.
 */
data class FloorScene(
    val mesh: PlanMesh,
    val pictures: Map<WallKey, Map<WallStage, String>> = emptyMap(),
    val tints: Map<String, Int> = emptyMap(),
    val roomCount: Int = 0,
    val ghostRooms: Set<String> = emptySet(),
    val focus: MeshBounds = mesh.bounds,
    /** Floors drawn solid: one, or every floor for All floors. */
    val solidFloors: Int = 1,
) {
    /** Nothing solid to show: the chosen floor has no rooms (faded floors below do not count). */
    val isEmpty: Boolean get() = mesh.parts.none { it.key.roomId !in ghostRooms }

    /** Inside wall faces of the solid floors, one per room edge. */
    val wallCount: Int get() = mesh.parts.count { it.key is MeshPartKey.WallFace && it.key.roomId !in ghostRooms }

    fun isGhost(key: MeshPartKey): Boolean = key.roomId in ghostRooms

    /** The height the camera looks at: the chosen floor's floor, or the middle of the stack for All floors. */
    val lookAtY: Float get() = if (solidFloors > 1) focus.centerY else focus.minY

    /** The picture to paint on each solid wall face that has one at [stage]; the rest stay in the room's tint. */
    fun picturesAt(stage: WallStage): Map<MeshPartKey.WallFace, String> =
        mesh.parts.mapNotNull { part ->
            val key = part.key as? MeshPartKey.WallFace ?: return@mapNotNull null
            if (key.roomId in ghostRooms) return@mapNotNull null
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

        /**
         * Builds several floors into one scene (AC-C21): each floor's mesh is raised to its [StackedFloor.baseYIn] and
         * all of them share one plan origin, the middle of the stack's combined footprint, so rooms that sit over each
         * other in the plans sit over each other in 3D. Off the main thread.
         */
        fun stack(
            layers: List<SceneLayer>,
            pictures: Map<WallKey, Map<WallStage, String>>,
            buildMesh: MeshMaker = DefaultMeshMaker,
        ): FloorScene {
            val usable = layers.flatMap { it.rooms }.filter { it.points.size >= 3 }
            if (usable.isEmpty()) return FloorScene(PlanMesh(emptyList(), MeshBounds.EMPTY, 0f, 0f), solidFloors = layers.count { !it.floor.ghost })
            val offX = -(usable.minOf { r -> r.points.minOf { it.x } } + usable.maxOf { r -> r.points.maxOf { it.x } }) / 2f
            val offZ = -(usable.minOf { r -> r.points.minOf { it.y } } + usable.maxOf { r -> r.points.maxOf { it.y } }) / 2f
            val parts = ArrayList<MeshPart>()
            val solidBounds = ArrayList<MeshBounds>()
            val ghosts = HashSet<String>()
            for (layer in layers) {
                val mesh = buildMesh(layer.rooms, layer.floor.ceilingHeightIn)
                if (mesh.parts.isEmpty()) continue
                val placed = mesh.translated(
                    (offX - mesh.offsetXIn) * M_PER_IN,
                    layer.floor.baseYIn * M_PER_IN,
                    (offZ - mesh.offsetZIn) * M_PER_IN,
                )
                parts += placed.parts
                if (layer.floor.ghost) ghosts += layer.rooms.map { it.id } else solidBounds += placed.bounds
            }
            val all = parts.map { it.bounds() }.fold(null as MeshBounds?) { acc, b -> acc?.union(b) ?: b } ?: MeshBounds.EMPTY
            val focus = solidBounds.reduceOrNull { a, b -> a.union(b) } ?: all
            val solidRooms = layers.filter { !it.floor.ghost }.flatMap { it.rooms }
            return FloorScene(
                mesh = PlanMesh(parts, all, offX, offZ),
                pictures = pictures,
                tints = layers.flatMap { it.rooms }.associate { it.id to RoomTint.of(it.kind) },
                roomCount = solidRooms.size,
                ghostRooms = ghosts,
                focus = focus,
                solidFloors = layers.count { !it.floor.ghost },
            )
        }

        private const val M_PER_IN = 0.0254f
    }
}
