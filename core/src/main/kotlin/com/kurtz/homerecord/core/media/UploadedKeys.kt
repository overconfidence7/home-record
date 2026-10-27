package com.kurtz.homerecord.core.media

import com.kurtz.homerecord.core.data.HomeRecordDatabase

/**
 * Saves the R2 key of a finished upload on its row and queues the row for sync (cr:sync-local-columns), so other
 * phones, and this one after Free up, can fetch the bytes: a job file (AC-F20), a photo or its marked-up copy (AC-F55)
 * and a voice memo. A row that is gone or an entity without a key column is left alone.
 */
class UploadedKeys(
    private val db: HomeRecordDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    suspend fun record(entity: String, entityId: String, key: String): Boolean {
        if (key.isEmpty()) return false
        val saved = when (entity) {
            "job_file" -> db.jobFileDao().get(entityId)?.takeIf { it.r2Key != key }?.let { db.jobFileDao().upsert(it.copy(r2Key = key, updatedAt = now())) }
            "photo" -> db.photoDao().get(entityId)?.takeIf { it.r2Key != key }?.let { db.photoDao().upsert(it.copy(r2Key = key, updatedAt = now())) }
            "note" -> db.noteDao().get(entityId)?.takeIf { it.r2Key != key }?.let { db.noteDao().upsert(it.copy(r2Key = key, updatedAt = now())) }
            else -> null
        } != null
        if (saved) onChange(entity, entityId)
        return saved
    }
}
