package com.kurtz.homerecord.core.media

import com.kurtz.homerecord.core.data.HomeRecordDatabase

/**
 * Saves the R2 key of a finished upload on its row and queues the row for sync (cr:sync-local-columns), so other
 * phones, and this one after Free up, can fetch the bytes: a job file (AC-F20), a photo or its marked-up copy (AC-F55)
 * a voice memo, a receipt (T060c), and a wall photo or its straightened copy (T048d; [filename] tells which). A row that is gone or an
 * entity without a key column is left alone.
 */
class UploadedKeys(
    private val db: HomeRecordDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    suspend fun record(entity: String, entityId: String, key: String, filename: String = ""): Boolean {
        if (key.isEmpty()) return false
        val saved = when (entity) {
            "job_file" -> db.jobFileDao().get(entityId)?.takeIf { it.r2Key != key }?.let { db.jobFileDao().upsert(it.copy(r2Key = key, updatedAt = now())) }
            "photo" -> db.photoDao().get(entityId)?.takeIf { it.r2Key != key }?.let { db.photoDao().upsert(it.copy(r2Key = key, updatedAt = now())) }
            "material" -> db.materialDao().get(entityId)?.takeIf { it.r2Key != key }?.let { db.materialDao().upsert(it.copy(r2Key = key, updatedAt = now())) }
            "note" -> db.noteDao().get(entityId)?.takeIf { it.r2Key != key }?.let { db.noteDao().upsert(it.copy(r2Key = key, updatedAt = now())) }
            "wall_photo" -> db.wallPhotoDao().get(entityId)?.let { row ->
                if (WallPhotoFiles.isTexture(filename)) {
                    row.takeIf { it.textureR2Key != key }?.let { db.wallPhotoDao().upsert(it.copy(textureR2Key = key, updatedAt = now())) }
                } else {
                    row.takeIf { it.r2Key != key }?.let { db.wallPhotoDao().upsert(it.copy(r2Key = key, updatedAt = now())) }
                }
            }
            else -> null
        } != null
        if (saved) onChange(entity, entityId)
        return saved
    }
}
