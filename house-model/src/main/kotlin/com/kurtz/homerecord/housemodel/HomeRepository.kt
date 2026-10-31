package com.kurtz.homerecord.housemodel

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.OpeningEntity
import com.kurtz.homerecord.core.data.StairEntity
import com.kurtz.homerecord.core.data.WallEntity
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.plan.OpeningKind
import com.kurtz.homerecord.housemodel.plan.PlanCodec
import com.kurtz.homerecord.housemodel.plan.PlanOpening
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.PlanStair
import com.kurtz.homerecord.housemodel.plan.SavedWall
import com.kurtz.homerecord.housemodel.plan.WallDeriver
import com.kurtz.homerecord.housemodel.plan.WallMatcher
import com.kurtz.homerecord.core.data.RoomEntity
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** A room that exists but has no shape on the plan yet. */
data class UnplacedRoom(val id: String, val name: String, val kind: String)

data class FloorPlan(val placed: List<PlanRoom>, val unplaced: List<UnplacedRoom>)

/** A floor with the number of rooms on it, for the floor list (AC-C3). */
data class FloorSummary(val id: String, val name: String, val ceilingHeightIn: Int, val halfLevel: Boolean, val roomCount: Int, val elevationIn: Int = 0)

/** The floor a screen opens on: the one with rooms, else the entrance level (lowest at or above grade), never the basement. */
fun List<FloorSummary>.defaultFloor(): FloorSummary? =
    firstOrNull { it.roomCount > 0 }
        ?: filter { it.elevationIn >= 0 }.minByOrNull { it.elevationIn }
        ?: firstOrNull()

/** New-home answers from onboarding (AC-C1). */
data class NewHome(
    val name: String,
    val address: String?,
    val type: HouseType,
    val foundation: Foundation,
    val attachedGarage: Boolean,
    /** False for "Skip for now": the house type's floors, but no seeded rooms (PRODUCT.md step 1). */
    val seedFromTemplate: Boolean = true,
)

/**
 * Homes, floors and the rooms they start with. [strings] names the seeded floors and Garage in the device's language.
 * [onChange] is called with the entity name and id after every write so the sync outbox can pick it up.
 */
