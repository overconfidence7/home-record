package com.kurtz.homerecord.core.photo

import com.kurtz.homerecord.core.data.PhotoEntity

/**
 * Chooses the "Before" shot to ghost over the live preview when taking an "After" (AC-F25): a Before that no After
 * points to yet, preferring the selected room, newest first.
 */
object GhostPicker {
    fun pick(photos: List<PhotoEntity>, roomId: String?): PhotoEntity? {
        val paired = photos.mapNotNull { it.beforeOfPhotoId }.toSet()
        val open = photos.filter { it.deletedAt == null && it.stage == PhotoEdits.BEFORE && it.id !in paired }
        if (open.isEmpty()) return null
        val sameRoom = open.filter { it.roomId == roomId }
        return (sameRoom.ifEmpty { open }).maxByOrNull { it.takenAt }
    }
}
