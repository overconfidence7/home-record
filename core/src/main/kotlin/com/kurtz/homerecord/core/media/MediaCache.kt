package com.kurtz.homerecord.core.media

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Maximum size of the download cache; 0 means keep nothing beyond the files marked available offline. */
interface MediaCacheSettings {
    var capBytes: Long

    companion object {
        const val DEFAULT_CAP_BYTES = 500L * 1024 * 1024
    }
}

class PrefsMediaCacheSettings(private val prefs: android.content.SharedPreferences) : MediaCacheSettings {
    override var capBytes: Long
        get() = prefs.getLong(KEY, MediaCacheSettings.DEFAULT_CAP_BYTES)
        set(value) { prefs.edit().putLong(KEY, value.coerceAtLeast(0)).apply() }

    private companion object { const val KEY = "media_cache_cap_bytes" }
}

/**
 * Files downloaded from R2 live in [dir] (AC-F54). Opening a file marks it used; when the folder grows past the
 * cap the least recently used files go first, except those [pinned] (Available offline). "Free up" drops everything unpinned.
 * Every file operation runs on [io], so callers on Main never touch the disk (S7).
 */
class MediaCache(
    private val dir: File,
    private val settings: MediaCacheSettings,
    private val api: MediaTransferApi,
    private val pinned: () -> Set<String> = { emptySet() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    fun fileFor(key: String) = File(dir, key.replace('/', '_'))

    suspend fun sizeBytes(): Long = withContext(io) { files().sumOf { it.length() } }

    /** The cached copy of [key], downloading it from [getUrl] when missing; null when the download fails. */
    suspend fun open(key: String, getUrl: suspend () -> String?): File? {
        val f = fileFor(key)
        val cached = withContext(io) {
            f.exists().also { if (it) f.setLastModified(System.currentTimeMillis()) else dir.mkdirs() }
        }
        if (cached) return f
        val url = getUrl() ?: return null
        if (!api.download(url, f)) { withContext(io) { f.delete() }; return null }
        trim()
        return withContext(io) { f.takeIf { it.exists() } }
    }

    /** Drops least recently used unpinned files until the folder fits the cap; returns bytes freed. */
    suspend fun trim(): Long = withContext(io) { evict(settings.capBytes) }

    /** The "Free up" button: removes every unpinned file; returns bytes freed. */
    suspend fun freeUp(): Long = withContext(io) { evict(0) }

    private fun evict(target: Long): Long {
        val keep = pinned().map { fileFor(it).name }.toSet()
        var size = files().sumOf { it.length() }
        var freed = 0L
        for (f in files().filter { it.name !in keep }.sortedBy { it.lastModified() }) {
            if (size <= target) break
            val n = f.length()
            if (f.delete()) { size -= n; freed += n }
        }
        return freed
    }

    private fun files() = dir.listFiles { f -> f.isFile && !f.name.endsWith(".part") }?.toList().orEmpty()
}
