package com.kurtz.homerecord.housemodel.walls

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.data.WallPhotoEntity
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** A wall by its room and plan edge, the way the 3D mesh names wall faces. */
data class WallKey(val roomId: String, val edgeIndex: Int)

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
 *
 * [sweep] gets the files of photos replaced or deleted here, after the write, to delete the ones no live row still uses
 * ([ReplacedFiles], R10c).
 */
class WallPhotoRepository(
    private val db: HomeRecordDatabase,
    private val source: String = Sources.HOUSE_MANUAL,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
    private val upload: suspend (WallPhotoEntity) -> Unit = {},
    private val fetchMissing: suspend (List<WallPhotoEntity>) -> Unit = {},
    private val sweep: suspend (paths: List<String?>) -> Unit = {},
) {
    /** The wall's photos keyed by stage, the newest at each stage. */
    fun observeByStage(wallId: String): Flow<Map<WallStage, WallPhoto>> = db.wallPhotoDao().observeFor(wallId).map(::latestByStage)

    /** The room's walls with a picture per photographed stage, following plan edits and new photos (AC-C17). */
    fun observeRoomWalls(roomId: String): Flow<List<RoomWall>> =
        combine(db.wallDao().observeFor(roomId), withMissingFetched(db.wallPhotoDao().observeForRoom(roomId)), RoomWalls::build)
            .distinctUntilChanged()

    /**
     * The pictures the 3D view paints on the walls of [roomIds] (AC-C20): for each wall that has a photo, keyed by its
     * room and plan edge, the straightened copy (else the photo as taken) at each photographed stage. Follows new photos
     * and fetches missing files like the room page.
     */
    fun observeWallPictures(roomIds: List<String>): Flow<Map<WallKey, Map<WallStage, String>>> {
        if (roomIds.isEmpty()) return flowOf(emptyMap())
        val perRoom = roomIds.distinct().map { roomId ->
            combine(db.wallDao().observeFor(roomId), withMissingFetched(db.wallPhotoDao().observeForRoom(roomId))) { walls, photos ->
                val byWall = photos.groupBy { it.wallId }
                walls.mapNotNull { wall ->
                    val pictures = latestRowByStage(byWall[wall.id].orEmpty()).mapValues { (_, p) -> p.texturePath ?: p.originalPath }
                    if (pictures.isEmpty()) null else WallKey(wall.roomId, wall.edgeIndex) to pictures
                }
            }
        }
        return combine(perRoom) { rooms -> rooms.flatMap { it.toList() }.toMap() }.distinctUntilChanged()
    }

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
     * picture; any earlier photo of that wall at that stage is removed, and its files with it unless another live photo
     * shares them. Returns the new photo's id, or null when the wall
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
        if (replaced.isNotEmpty()) sweep(replaced.flatMap { listOf(it.originalPath, it.texturePath) })
        return id
    }

    /**
     * Makes the straightened copies of photos [ids] again after a plan save cut or stretched their outlines (R10a). Each
     * copy gets its own file (two parts of a split wall share one photo), named after the row. A photo whose file is not
     * on this phone yet, or whose outline changed again meanwhile, is skipped; the walls show the photo until then.
     */
    suspend fun remakeTextures(ids: List<String>, maker: WallTextureMaker) {
        for (id in ids) {
            val row = db.wallPhotoDao().get(id) ?: continue
            if (row.originalPath.isBlank() || row.texturePath != null) continue
            val outline = WallOutline.decode(row.cornersJson) ?: continue
            val wall = db.wallDao().get(row.wallId) ?: continue
            val dest = row.originalPath.substringBeforeLast('.', row.originalPath) + "-" + row.id.take(8) + "-wall.jpg"
            val texture = try {
                maker.make(row.originalPath, outline, wall.lengthIn, wall.heightIn, dest)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wall texture remake failed", e)
                null
            } ?: continue
            val current = db.wallPhotoDao().get(id) ?: continue
            if (current.cornersJson != row.cornersJson || current.texturePath != null) continue
            val updated = current.copy(texturePath = texture, updatedAt = now())
            db.wallPhotoDao().upsert(updated)
            onChange(ENTITY, id)
            upload(updated)
        }
    }

    /**
     * Follows a plan save or floor delete (R10a, R10d): makes the recut photos' straightened copies again, then deletes
     * the files the write dropped that no live row uses. The sweep waits for the remake so a new copy is never written
     * beside a file still being cleaned up, and runs even when a remake fails (the walls show the photo meanwhile).
     */
    suspend fun afterPlanChange(changes: PlanPhotoChanges, maker: WallTextureMaker) {
        try {
            remakeTextures(changes.recutIds, maker)
        } finally {
            if (changes.droppedFiles.isNotEmpty()) withContext(NonCancellable) { sweep(changes.droppedFiles) }
        }
    }

    /** Deletes the photo, and its files unless another live photo shares them. */
    suspend fun delete(id: String) {
        val row = db.wallPhotoDao().get(id) ?: return
        db.wallPhotoDao().softDelete(id, now())
        onChange(ENTITY, id)
        sweep(listOf(row.originalPath, row.texturePath))
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
