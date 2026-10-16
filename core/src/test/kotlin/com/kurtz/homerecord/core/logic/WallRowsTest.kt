package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Photo
import com.kurtz.homerecord.core.model.PhotoStage
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.model.Wall
import org.junit.Assert.assertEquals
import org.junit.Test

class WallRowsTest {
    private val std = listOf("North", "East", "South", "West")
    private fun wall(id: String, label: String) = Wall(id, "r", label, 0, 0, Source.FIELD_LOG)
    private fun photo(wallId: String?, stage: PhotoStage = PhotoStage.BEHIND_WALL) =
        Photo("p$wallId${stage}${System.nanoTime()}", "h", "r", wallId, stage, 0, null, "media/x.jpg", 0, 0, Source.FIELD_LOG)

    @Test fun `standard walls without rows show zero and no id`() {
        val rows = WallRows.build(std, emptyList(), emptyList())
        assertEquals(listOf("North", "East", "South", "West"), rows.map { it.label })
        assertEquals(listOf(0, 0, 0, 0), rows.map { it.count })
        assertEquals(listOf(null, null, null, null), rows.map { it.wallId })
    }

    @Test fun `counts only behind-wall photos per wall (AC-65)`() {
        val rows = WallRows.build(
            std, listOf(wall("n", "north")),
            listOf(photo("n"), photo("n"), photo("n"), photo("n", PhotoStage.GENERAL), photo(null)),
        )
        val north = rows.first()
        assertEquals("n", north.wallId)
        assertEquals("North", north.label)
        assertEquals(3, north.count)
    }

    @Test fun `custom walls follow the standard ones`() {
        val rows = WallRows.build(std, listOf(wall("c", "Closet back"), wall("e", "East")), listOf(photo("c")))
        assertEquals(listOf("North", "East", "South", "West", "Closet back"), rows.map { it.label })
        assertEquals(1, rows.last().count)
        assertEquals("e", rows[1].wallId)
    }
}
