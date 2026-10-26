package com.kurtz.homerecord.core.photo

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.PhotoEntity
import org.junit.Test

class GhostPickerTest {
    private fun photo(id: String, room: String? = null, stage: String? = PhotoEdits.BEFORE, at: Long = 1, after: String? = null, deleted: Long? = null) =
        PhotoEntity(id = id, homeId = "h", roomId = room, stage = stage, path = "/m/$id.jpg", takenAt = at, beforeOfPhotoId = after, createdAt = at, updatedAt = at, deletedAt = deleted)

    // AC-F25
    @Test fun noBeforeMeansNoGhost() {
        assertThat(GhostPicker.pick(listOf(photo("a", stage = "Rough-in")), null)).isNull()
    }

    // AC-F25
    @Test fun prefersSelectedRoomThenNewest() {
        val list = listOf(photo("a", "r1", at = 1), photo("b", "r2", at = 5), photo("c", "r1", at = 3))
        assertThat(GhostPicker.pick(list, "r1")?.id).isEqualTo("c")
        assertThat(GhostPicker.pick(list, "r9")?.id).isEqualTo("b")
    }

    // AC-F25
    @Test fun pairedAndDeletedBeforesAreSkipped() {
        val list = listOf(photo("a", at = 1), photo("b", at = 5, deleted = 6), photo("after", stage = "Finish", at = 7, after = "a"))
        assertThat(GhostPicker.pick(list, null)).isNull()
    }
}
