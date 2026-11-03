package com.kurtz.homerecord.fieldlog.log

/** One row the log list has laid out: its key, index and place along the list in pixels. */
data class RevealItem(val key: Any, val index: Int, val offset: Int, val size: Int)

/** What the log list should do to show a section that was just saved. */
sealed interface RevealTarget {
    data object None : RevealTarget
    /** Scroll down by [px] so the section's last line clears the bottom edge. */
    data class ScrollBy(val px: Int) : RevealTarget
    /** The section's text is below the list; put its header at the top. */
    data class ToItem(val index: Int) : RevealTarget
}

/**
 * Where to scroll so a just-saved log section shows its header and its last line (QA F09b). Keys follow the list in
 * [LogScreen]: a section's header is `<key>-h`; a typed section's text row is `<key>`; Work done lists `work-<i>`, or
 * `work-none` when empty. Never scrolls the header above the top edge to reach the last line.
 */
object LogSectionReveal {
    fun keyOf(section: LogTextSection): String = when (section) {
        LogTextSection.WORK_DONE -> "work"
        LogTextSection.VISITORS -> "visitors"
        LogTextSection.SAFETY -> "safety"
        LogTextSection.CLIENT_NOTES -> "client"
    }

    fun target(section: LogTextSection, workCount: Int, visible: List<RevealItem>, viewportStart: Int, viewportEnd: Int): RevealTarget {
        val key = keyOf(section)
        val lastKey = when (section) {
            LogTextSection.WORK_DONE -> if (workCount == 0) "work-none" else "work-${workCount - 1}"
            else -> key
        }
        val header = visible.firstOrNull { it.key == "$key-h" }
        val last = visible.firstOrNull { it.key == lastKey }
        return when {
            last != null -> {
                val overflow = last.offset + last.size - viewportEnd
                val room = header?.let { it.offset - viewportStart } ?: overflow
                val px = minOf(overflow, room)
                if (px > 0) RevealTarget.ScrollBy(px) else RevealTarget.None
            }
            header != null -> if (header.offset > viewportStart) RevealTarget.ToItem(header.index) else RevealTarget.None
            else -> RevealTarget.None
        }
    }
}