class HomeRepository(
    private val db: HomeRecordDatabase,
    private val strings: (Int) -> String,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    /** The device's home. House Manual holds one home until Pro lifts the limit (T032). */
    fun observeHome(): Flow<HomeEntity?> = db.homeDao().observeAll().map { it.firstOrNull() }

    fun observeFloors(homeId: String): Flow<List<FloorSummary>> =
        combine(db.floorDao().observeFor(homeId), db.roomDao().observeCounts()) { floors, counts ->
            val byFloor = counts.associate { it.floorId to it.rooms }
            floors.map { FloorSummary(it.id, it.name, it.ceilingHeightIn, it.halfLevel, byFloor[it.id] ?: 0, it.elevationIn) }
        }

    /** Creates the home, its seeded floors and, with an attached garage, a Garage room on the right floor. */
    suspend fun createHome(new: NewHome): String {
        val at = now()
        val seeded = HouseTypeTemplate.forHome(new.type, new.foundation, new.attachedGarage)
        val template = if (new.seedFromTemplate) seeded else seeded.copy(garageFloorIndex = null)
        val homeId = Ids.newId()
        // One transaction: observers see the home with all its floors or nothing.
        db.withTransaction {
            db.homeDao().upsert(
                HomeEntity(
                    id = homeId,
                    name = new.name.trim(),
                    address = new.address?.trim()?.takeIf { it.isNotEmpty() },
                    houseType = new.type.name,
                    foundation = new.foundation.name,
                    attachedGarage = new.attachedGarage,
                    createdAt = at,
                    updatedAt = at,
                ),
            )
            onChange("home", homeId)
            template.floors.forEachIndexed { index, f ->
                val floorId = Ids.newId()
                db.floorDao().upsert(
                    FloorEntity(
                        id = floorId,
                        homeId = homeId,
                        sortOrder = index,
                        name = strings(f.role.nameRes),
                        ceilingHeightIn = f.ceilingHeightIn,
                        halfLevel = f.halfLevel,
                        elevationIn = f.elevationIn,
                        createdAt = at,
                        updatedAt = at,
                    ),
                )
                onChange("floor", floorId)
                if (index == template.garageFloorIndex) {
                    val roomId = Ids.newId()
                    db.roomDao().upsert(RoomEntity(id = roomId, floorId = floorId, name = strings(R.string.room_kind_garage), kind = "GARAGE", createdAt = at, updatedAt = at))
                    onChange("room", roomId)
                }
            }
        }
        return homeId
    }

    /** Adds a floor above the existing ones. */
    suspend fun addFloor(homeId: String, name: String): String {
        val at = now()
        val floors = db.floorDao().listFor(homeId)
        val id = Ids.newId()
        db.floorDao().upsert(
            FloorEntity(
                id = id,
                homeId = homeId,
                sortOrder = (floors.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                name = name.trim(),
                elevationIn = (floors.maxOfOrNull { it.elevationIn } ?: -STORY_IN) + STORY_IN,
                createdAt = at,
                updatedAt = at,
            ),
        )
        onChange("floor", id)
        return id
    }

    suspend fun updateFloor(floorId: String, name: String, ceilingHeightIn: Int) {
        val floor = db.floorDao().get(floorId) ?: return
        db.floorDao().upsert(floor.copy(name = name.trim(), ceilingHeightIn = ceilingHeightIn, updatedAt = now()))
        onChange("floor", floorId)
    }

    /** Swaps the floor with its neighbour; [delta] is -1 to move down the list, 1 to move up. */
    suspend fun moveFloor(homeId: String, floorId: String, delta: Int) {
        val floors = db.floorDao().listFor(homeId).toMutableList()
        val from = floors.indexOfFirst { it.id == floorId }
        val to = from + delta
        if (from < 0 || to !in floors.indices) return
        floors.add(to, floors.removeAt(from))
        val at = now()
        floors.forEachIndexed { index, f ->
            if (f.sortOrder != index) {
                db.floorDao().upsert(f.copy(sortOrder = index, updatedAt = at))
                onChange("floor", f.id)
            }
        }
    }


    /** Rooms on [floorId] split into those with a shape and those still waiting to be placed (AC-C6, AC-F48). */
    fun observePlan(floorId: String): Flow<FloorPlan> = db.roomDao().observeFor(floorId).map { rows -> toFloorPlan(rows) }

    /** One placed room by id, or null when it is gone or has no shape yet. */
    suspend fun findRoom(roomId: String): PlanRoom? {
        val row = db.roomDao().get(roomId) ?: return null
        return loadPlan(row.floorId).placed.firstOrNull { it.id == roomId }
    }

    /** One placed room as the plan changes; emits null once it is deleted or loses its shape (HM-Room). */
    fun observeRoom(roomId: String): Flow<PlanRoom?> = flow {
        val row = db.roomDao().get(roomId)
        if (row == null) emit(null) else emitAll(observePlan(row.floorId).map { plan -> plan.placed.firstOrNull { it.id == roomId } })
    }.distinctUntilChanged()

    /** Where a room lives, for the room page subtitle: its floor id and name plus the home name. */
    suspend fun roomLocation(roomId: String): RoomLocation? {
        val row = db.roomDao().get(roomId) ?: return null
        val floor = db.floorDao().get(row.floorId) ?: return null
        val home = db.homeDao().get(floor.homeId) ?: return null
        return RoomLocation(floor.id, floor.name, home.name)
    }

    suspend fun loadPlan(floorId: String): FloorPlan = toFloorPlan(db.roomDao().listFor(floorId))

    private suspend fun toFloorPlan(rows: List<RoomEntity>): FloorPlan {
        val placed = mutableListOf<PlanRoom>()
        val unplaced = mutableListOf<UnplacedRoom>()
        rows.forEach { row ->
            val points = PlanCodec.decode(row.polygonJson)
            if (points.isEmpty()) {
                unplaced += UnplacedRoom(row.id, row.name, row.kind)
            } else {
                val edgeOfWall = db.wallDao().listFor(row.id).associate { it.id to it.edgeIndex }
                val openings = edgeOfWall.keys.flatMap { wallId -> db.openingDao().listFor(wallId) }.mapNotNull { o ->
                    val edge = edgeOfWall[o.wallId]?.takeIf { it in points.indices } ?: return@mapNotNull null
                    PlanOpening(o.id, edge, OpeningKind.entries.firstOrNull { it.name == o.kind } ?: OpeningKind.DOOR, o.offsetIn, o.widthIn, o.heightIn)
                }
                val stairs = db.stairDao().listFor(row.id).map { PlanStair(it.id, it.toFloorId, PlanCodec.decode(it.polygonJson)) }
                placed += PlanRoom(row.id, row.name, row.kind, points, openings, stairs).clampOpenings()
            }
        }
        return FloorPlan(placed, unplaced)
    }

    /**
     * Writes the walls, doors, windows and stairs of [room]. Every edge has a wall row; a wall keeps its id through a
     * reshape by matching the stored outline to the new one by endpoints (WallMatcher), so its photos stay on it.
     * Walls whose edge is gone are soft deleted with their wall photos and wall overlays, and items on them stay in the
     * room off the wall. Runs before the room row itself is written.
     */
    private suspend fun saveOpeningsAndStairs(room: PlanRoom, allRooms: List<PlanRoom>, ceilingHeightIn: Int, at: Long) {
        val derived = WallDeriver().derive(allRooms).filter { it.roomId == room.id }.associateBy { it.edgeIndex }
        val stored = db.wallDao().listFor(room.id)
        val oldOutline = db.roomDao().get(room.id)?.let { PlanCodec.decode(it.polygonJson) }.orEmpty()
        val match = WallMatcher().match(oldOutline, stored.map { SavedWall(it.id, it.edgeIndex) }, room.points)
        val storedById = stored.associateBy { it.id }
        val existingOpenings = stored.flatMap { db.openingDao().listFor(it.id) }.associateBy { it.id }
        val keepOpenings = room.openings.map { it.id }.toSet()
        existingOpenings.values.filter { it.id !in keepOpenings }.forEach {
            db.openingDao().softDelete(it.id, at)
            onChange("opening", it.id)
        }
        match.removed.forEach { wallId ->
            db.wallPhotoDao().listFor(wallId).forEach { db.wallPhotoDao().softDelete(it.id, at); onChange("wall_photo", it.id) }
            db.overlayDao().listForRoom(room.id).filter { it.wallId == wallId }.forEach { db.overlayDao().softDelete(it.id, at); onChange("overlay", it.id) }
            db.itemDao().listForRoom(room.id).filter { it.wallId == wallId }.forEach {
                db.itemDao().upsert(it.copy(wallId = null, updatedAt = at))
                onChange("item", it.id)
            }
            db.wallDao().softDelete(wallId, at)
            onChange("wall", wallId)
        }
        val wallOfEdge = room.points.indices.associateWith { edge ->
            val d = derived[edge]
            val row = match.wallOfEdge[edge]?.let { storedById[it] }
            val wall = row?.copy(
                edgeIndex = edge, lengthIn = d?.lengthIn ?: row.lengthIn, orientation = d?.orientation ?: row.orientation,
                exterior = d?.exterior ?: row.exterior,
            ) ?: WallEntity(
                id = Ids.newId(), roomId = room.id, edgeIndex = edge, orientation = d?.orientation ?: "N",
                lengthIn = d?.lengthIn ?: room.edgeLength(edge), heightIn = ceilingHeightIn, exterior = d?.exterior ?: false,
                createdAt = at, updatedAt = at,
            )
            if (wall != row) {
                db.wallDao().upsert(wall.copy(updatedAt = at))
                onChange("wall", wall.id)
            }
            wall.id
        }
        room.openings.forEach { o ->
            val wallId = wallOfEdge[o.edgeIndex] ?: return@forEach
            val row = existingOpenings[o.id]
            if (row == null || row.wallId != wallId || row.kind != o.kind.name || row.offsetIn != o.offsetIn || row.widthIn != o.widthIn || row.heightIn != o.heightIn) {
                db.openingDao().upsert(
                    OpeningEntity(
                        id = o.id, wallId = wallId, kind = o.kind.name, offsetIn = o.offsetIn, widthIn = o.widthIn, heightIn = o.heightIn,
                        createdAt = row?.createdAt ?: at, updatedAt = at,
                    ),
                )
                onChange("opening", o.id)
            }
        }
        val existingStairs = db.stairDao().listFor(room.id).associateBy { it.id }
        val keepStairs = room.stairs.map { it.id }.toSet()
        existingStairs.values.filter { it.id !in keepStairs }.forEach {
            db.stairDao().softDelete(it.id, at)
            onChange("stair", it.id)
        }
        room.stairs.forEach { st ->
            val json = PlanCodec.encode(st.points)
            val row = existingStairs[st.id]
            if (row == null || row.toFloorId != st.toFloorId || row.polygonJson != json) {
                db.stairDao().upsert(StairEntity(id = st.id, roomId = room.id, toFloorId = st.toFloorId, polygonJson = json, createdAt = row?.createdAt ?: at, updatedAt = at))
                onChange("stair", st.id)
            }
        }
    }

    /**
     * Saves the editor's rooms for [floorId]. Rooms that were shaped before and are missing now are soft deleted;
     * rooms that never had a shape are left alone. One transaction, so a half-saved plan is never synced.
     */
    suspend fun savePlan(floorId: String, rooms: List<PlanRoom>) {
        db.withTransaction {
            val at = now()
            val existing = db.roomDao().listFor(floorId).associateBy { it.id }
            val keep = rooms.map { it.id }.toSet()
            val ceiling = db.floorDao().get(floorId)?.ceilingHeightIn ?: DEFAULT_CEILING_IN
            existing.values.filter { it.id !in keep && PlanCodec.decode(it.polygonJson).isNotEmpty() }.forEach { deleteRoomRows(it.id, at) }
            rooms.forEach { saveOpeningsAndStairs(it, rooms, ceiling, at) }
            rooms.forEach { room ->
                val json = PlanCodec.encode(room.points)
                val row = existing[room.id]
                if (row == null) {
                    db.roomDao().upsert(RoomEntity(id = room.id, floorId = floorId, name = room.name, kind = room.kind, polygonJson = json, createdAt = at, updatedAt = at))
                } else if (row.name != room.name || row.kind != room.kind || row.polygonJson != json) {
                    db.roomDao().upsert(row.copy(name = room.name, kind = room.kind, polygonJson = json, updatedAt = at))
                } else {
                    return@forEach
                }
                onChange("room", room.id)
            }
        }
    }

    /** Every room of the home, bottom floor first, for pickers. */
    suspend fun roomChoices(homeId: String): List<RoomChoice> =
        db.floorDao().listFor(homeId).flatMap { floor ->
            db.roomDao().listFor(floor.id).map { RoomChoice(it.id, it.name, floor.name) }
        }

    suspend fun roomCount(floorId: String): Int = db.roomDao().listFor(floorId).size

    /** Soft deletes the floor and its rooms with everything on them, and unlinks stairs that led to it, in one transaction. */
    suspend fun deleteFloor(floorId: String) {
        val at = now()
        db.withTransaction {
            db.roomDao().listFor(floorId).forEach { deleteRoomRows(it.id, at) }
            val homeId = db.floorDao().get(floorId)?.homeId
            if (homeId != null) {
                db.roomDao().listForHome(homeId).forEach { room ->
                    db.stairDao().listFor(room.id).filter { it.toFloorId == floorId }.forEach {
                        db.stairDao().upsert(it.copy(toFloorId = null, updatedAt = at))
                        onChange("stair", it.id)
                    }
                }
            }
            db.floorDao().softDelete(floorId, at)
            onChange("floor", floorId)
        }
    }

    /**
     * Soft deletes a room with its walls (their doors, windows and wall photos), stairs, overlays and breaker links
     * (cr:room-delete-cascade). Items in it move to Whole house; photos and documents stay in the home with no room.
     * Callers hold the transaction.
     */
    private suspend fun deleteRoomRows(roomId: String, at: Long) {
        db.wallDao().listFor(roomId).forEach { wall ->
            db.openingDao().listFor(wall.id).forEach { db.openingDao().softDelete(it.id, at); onChange("opening", it.id) }
            db.wallPhotoDao().listFor(wall.id).forEach { db.wallPhotoDao().softDelete(it.id, at); onChange("wall_photo", it.id) }
            db.wallDao().softDelete(wall.id, at)
            onChange("wall", wall.id)
        }
        db.stairDao().listFor(roomId).forEach { db.stairDao().softDelete(it.id, at); onChange("stair", it.id) }
        db.overlayDao().listForRoom(roomId).forEach { db.overlayDao().softDelete(it.id, at); onChange("overlay", it.id) }
        db.breakerLinkDao().listForRoom(roomId).forEach { db.breakerLinkDao().softDelete(it.id, at); onChange("breaker_link", it.id) }
        db.itemDao().listForRoom(roomId).forEach {
            db.itemDao().upsert(it.copy(roomId = PseudoRooms.WHOLE_HOUSE, wallId = null, planX = null, planY = null, updatedAt = at))
            onChange("item", it.id)
        }
        db.photoDao().listForRoom(roomId).forEach { db.photoDao().upsert(it.copy(roomId = null, updatedAt = at)); onChange("photo", it.id) }
        db.documentDao().listForRoom(roomId).forEach { db.documentDao().upsert(it.copy(roomId = null, updatedAt = at)); onChange("document", it.id) }
        db.roomDao().softDelete(roomId, at)
        onChange("room", roomId)
    }

    private companion object {
        const val STORY_IN = 108
        const val DEFAULT_CEILING_IN = 96
    }
}

/** A room as the item form's room picker lists it. */
data class RoomChoice(val id: String, val name: String, val floorName: String)

data class RoomLocation(val floorId: String, val floorName: String, val homeName: String)
