package com.kurtz.homerecord.core.photo

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef

/** Draws [marks] onto the photo at [sourcePath] and writes the result to [destPath]. Returns false when it could not. */
fun interface AnnotatedImageRenderer {
    suspend fun render(sourcePath: String, marks: List<Annotation>, destPath: String): Boolean
}

/**
 * Saves marks drawn on a photo as a new photo next to it, so the original stays untouched (AC-F26). The copy keeps the
 * original's job, room and phase, is queued for sync through [onChange] and for upload through [queueUpload].
 */
class PhotoAnnotator(
    private val db: HomeRecordDatabase,
    private val renderer: AnnotatedImageRenderer,
    private val queueUpload: suspend (TenantRef, entity: String, entityId: String, filename: String, path: String, mime: String) -> Unit,
    private val companyId: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    /** Returns the new photo id, or null when there is nothing to save, the original is gone or the image could not be written. */
    suspend fun saveCopy(originalId: String, marks: List<Annotation>): String? {
        if (marks.isEmpty()) return null
        val original = db.photoDao().get(originalId) ?: return null
        val id = Ids.newId()
        val dir = original.path.substringBeforeLast('/', "")
        val path = (if (dir.isEmpty()) "" else "$dir/") + "$id.jpg"
        if (!renderer.render(original.path, marks, path)) return null
        db.photoDao().upsert(PhotoGallery.copyWithMarks(original, id, path, marks, now()))
        onChange("photo", id)
        companyId()?.let { queueUpload(TenantRef(TenantKind.Company, it), "photo", id, "$id.jpg", path, "image/jpeg") }
        return id
    }
}
