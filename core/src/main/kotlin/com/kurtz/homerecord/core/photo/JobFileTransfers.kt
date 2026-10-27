package com.kurtz.homerecord.core.photo

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaCache
import com.kurtz.homerecord.core.media.MediaTransferApi
import com.kurtz.homerecord.core.sync.TenantRef
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Where a file's upload to R2 stands, for the row on the Files tab (AC-F19). */
enum class UploadStatus { None, Waiting, Uploading, Failed, Uploaded }

/** Storage the app holds for job files: marked Available offline versus downloads that "Free up" can drop (AC-F20). */
data class FileStorage(val offlineBytes: Long, val cachedBytes: Long) {
    val totalBytes get() = offlineBytes + cachedBytes
}

enum class OnPhone { Ready, NoNetwork, NotUploaded, Failed }

/**
 * Moves job file bytes between the phone and R2 (AC-F19, AC-F20). Files marked Available offline live in [offlineDir];
 * anything else downloaded on open goes through [cache] and can be dropped with [freeUp]. File checks and copies run on
 * [io], so view models can call this from Main (S7).
 */
class JobFileTransfers(
    private val db: HomeRecordDatabase,
    private val api: MediaTransferApi,
    private val cache: MediaCache,
    private val offlineDir: File,
    private val tenant: () -> TenantRef?,
    private val jobFiles: JobFiles,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun uploadStatus(fileId: String): Flow<UploadStatus> =
        db.mediaUploadDao().observeFor("job_file", fileId).map {
            when (it?.state) {
                null -> UploadStatus.None
                "uploading" -> UploadStatus.Uploading
                "failed" -> UploadStatus.Failed
                "done" -> UploadStatus.Uploaded
                else -> UploadStatus.Waiting
            }
        }

    suspend fun storage(): FileStorage = FileStorage(
        offlineBytes = withContext(io) { offlineDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0 },
        cachedBytes = cache.sizeBytes(),
    )

    private suspend fun isOnDisk(path: String?) = path != null && withContext(io) { File(path).isFile }

    /** Makes sure the bytes of [id] are on the phone, downloading them when needed; the viewer reads the row's path afterwards. */
    suspend fun ensureOnPhone(id: String): OnPhone {
        val row = db.jobFileDao().get(id) ?: return OnPhone.Failed
        if (isOnDisk(row.path)) return OnPhone.Ready
        val key = row.r2Key ?: return OnPhone.NotUploaded
        val t = tenant() ?: return OnPhone.NoNetwork
        var offline = false
        val file = cache.open(key) {
            when (val r = api.presignGet(t, key)) {
                is ApiResult.Ok -> r.value
                is ApiResult.Err -> { offline = r.failure == ApiFailure.Offline; null }
            }
        } ?: return if (offline) OnPhone.NoNetwork else OnPhone.Failed
        jobFiles.setLocal(id, row.offline, file.absolutePath)
        return OnPhone.Ready
    }

    /** "Available offline" (AC-F20): on moves a downloaded copy into app storage, off hands it back to the cache. */
    suspend fun setAvailableOffline(id: String, on: Boolean): OnPhone {
        if (on) ensureOnPhone(id).let { if (it != OnPhone.Ready) return it }
        val row = db.jobFileDao().get(id) ?: return OnPhone.Failed
        val src = row.path?.takeIf { isOnDisk(it) }?.let(::File)
        when {
            src == null -> jobFiles.setLocal(id, on, null)
            on && inCache(src) -> {
                val dest = File(offlineDir, "${row.id}_${src.name}")
                withContext(io) { move(src, dest) }
                jobFiles.setLocal(id, true, dest.absolutePath)
            }
            !on && src.parentFile == offlineDir && row.r2Key != null -> {
                val back = cache.fileFor(row.r2Key)
                withContext(io) { move(src, back) }
                jobFiles.setLocal(id, false, back.absolutePath)
            }
            else -> jobFiles.setLocal(id, on, row.path)
        }
        return OnPhone.Ready
    }

    private fun move(src: File, dest: File) {
        src.copyTo(dest, overwrite = true)
        src.delete()
    }

    private fun inCache(f: File) = f.parentFile == cache.fileFor("x").parentFile

    /** The "Free up" button: drops downloads that are not Available offline; returns bytes freed. */
    suspend fun freeUp(): Long {
        val freed = cache.freeUp()
        for (row in db.jobFileDao().withLocalPath()) {
            if (!row.offline && !isOnDisk(row.path)) jobFiles.setLocal(row.id, false, null)
        }
        return freed
    }
}
