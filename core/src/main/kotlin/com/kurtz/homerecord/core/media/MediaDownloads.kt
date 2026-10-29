package com.kurtz.homerecord.core.media

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.photo.OnPhone
import com.kurtz.homerecord.core.sync.TenantRef
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Brings a photo or voice memo onto this phone when only its record is here: pulled from another phone (path "" and
 * `r2Key` set) or dropped from the download cache. The bytes go through [cache] and the row's local path is set
 * without queuing a sync, since the path describes this phone only (cr:sync-local-columns, AC-F55). One download runs
 * at a time, so a grid asking for the same photo twice fetches it once. File checks run on [io] (S7).
 */
class MediaDownloads(
    private val db: HomeRecordDatabase,
    private val api: MediaTransferApi,
    private val cache: MediaCache,
    private val tenant: () -> TenantRef?,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val lock = Mutex()

    suspend fun isOnPhone(path: String?): Boolean = !path.isNullOrEmpty() && withContext(io) { File(path).isFile }

    /** The paths among [paths] whose bytes are not on this phone. */
    suspend fun missing(paths: Collection<String?>): Set<String?> = withContext(io) {
        paths.filterTo(mutableSetOf()) { it.isNullOrEmpty() || !File(it).isFile }
    }

    suspend fun photo(id: String): OnPhone = lock.withLock {
        val row = db.photoDao().get(id) ?: return OnPhone.Failed
        if (isOnPhone(row.path)) return OnPhone.Ready
        fetch(row.r2Key) { db.photoDao().setLocalPath(id, it) }
    }

    suspend fun voiceMemo(id: String): OnPhone = lock.withLock {
        val row = db.noteDao().get(id) ?: return OnPhone.Failed
        if (isOnPhone(row.voicePath)) return OnPhone.Ready
        fetch(row.r2Key) { db.noteDao().setVoicePath(id, it) }
    }

    private suspend fun fetch(key: String?, save: suspend (String) -> Unit): OnPhone {
        if (key == null) return OnPhone.NotUploaded
        val t = tenant() ?: return OnPhone.NoNetwork
        var offline = false
        val file = cache.open(key) {
            when (val r = api.presignGet(t, key)) {
                is ApiResult.Ok -> r.value
                is ApiResult.Err -> { offline = r.failure == ApiFailure.Offline; null }
            }
        } ?: return if (offline) OnPhone.NoNetwork else OnPhone.Failed
        save(file.absolutePath)
        return OnPhone.Ready
    }
}
