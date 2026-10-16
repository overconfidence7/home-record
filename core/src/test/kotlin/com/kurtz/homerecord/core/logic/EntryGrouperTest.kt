package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.EntryKind
import com.kurtz.homerecord.core.model.LogEntry
import com.kurtz.homerecord.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class EntryGrouperTest {
    private fun entry(id: String, day: String, at: Long) =
        LogEntry(id, "j", day, EntryKind.NOTE, text = id, createdAt = at, updatedAt = at, source = Source.FIELD_LOG)

    @Test fun `groups by day newest first with readable headers (AC-61)`() {
        val groups = EntryGrouper.group(
            listOf(entry("a", "2026-10-12", 1), entry("b", "2026-10-13", 2), entry("c", "2026-10-13", 3)),
            Locale.US,
        )
        assertEquals(listOf("Tue, Oct 13", "Mon, Oct 12"), groups.map { it.header })
        assertEquals(listOf("c", "b"), groups[0].entries.map { it.id })
    }

    @Test fun `empty input gives no groups`() = assertTrue(EntryGrouper.group(emptyList()).isEmpty())

    @Test fun `bad day string falls back to raw text`() {
        assertEquals("nope", EntryGrouper.group(listOf(entry("a", "nope", 1)), Locale.US).single().header)
    }

    @Test fun `formats durations as m ss`() {
        assertEquals("0:05", EntryGrouper.formatDuration(5_400))
        assertEquals("3:00", EntryGrouper.formatDuration(180_000))
        assertEquals("0:00", EntryGrouper.formatDuration(-5))
    }
}
