package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.DailyReport
import com.kurtz.homerecord.core.model.EntryKind
import com.kurtz.homerecord.core.model.LogEntry
import com.kurtz.homerecord.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReportDraftBuilderTest {
    private fun entry(id: String, day: String, kind: EntryKind, at: Long, text: String? = null, path: String? = null) =
        LogEntry(id, "j", day, kind, text, path, null, null, at, at, Source.FIELD_LOG)

    private val entries = listOf(
        entry("n2", "2026-10-14", EntryKind.NOTE, 20, text = " Hung drywall "),
        entry("n1", "2026-10-14", EntryKind.NOTE, 10, text = "Framed wall"),
        entry("n3", "2026-10-13", EntryKind.NOTE, 5, text = "Other day"),
        entry("blank", "2026-10-14", EntryKind.NOTE, 30, text = "  "),
        entry("p2", "2026-10-14", EntryKind.PHOTO, 15, path = "media/b.jpg"),
        entry("p1", "2026-10-14", EntryKind.PHOTO, 12, path = "media/a.jpg"),
        entry("v", "2026-10-14", EntryKind.VOICE, 11, path = "media/v.m4a"),
    )

    @Test fun `work completed joins that day's notes oldest first (AC-67)`() {
        assertEquals("Framed wall\nHung drywall", ReportDraftBuilder.workCompleted(entries, "2026-10-14"))
    }

    @Test fun `no notes gives empty work completed`() {
        assertEquals("", ReportDraftBuilder.workCompleted(entries, "2026-10-01"))
    }

    @Test fun `photos are that day's photo entries oldest first (AC-67)`() {
        assertEquals(listOf("p1", "p2"), ReportDraftBuilder.photos(entries, "2026-10-14").map { it.id })
    }

    @Test fun `build prefills a new draft`() {
        val d = ReportDraftBuilder.build("j", "2026-10-14", entries, null, "r1", 99, Source.FIELD_LOG)
        assertEquals("Framed wall\nHung drywall", d.workCompleted)
        assertEquals("r1", d.id)
    }

    @Test fun `build keeps an existing edited draft`() {
        val existing = DailyReport("r0", "j", "2026-10-14", workCompleted = "Mine", createdAt = 1, updatedAt = 1, source = Source.FIELD_LOG)
        assertSame(existing, ReportDraftBuilder.build("j", "2026-10-14", entries, existing, "r1", 99, Source.FIELD_LOG))
    }
}
