package com.kurtz.homerecord.core.photo

import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobFileEntity
import java.util.UUID

/** What the Files tab shows for one folder: sub-folder names and the files directly inside it. */
data class FolderListing(val folder: String, val folders: List<String>, val files: List<JobFileEntity>)

/**
 * Job files with folders (AC-F23). A folder is the `folder` path on its files ("Permits/2026"); an empty folder is kept
 * as a marker row with mime [FOLDER_MIME]. Every change is queued for sync through [onChange]; delete is soft.
 */
class JobFiles(
    private val db: HomeRecordDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    /** Adds a file whose bytes are already stored at [path]. The upload to R2 follows through the media queue. */
    suspend fun add(jobId: String, folder: String, name: String, path: String, mime: String, size: Long, author: String?): JobFileEntity {
        val t = now()
        val row = JobFileEntity(
            id = newId(), jobId = jobId, folder = clean(folder), name = name.trim(), path = path, mime = mime, size = size,
            author = author, offline = true, createdAt = t, updatedAt = t,
        )
        save(row)
        return row
    }

    /** Returns false for a blank name or a folder that already exists. */
    suspend fun createFolder(jobId: String, parent: String, name: String): Boolean {
        val full = join(clean(parent), name.trim().replace("/", " ").trim())
        if (full.isEmpty() || folderNames(jobId).contains(full)) return false
        val t = now()
        save(JobFileEntity(id = newId(), jobId = jobId, folder = full, name = MARKER, mime = FOLDER_MIME, createdAt = t, updatedAt = t))
        return true
    }

    suspend fun rename(id: String, name: String): Boolean {
        val clean = name.trim()
        if (clean.isEmpty()) return false
        return update(id) { it.copy(name = clean) }
    }

    suspend fun move(id: String, folder: String): Boolean = update(id) { it.copy(folder = clean(folder)) }

    /** "Available offline" (AC-F20): the caller downloads the bytes and passes the local [path] when turning it on. */
    suspend fun setOffline(id: String, offline: Boolean, path: String? = null): Boolean =
        update(id) { it.copy(offline = offline, path = if (offline) path ?: it.path else null) }

    /** Records where the bytes sit on this phone and whether they are kept offline; a null [path] means not on the phone. */
    suspend fun setLocal(id: String, offline: Boolean, path: String?): Boolean = update(id) { it.copy(offline = offline, path = path) }

    /** Remembers the R2 key once the upload finishes, so other phones (and this one after Free up) can download it. */
    suspend fun setKey(id: String, r2Key: String): Boolean = update(id) { it.copy(r2Key = r2Key) }

    suspend fun delete(id: String): Boolean {
        if (db.jobFileDao().get(id) == null) return false
        db.jobFileDao().softDelete(id, now())
        onChange("job_file", id)
        return true
    }

    suspend fun list(jobId: String, folder: String): FolderListing {
        val here = clean(folder)
        val rows = db.jobFileDao().listFor(jobId)
        val subs = rows.map { it.folder }.filter { it.startsWith(prefix(here)) && it != here }
            .map { it.removePrefix(prefix(here)).substringBefore('/') }.distinct().sorted()
        val files = rows.filter { it.folder == here && it.mime != FOLDER_MIME }.sortedBy { it.name.lowercase() }
        return FolderListing(here, subs, files)
    }

    /** Search by file name across all folders (AC-F23). */
    suspend fun search(jobId: String, query: String): List<JobFileEntity> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return db.jobFileDao().listFor(jobId).filter { it.mime != FOLDER_MIME && it.name.lowercase().contains(q) }.sortedBy { it.name.lowercase() }
    }

    private suspend fun folderNames(jobId: String) = db.jobFileDao().listFor(jobId).map { it.folder }.toSet()

    private suspend fun update(id: String, change: (JobFileEntity) -> JobFileEntity): Boolean {
        val row = db.jobFileDao().get(id) ?: return false
        save(change(row).copy(updatedAt = now()))
        return true
    }

    private suspend fun save(row: JobFileEntity) {
        db.jobFileDao().upsert(row)
        onChange("job_file", row.id)
    }

    private fun clean(folder: String) = folder.split('/').map { it.trim() }.filter { it.isNotEmpty() }.joinToString("/")
    private fun join(parent: String, name: String) = if (name.isEmpty()) "" else if (parent.isEmpty()) name else "$parent/$name"
    private fun prefix(folder: String) = if (folder.isEmpty()) "" else "$folder/"

    companion object {
        const val FOLDER_MIME = "inode/directory"
        private const val MARKER = ".folder"
    }
}
