package com.kurtz.homerecord.core.photo

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.model.GeoFix
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The chips above the shutter (AC-F24): remembered between shots. [roomId] and [phase] may be unset. */
data class CaptureTags(val jobId: String, val roomId: String? = null, val phase: String? = null)

/** What the lens limiter decided for this device (AC-F29). */
enum class LensChoice { Back, Front, None }

/** Picks a lens from what the device has, so single-camera emulators and phones still capture (AC-F29). */
object LensLimiter {
    fun choose(hasBack: Boolean, hasFront: Boolean, wantFront: Boolean): LensChoice = when {
        wantFront && hasFront -> LensChoice.Front
        hasBack -> LensChoice.Back
        hasFront -> LensChoice.Front
        else -> LensChoice.None
    }

    /** The flip button shows only when there is something to flip to. */
    fun canFlip(hasBack: Boolean, hasFront: Boolean): Boolean = hasBack && hasFront
}

/** One day of a job's photos, newest day first (AC-F24). */
data class PhotoDay(val day: LocalDate, val photos: List<PhotoEntity>)

object PhotoGrouping {
    fun byDay(photos: List<PhotoEntity>, zone: ZoneId = ZoneId.systemDefault()): List<PhotoDay> =
        photos.groupBy { Instant.ofEpochMilli(it.takenAt).atZone(zone).toLocalDate() }
            .map { (day, rows) -> PhotoDay(day, rows.sortedByDescending { it.takenAt }) }
            .sortedByDescending { it.day }
}

/**
 * Saves a captured or imported photo against a job with room, phase, time and fix (AC-F24), queues it for sync
 * through [onChange] and for upload through [queueUpload]. Photos for a job without a house use the job id as `homeId`.
 */
class JobPhotos(
    private val db: HomeRecordDatabase,
    private val queueUpload: suspend (TenantRef, entity: String, entityId: String, filename: String, path: String, mime: String) -> Unit,
    private val companyId: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    /** Returns the new photo id, or null when the job is gone. [beforeOf] marks this as the "after" of that photo (AC-F25). */
    suspend fun save(path: String, tags: CaptureTags, fix: GeoFix?, beforeOf: String? = null, takenAt: Long? = null): String? {
        val job = db.jobDao().get(tags.jobId) ?: return null
        val stamp = now()
        val id = Ids.newId()
        db.photoDao().upsert(
            PhotoEntity(
                id = id, homeId = job.homeId ?: job.id, roomId = tags.roomId, jobId = job.id, stage = tags.phase,
                path = path, takenAt = takenAt ?: stamp, lat = fix?.lat, lng = fix?.lng, beforeOfPhotoId = beforeOf,
                createdAt = stamp, updatedAt = stamp, source = Sources.FIELD_LOG,
            ),
        )
        onChange("photo", id)
        companyId()?.let { queueUpload(TenantRef(TenantKind.Company, it), "photo", id, path.substringAfterLast('/'), path, "image/jpeg") }
        return id
    }

    /**
     * Adds a location found after the shot was saved, so a photo is never held back waiting for GPS (AC-F24).
     * A row that already has a fix, or has been deleted, is left alone.
     */
    suspend fun addFix(ids: List<String>, fix: GeoFix) {
        ids.forEach { id ->
            if (db.photoDao().setFixIfMissing(id, fix.lat, fix.lng, now()) > 0) onChange("photo", id)
        }
    }

    fun observeForJob(jobId: String): Flow<List<PhotoEntity>> = db.photoDao().observeForJob(jobId)
}
