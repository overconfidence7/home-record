package com.kurtz.homerecord.core.bundle

import com.kurtz.homerecord.core.media.MediaFileStore

/**
 * Decides which relative paths may travel in a bundle (AC-91). Only direct children of
 * `media/` qualify: no absolute paths, no `..`, no backslashes, no nested directories.
 */
object MediaPathSanitizer {
    fun isSafe(path: String): Boolean {
        val prefix = MediaFileStore.MEDIA_DIR + "/"
        if (!path.startsWith(prefix)) return false
        val name = path.removePrefix(prefix)
        return name.isNotEmpty() &&
            name != "." && name != ".." &&
            '/' !in name && '\\' !in name && '\u0000' !in name
    }
}
