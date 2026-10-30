package com.kurtz.homerecord.core.media

/** The one short size label both apps show for files and storage: decimal units, like the Android file manager. */
object ByteSize {
    fun label(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "${bytes / 1_000} KB"
        else -> "${bytes.coerceAtLeast(0)} B"
    }
}
