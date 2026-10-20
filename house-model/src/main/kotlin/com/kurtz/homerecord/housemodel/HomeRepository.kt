package com.kurtz.homerecord.housemodel

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.housemodel.plan.PlanCodec
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.core.data.RoomEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** A room that exists but has no shape on the plan yet. */
data class UnplacedRoom(val id: String, val name: String, val kind: String)

data class FloorPlan(val placed: List<PlanRoom>, val unplaced: List<UnplacedRoom>)

/** A floor with the number of rooms on it, for the floor list (AC-C3). */
data class FloorSummary(val id: String, val name: String, val ceilingHeightIn: Int, val halfLevel: Boolean, val roomCount: Int)

/** New-home answers from onboarding (AC-C1). */
data class NewHome(
    val name: String,
    val address: String?,
    val type: HouseType,
    val foundation: Foundation,
    val attachedGarage: Boolean,
    /** False for "Skip for now": a single empty Main floor instead of the house type's floors. */
    val seedFromTemplate: Boolean = true,
)

/**
 * Homes, floors and the rooms they start with. [onChange] is called with the entity name and id after every write so
 * the sync outbox can pick it up.
 */
class HomeRepository(
    private val db: HomeRecordDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    /** The device's home. House Manual holds one home until Pro lifts the limit (T032). */
    fun observeHome(): Flow<HomeEntity?> = db.homeDao().observeAll().map { it.firstOrNull() }

    fun observeFloors(homeId: String): Flow<List<FloorSummary>> =
        combine(db.floorDao().observeFor(homeId), db.roomDao().observeCounts()) { floors, counts ->
            val byFloor = counts.associate { it.floorId to it.rooms }
            floors.map { FloorSummary(it.id, it.name, it.ceilingHeightIn, it.halfLevel, byFloor[it.id] ?: 0) }
        }

    /** Creates the home, its seeded floors and, with an attached garage, a Garage room on the right floor. */
    suspend fun createHome(new: NewHome): String {
        val at = now()
        val template = if (new.seedFromTemplate) {
            HouseTypeTemplate.forHome(new.type, new.foundation, new.attachedGarage)
        } else {
            HouseTemplate(listOf(FloorTemplate("Main", 0)), garageFloorIndex = null)
        }
        val homeId = Ids.newId()
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
                    name = f.name,
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
                db.roomDao().upsert(RoomEntity(id = roomId, floorId = floorId, name = "Garage", kind = "GARAGE", createdAt = at, updatedAt = at))
                onChange("room", roomId)
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

    suspend fun loadPlan(floorId: String): FloorPlan = toFloorPlan(db.roomDao().listFor(floorId))

    private fun toFloorPlan(rows: List<RoomEntity>): FloorPlan {
        val placed = mutableListOf<PlanRoom>()
        val unplaced = mutableListOf<UnplacedRoom>()
        rows.forEach { row ->
            val points = PlanCodec.decode(row.polygonJson)
            if (points.isEmpty()) unplaced += UnplacedRoom(row.id, row.name, row.kind)
            else placed += PlanRoom(row.id, row.name, row.kind, points)
        }
        return FloorPlan(placed, unplaced)
    }

    /**
     * Saves the editor's rooms for [floorId]. Rooms that were shaped before and are missing now are soft deleted;
     * rooms that never had a shape are left alone.
     */
    suspend fun savePlan(floorId: String, rooms: List<PlanRoom>) {
        val at = now()
        val existing = db.roomDao().listFor(floorId).associateBy { it.id }
        val keep = rooms.map { it.id }.toSet()
        existing.values.filter { it.id !in keep && PlanCodec.decode(it.polygonJson).isNotEmpty() }.forEach {
            db.roomDao().softDelete(it.id, at)
            onChange("room", it.id)
        }
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

    suspend fun roomCount(floorId: String): Int = db.roomDao().listFor(floorId).size

    /** Soft deletes the floor and its rooms. */
    suspend fun deleteFloor(floorId: String) {
        val at = now()
        db.roomDao().listFor(floorId).forEach {
            db.roomDao().softDelete(it.id, at)
            onChange("room", it.id)
        }
        db.floorDao().softDelete(floorId, at)
        onChange("floor", floorId)
    }

    private companion object {
        const val STORY_IN = 108
    }
}
