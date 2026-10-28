package com.kurtz.homerecord.core.photo

import com.kurtz.homerecord.core.data.PhotoEntity

/**
 * Chooses the "Before" shot to ghost over the live preview when taking an "After" (AC-F25): a Before that no After
 * points to yet, from the selected room only (a shot with no room matches a Before with no room), newest first.
 * A Before from another room is never offered, so an After is only ever paired on purpose (cr:fl-ghost-autopair).
 */
object GhostPicker {
    fun pick(photos: List<PhotoEntity>, roomId: String?): PhotoEntity? {
        val paired = photos.mapNotNull { it.beforeOfPhotoId }.toSet()
        return photos
            .filter { it.deletedAt == null && it.pairRole == PhotoEdits.BEFORE && it.id !in paired && it.roomId == roomId }
            .maxByOrNull { it.takenAt }
    }
}
