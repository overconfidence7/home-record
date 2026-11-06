package com.kurtz.homerecord.core.media

import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.photo.OnPhone
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * House Manual's photo files in the cloud (T075a, AC-H15, AC-C17). After each sync of a signed-in home, [afterSync]
 * queues every file of that home that has no R2 key yet (item and room photos, wall photos as taken and their
 * straightened copies) and fetches the item photos taken on another phone. Running it after a sync, not when a photo
 * is saved, means photos taken while signed out go up after the first sign-in, and nothing is queued for a home the
 * server does not know yet (the sync registers it first). The upload queue skips a file it already holds, so a pass
 * that finds nothing new costs a query and one file check per photo. Wall photos taken elsewhere come down when
 * their room page opens ([MediaDownloads.wallPhotos]).
 */
class HomeMedia(
    private val db: HomeRecordDatabase,
    private val enqueue: suspend (tenant: TenantRef, entity: String, entityId: String, filename: String, path: String, mime: String) -> Unit,
    private val isOnPhone: suspend (String?) -> Boolean,
    private val fetchPhoto: suspend (photoId: String) -> OnPhone,
) {
    private val lock = Mutex()

    /** Queues the home's unsent files, then fetches its missing photos; returns what it did. */
    suspend fun afterSync(homeId: String): Pass = lock.withLock {
        Pass(queued = queueUnsent(homeId), fetched = fetchMissingPhotos(homeId))
    }

    private suspend fun queueUnsent(homeId: String): Int {
        val tenant = TenantRef(TenantKind.Home, homeId)
        var queued = 0
        for (photo in db.photoDao().listFor(homeId)) {
            if (photo.r2Key != null || photo.jobId != null || !isOnPhone(photo.path)) continue
            enqueue(tenant, PHOTO, photo.id, photo.path.substringAfterLast('/'), photo.path, WallPhotoFiles.mime(photo.path))
            queued++
        }
        for (wall in db.wallPhotoDao().listForHome(homeId)) {
            if (wall.r2Key == null && isOnPhone(wall.originalPath)) {
                enqueue(tenant, WallPhotoFiles.ENTITY, wall.id, WallPhotoFiles.originalName(wall.originalPath), wall.originalPath, WallPhotoFiles.mime(wall.originalPath))
                queued++
            }
            val texture = wall.texturePath
            if (wall.textureR2Key == null && texture != null && isOnPhone(texture)) {
                enqueue(tenant, WallPhotoFiles.ENTITY, wall.id, WallPhotoFiles.TEXTURE, texture, "image/jpeg")
                queued++
            }
        }
        return queued
    }

    private suspend fun fetchMissingPhotos(homeId: String): Int {
        var fetched = 0
        for (photo in db.photoDao().listFor(homeId)) {
            if (photo.r2Key == null || isOnPhone(photo.path)) continue
            when (fetchPhoto(photo.id)) {
                OnPhone.Ready -> fetched++
                // No network: the rest would fail the same way; the next sync tries again.
                OnPhone.NoNetwork -> break
                else -> {}
            }
        }
        return fetched
    }

    /** [queued] files handed to the upload queue (already queued ones included) and [fetched] photos brought down. */
    data class Pass(val queued: Int, val fetched: Int)

    private companion object {
        const val PHOTO = "photo"
    }
}
