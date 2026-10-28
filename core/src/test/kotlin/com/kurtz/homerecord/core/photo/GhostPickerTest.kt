package com.kurtz.homerecord.core.photo

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.PhotoEntity
import org.junit.Test

class GhostPickerTest {
    private fun photo(id: String, room: String? = null, role: String? = PhotoEdits.BEFORE, at: Long = 1, after: String? = null, deleted: Long? = null, stage: String? = null) =
        PhotoEntity(id = id, homeId = "h", roomId = room, stage = stage, pairRole = role, path = "/m/$id.jpg", takenAt = at, beforeOfPhotoId = after, createdAt = at, updatedAt = at, deletedAt = deleted)

    // AC-F25
    @Test fun noBeforeMeansNoGhost() {
        assertThat(GhostPicker.pick(listOf(photo("a", role = null, stage = "Rough-in")), null)).isNull()
    }

    // AC-F25, cr:fl-before-phase: a phase named "Before" is a phase, not the Before mark.
    @Test fun aPhaseCalledBeforeIsNotABeforeShot() {
        assertThat(GhostPicker.pick(listOf(photo("a", role = null, stage = PhotoEdits.BEFORE)), null)).isNull()
    }

    // AC-F25
    @Test fun newestBeforeInTheSelectedRoom() {
        val list = listOf(photo("a", "r1", at = 1), photo("b", "r2", at = 5), photo("c", "r1", at = 3))
        assertThat(GhostPicker.pick(list, "r1")?.id).isEqualTo("c")
        assertThat(GhostPicker.pick(list, "r2")?.id).isEqualTo("b")
    }

    // AC-F25, cr:fl-ghost-autopair: a Before from another room is never offered.
    @Test fun noBeforeFromAnotherRoom() {
        val list = listOf(photo("a", "r1", at = 1), photo("b", null, at = 5))
        assertThat(GhostPicker.pick(list, "r9")).isNull()
        assertThat(GhostPicker.pick(list, null)?.id).isEqualTo("b")
        assertThat(GhostPicker.pick(listOf(photo("a", "r1")), null)).isNull()
    }

    // AC-F25
    @Test fun pairedAndDeletedBeforesAreSkipped() {
        val list = listOf(photo("a", at = 1), photo("b", at = 5, deleted = 6), photo("after", role = null, stage = "Finish", at = 7, after = "a"))
        assertThat(GhostPicker.pick(list, null)).isNull()
    }
}
