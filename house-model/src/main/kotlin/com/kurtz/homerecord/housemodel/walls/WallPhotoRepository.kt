package com.kurtz.homerecord.housemodel.walls

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.data.WallPhotoEntity
import kotlinx.coroutines.flow.Flow
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
 */
class WallPhotoRepository(
    private val db: HomeRecordDatabase,
    private val source: String = Sources.HOUSE_MANUAL,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    /** The wall's photos keyed by stage, the newest at each stage. */
    fun observeByStage(wallId: String): Flow<Map<WallStage, WallPhoto>> = db.wallPhotoDao().observeFor(wallId).map(::latestByStage)

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
        db.wallPhotoDao().upsert(
            WallPhotoEntity(
                id = id, wallId = wallId, stage = stage.name, originalPath = originalPath, texturePath = texturePath,
                cornersJson = outline.encode(), takenAt = takenAt, createdAt = at, updatedAt = at, source = source,
            ),
        )
        onChange(ENTITY, id)
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

        /** The newest photo at each stage; rows with the same stage (two phones before a sync) keep the later one. */
        fun latestByStage(rows: List<WallPhotoEntity>): Map<WallStage, WallPhoto> =
            rows.groupBy { WallStage.fromStored(it.stage) }
                .mapValues { (_, list) -> list.maxWith(compareBy<WallPhotoEntity>({ it.takenAt }, { it.createdAt }, { it.id })).toWallPhoto() }
    }
}
