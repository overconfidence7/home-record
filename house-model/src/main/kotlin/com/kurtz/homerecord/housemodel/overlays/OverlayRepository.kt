package com.kurtz.homerecord.housemodel.overlays

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.OverlayEntity
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What a new overlay or an edit sets (AC-C26). */
data class OverlayDraft(
    val kind: OverlayKind,
    val points: List<OverlayPoint>,
    val stage: WallStage,
    val label: String = "",
    val note: String? = null,
    val depth: OverlayDepth = OverlayDepth.BEHIND_DRYWALL,
    val photoPath: String? = null,
    val itemId: String? = null,
)

/** Why an overlay was not saved. */
enum class OverlayProblem { NO_POINTS, WALL_GONE, NOT_FOUND }

sealed interface OverlaySave {
    data class Saved(val id: String) : OverlaySave
    data class Refused(val problem: OverlayProblem) : OverlaySave
}

/**
 * Overlays on walls and the floor plan (AC-C25..C28). Wall overlays keep their points in wall space, clamped to 0..1, so
 * they stay put whichever stage's photo is shown (AC-C27); plan overlays keep plan inches. Edits move points and change
 * fields; delete is soft so sync carries it. [onChange] feeds the sync outbox after every write.
 */
class OverlayRepository(
    private val db: HomeRecordDatabase,
    private val source: String = Sources.HOUSE_MANUAL,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    private val dao get() = db.overlayDao()

    fun observeForWall(wallId: String): Flow<List<Overlay>> = dao.observeForWall(wallId).map { rows -> rows.map { it.toOverlay() } }

    fun observeForRoom(roomId: String): Flow<List<Overlay>> = dao.observeForRoom(roomId).map { rows -> rows.map { it.toOverlay() } }

    fun observeForHome(homeId: String): Flow<List<Overlay>> = dao.observeFor(homeId).map { rows -> rows.map { it.toOverlay() } }

    suspend fun get(id: String): Overlay? = dao.get(id)?.toOverlay()

    /** A new overlay on wall [wallId] of the home; its room comes from the wall. */
    suspend fun addOnWall(homeId: String, wallId: String, draft: OverlayDraft): OverlaySave {
        val wall = db.wallDao().get(wallId) ?: return OverlaySave.Refused(OverlayProblem.WALL_GONE)
        return insert(homeId, wall.roomId, wallId, draft)
    }

    /** A new overlay on the floor plan, in plan inches, in room [roomId] when it was drawn inside one. */
    suspend fun addOnPlan(homeId: String, roomId: String?, draft: OverlayDraft): OverlaySave = insert(homeId, roomId, null, draft)

    /** Replaces the overlay's fields and points with [draft]; where it sits (home, room, wall) stays. */
    suspend fun update(id: String, draft: OverlayDraft): OverlaySave {
        val row = dao.get(id) ?: return OverlaySave.Refused(OverlayProblem.NOT_FOUND)
        val points = cleaned(draft.points, row.wallId != null) ?: return OverlaySave.Refused(OverlayProblem.NO_POINTS)
        dao.upsert(row.copy(
            kind = draft.kind.wire,
            label = draft.label.trim(),
            note = draft.note?.trim()?.ifEmpty { null },
            depth = draft.depth.name,
            pointsJson = OverlayPoints.encode(points),
            stage = draft.stage.name,
            photoPath = draft.photoPath,
            itemId = draft.itemId,
            updatedAt = now(),
        ))
        onChange(ENTITY, id)
        return OverlaySave.Saved(id)
    }

    /** Point [index] dragged to [to] (AC-C28). False when the overlay or the point is gone. */
    suspend fun movePoint(id: String, index: Int, to: OverlayPoint): Boolean {
        val row = dao.get(id) ?: return false
        val points = OverlayPoints.decode(row.pointsJson)
        if (index !in points.indices) return false
        val moved = points.toMutableList().also { it[index] = if (row.wallId != null) clamp(to) else to }
        dao.upsert(row.copy(pointsJson = OverlayPoints.encode(moved), updatedAt = now()))
        onChange(ENTITY, id)
        return true
    }

    /** Soft deletes the overlay (AC-C28). */
    suspend fun delete(id: String): Boolean {
        dao.get(id) ?: return false
        dao.softDelete(id, now())
        onChange(ENTITY, id)
        return true
    }

    private suspend fun insert(homeId: String, roomId: String?, wallId: String?, draft: OverlayDraft): OverlaySave {
        val points = cleaned(draft.points, wallId != null) ?: return OverlaySave.Refused(OverlayProblem.NO_POINTS)
        val id = Ids.newId()
        val at = now()
        dao.upsert(OverlayEntity(
            id = id,
            homeId = homeId,
            roomId = roomId,
            wallId = wallId,
            kind = draft.kind.wire,
            label = draft.label.trim(),
            note = draft.note?.trim()?.ifEmpty { null },
            depth = draft.depth.name,
            pointsJson = OverlayPoints.encode(points),
            stage = draft.stage.name,
            photoPath = draft.photoPath,
            itemId = draft.itemId,
            createdAt = at,
            updatedAt = at,
            source = source,
        ))
        onChange(ENTITY, id)
        return OverlaySave.Saved(id)
    }

    private fun cleaned(points: List<OverlayPoint>, onWall: Boolean): List<OverlayPoint>? {
        val finite = points.filter { it.x.isFinite() && it.y.isFinite() }.map { if (onWall) clamp(it) else it }
        // Two taps on the same spot make a point, not a zero-length line.
        val deduped = finite.fold(emptyList<OverlayPoint>()) { acc, p -> if (acc.lastOrNull() == p) acc else acc + p }
        return deduped.ifEmpty { null }
    }

    private fun clamp(p: OverlayPoint) = OverlayPoint(p.x.coerceIn(0f, 1f), p.y.coerceIn(0f, 1f))

    private companion object {
        const val ENTITY = "overlay"
    }
}
