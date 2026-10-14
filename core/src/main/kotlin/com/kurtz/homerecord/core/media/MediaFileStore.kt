package com.kurtz.homerecord.core.media

import java.io.File
import java.util.UUID

/**
 * App-private media storage under `filesDir/media/`. Paths handed to the database are
 * always relative to the files dir (e.g. `media/<uuid>.jpg`), never absolute.
 */
class MediaFileStore(private val filesDir: File) {
    private val mediaDir: File get() = File(filesDir, MEDIA_DIR)

    /** Relative path for a new media file; the file itself is not created. */
    fun newRelativePath(extension: String): String =
        "$MEDIA_DIR/${UUID.randomUUID()}.${extension.trimStart('.').lowercase()}"

    fun newPhotoPath(): String = newRelativePath("jpg")

    fun newAudioPath(): String = newRelativePath("m4a")

    /**
     * Resolves a stored relative path to a file inside the media dir, creating the
     * parent dir. Returns null for anything that would escape it (absolute paths, `..`).
     */
    fun resolve(relativePath: String): File? {
        if (relativePath.isBlank() || relativePath.startsWith("/") || relativePath.contains('\\')) return null
        val root = mediaDir.canonicalFile
        val file = File(filesDir, relativePath).canonicalFile
        if (file.parentFile != root) return null
        return file
    }

    /** Like [resolve] but also makes sure the media dir exists, for writers. */
    fun resolveForWrite(relativePath: String): File? {
        val file = resolve(relativePath) ?: return null
        mediaDir.mkdirs()
        return file
    }

    fun exists(relativePath: String): Boolean = resolve(relativePath)?.isFile == true

    /** Deletes the file; true if it existed and was removed. */
    fun delete(relativePath: String?): Boolean {
        if (relativePath == null) return false
        val file = resolve(relativePath) ?: return false
        return file.isFile && file.delete()
    }

    companion object {
        const val MEDIA_DIR = "media"
    }
}
