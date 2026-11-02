package com.kurtz.homerecord.housemodel.walls

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.data.WallPhotoEntity
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** One wall photo at one stage (AC-C17); [texturePath] is the straightened copy the 3D view uses (AC-C19). */
data class WallPhoto(
    val id: String,
    val wallId: String,
    val stage: WallStage,
    val originalPath: String,
    val texturePath: String?,
    val outline: WallOutline?,
    val takenAt: Long,
)

fun WallPhotoEntity.toWallPhoto(): WallPhoto =
    WallPhoto(id, wallId, WallStage.fromStored(stage), originalPath, texturePath, WallOutline.decode(cornersJson), takenAt)

/**
 * Wall photos per stage. A wall keeps one photo per stage: photographing it again at the same stage replaces the earlier
 * one (the room page and the 3D view show one picture per stage). [onChange] feeds the sync outbox after every write.
 *
 * Files to the cloud (T048d): [upload] queues a new photo's files for R2 (the upload records their keys on the row);
 * [fetchMissing] brings the files of photos taken on another phone onto this one, and runs whenever the room page's
 * photos change, after the page has its rows, so a wall shows its plain tile until the picture arrives.
 */
class WallPhotoRepository(
    private val db: HomeRecordDatabase,
    private val source: String = Sources.HOUSE_MANUAL,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
    private val upload: suspend (WallPhotoEntity) -> Unit = {},
    private val fetchMissing: suspend (List<WallPhotoEntity>) -> Unit = {},
) {
    /** The wall's photos keyed by stage, the newest at each stage. */
    fun observeByStage(wallId: String): Flow<Map<WallStage, WallPhoto>> = db.wallPhotoDao().observeFor(wallId).map(::latestByStage)

    /** The room's walls with a picture per photographed stage, following plan edits and new photos (AC-C17). */
    fun observeRoomWalls(roomId: String): Flow<List<RoomWall>> =
        combine(db.wallDao().observeFor(roomId), withMissingFetched(db.wallPhotoDao().observeForRoom(roomId)), RoomWalls::build)
            .distinctUntilChanged()

    /**
     * Passes [photos] on unchanged and, after each list, fetches the missing files of the photos the page shows (the
     * newest per wall and stage). A fetched file changes the row, the list comes again and the picture appears; a newer
     * list cancels a fetch still running. A failed fetch leaves the plain tile, never an error.
     */
    private fun withMissingFetched(photos: Flow<List<WallPhotoEntity>>): Flow<List<WallPhotoEntity>> = channelFlow {
        photos.collectLatest { rows ->
            send(rows)
            val shown = rows.groupBy { it.wallId }.values.flatMap { latestRowByStage(it).values }
            try {
                fetchMissing(shown)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wall photo download failed", e)
            }
        }
    }

    suspend fun byStage(wallId: String): Map<WallStage, WallPhoto> = latestByStage(db.wallPhotoDao().listFor(wallId))

    /**
     * Saves the photo at [originalPath] outlined by [outline] and straightened into [texturePath] as the wall's [stage]
     * picture; any earlier photo of that wall at that stage is removed. Returns the new photo's id, or null when the wall
     * does not exist (it was deleted while the camera was open).
     */
    suspend fun save(
        wallId: String,
        stage: WallStage,
        originalPath: String,
        texturePath: String?,
        outline: WallOutline,
        takenAt: Long = now(),
    ): String? {
        if (db.wallDao().get(wallId) == null) return null
        val at = now()
        val replaced = db.wallPhotoDao().listFor(wallId).filter { WallStage.fromStored(it.stage) == stage }
        val id = Ids.newId()
        val row = WallPhotoEntity(
            id = id, wallId = wallId, stage = stage.name, originalPath = originalPath, texturePath = texturePath,
            cornersJson = outline.encode(), takenAt = takenAt, createdAt = at, updatedAt = at, source = source,
        )
        db.wallPhotoDao().upsert(row)
        onChange(ENTITY, id)
        upload(row)
        replaced.forEach {
            db.wallPhotoDao().softDelete(it.id, at)
            onChange(ENTITY, it.id)
        }
        return id
    }

    suspend fun delete(id: String) {
        db.wallPhotoDao().get(id) ?: return
        db.wallPhotoDao().softDelete(id, now())
        onChange(ENTITY, id)
    }

    companion object {
        const val ENTITY = "wall_photo"
        private const val TAG = "WallPhotos"

        /** The newest photo at each stage; rows with the same stage (two phones before a sync) keep the later one. */
        fun latestByStage(rows: List<WallPhotoEntity>): Map<WallStage, WallPhoto> = latestRowByStage(rows).mapValues { (_, row) -> row.toWallPhoto() }

        /** [latestByStage] without decoding the outlines, for lists that only show the pictures. */
        fun latestRowByStage(rows: List<WallPhotoEntity>): Map<WallStage, WallPhotoEntity> =
            rows.groupBy { WallStage.fromStored(it.stage) }
                .mapValues { (_, list) -> list.maxWith(compareBy<WallPhotoEntity>({ it.takenAt }, { it.createdAt }, { it.id })) }
    }
}
