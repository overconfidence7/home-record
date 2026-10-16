package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.LogEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One day of entries for FL-Job: [header] like "Tue, Oct 13". */
data class DayGroup(val day: String, val header: String, val entries: List<LogEntry>)

/** AC-61: groups entries by day, newest day first; inside a day, newest entry first. */
object EntryGrouper {
    fun group(entries: List<LogEntry>, locale: Locale = Locale.getDefault()): List<DayGroup> {
        val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
        return entries.groupBy { it.day }
            .toSortedMap(compareByDescending { it })
            .map { (day, list) ->
                DayGroup(day, headerFor(day, formatter), list.sortedByDescending { it.createdAt })
            }
    }

    private fun headerFor(day: String, formatter: DateTimeFormatter): String =
        runCatching { LocalDate.parse(day).format(formatter) }.getOrDefault(day)

    /** Audio length as m:ss. */
    fun formatDuration(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        return "%d:%02d".format(Locale.US, total / 60, total % 60)
    }
}
