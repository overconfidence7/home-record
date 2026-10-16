package com.kurtz.homerecord.core.repo

import androidx.room.withTransaction
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toDomain
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.model.Photo
import com.kurtz.homerecord.core.model.PhotoStage
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.model.Wall
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Behind-the-wall capture (F12): walls per room and the BEHIND_WALL photos taken against them. */
class BehindWallRepository(
    private val db: HomeRecordDatabase,
    private val source: Source,
    private val entries: LogEntryRepository,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    fun observeRooms(homeId: String): Flow<List<Room>> =
        db.roomDao().observeForHome(homeId).map { list -> list.map { it.toDomain() } }

    fun observeWalls(roomId: String): Flow<List<Wall>> =
        db.wallDao().observeForRoom(roomId).map { list -> list.map { it.toDomain() } }

    fun observePhotos(homeId: String): Flow<List<Photo>> =
        db.photoDao().observeForHome(homeId).map { list -> list.map { it.toDomain() } }

    /** AC-63: the wall in [roomId] with this label (case-insensitive), created as a Wall row if new. Blank returns null. */
    suspend fun ensureWall(roomId: String, label: String): Wall? {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return null
        return db.withTransaction {
            val existing = db.wallDao().getAll().firstOrNull { it.roomId == roomId && it.label.equals(trimmed, ignoreCase = true) }
            if (existing != null) return@withTransaction existing.toDomain()
            val now = clock.now()
            val wall = Wall(ids.next(), roomId, trimmed, now, now, source)
            db.wallDao().upsert(wall.toEntity())
            wall
        }
    }

    /** AC-64: a BEHIND_WALL Photo with room and wall, plus the day's PHOTO LogEntry for the same file. */
    suspend fun addPhoto(jobId: String, homeId: String, roomId: String, wallId: String, mediaPath: String): Photo =
        db.withTransaction {
            val now = clock.now()
            val photo = Photo(
                id = ids.next(), homeId = homeId, roomId = roomId, wallId = wallId, stage = PhotoStage.BEHIND_WALL,
                takenAt = now, path = mediaPath, createdAt = now, updatedAt = now, source = source,
            )
            db.photoDao().upsert(photo.toEntity())
            entries.addPhoto(jobId, mediaPath, roomId)
            photo
        }
}
