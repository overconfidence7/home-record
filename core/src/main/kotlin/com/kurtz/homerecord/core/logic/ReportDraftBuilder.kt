package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.DailyReport
import com.kurtz.homerecord.core.model.EntryKind
import com.kurtz.homerecord.core.model.LogEntry
import com.kurtz.homerecord.core.model.Source

/** Prefills a daily report from a day's log entries (AC-67). Pure. */
object ReportDraftBuilder {
    /** That day's NOTE entries, oldest first, joined by newlines. */
    fun workCompleted(entries: List<LogEntry>, day: String): String =
        entries.filter { it.day == day && it.kind == EntryKind.NOTE }
            .sortedBy { it.createdAt }
            .mapNotNull { it.text?.trim()?.takeIf { t -> t.isNotEmpty() } }
            .joinToString("\n")

    /** That day's PHOTO entries, oldest first. */
    fun photos(entries: List<LogEntry>, day: String): List<LogEntry> =
        entries.filter { it.day == day && it.kind == EntryKind.PHOTO && it.mediaPath != null }
            .sortedBy { it.createdAt }

    /** A saved draft always wins; otherwise a fresh draft prefilled from the notes. */
    fun build(
        jobId: String,
        day: String,
        entries: List<LogEntry>,
        existing: DailyReport?,
        id: String,
        now: Long,
        source: Source,
    ): DailyReport = existing ?: DailyReport(
        id = id, jobId = jobId, day = day, workCompleted = workCompleted(entries, day),
        createdAt = now, updatedAt = now, source = source,
    )
}
