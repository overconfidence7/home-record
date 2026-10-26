package com.kurtz.homerecord.core.photo

import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.PhotoEntity

/**
 * Edits to a saved job photo: caption, room and phase tags, the Before mark and delete (AC-F25). Every change bumps
 * `updatedAt` and is queued for sync through [onChange]; delete is soft so sync can carry it.
 */
class PhotoEdits(
    private val db: HomeRecordDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    /** A blank caption clears it. Returns false when the photo is gone. */
    suspend fun setCaption(id: String, caption: String): Boolean =
        update(id) { it.copy(caption = caption.trim().takeIf { c -> c.isNotEmpty() }) }

    suspend fun setTags(id: String, roomId: String?, phase: String?): Boolean =
        update(id) { it.copy(roomId = roomId, stage = phase?.trim()?.takeIf { p -> p.isNotEmpty() }) }

    /** Marks [id] as a "Before" shot: it stays a plain photo until an After links to it with [JobPhotos.save]'s `beforeOf`. */
    suspend fun markBefore(id: String): Boolean = update(id) { it.copy(stage = BEFORE) }

    suspend fun delete(id: String): Boolean {
        if (db.photoDao().get(id) == null) return false
        db.photoDao().softDelete(id, now())
        onChange("photo", id)
        return true
    }

    private suspend fun update(id: String, change: (PhotoEntity) -> PhotoEntity): Boolean {
        val row = db.photoDao().get(id) ?: return false
        db.photoDao().upsert(change(row).copy(updatedAt = now()))
        onChange("photo", id)
        return true
    }

    companion object {
        const val BEFORE = "Before"
    }
}
