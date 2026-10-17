package com.kurtz.homerecord.core.bundle

import com.kurtz.homerecord.core.media.MediaFileStore
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a `.homerecord` zip: `record.json` plus the referenced files under `media/`. */
class BundleWriter(private val media: MediaFileStore) {
    /**
     * Streams [bundle] to [out] (not closed). Media that is unsafe, unresolvable or missing on
     * disk is skipped. Returns the relative paths actually included.
     */
    fun write(bundle: RecordBundle, out: OutputStream): List<String> {
        val included = mutableListOf<String>()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(RECORD_ENTRY))
            zip.write(RecordJson.encode(bundle).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for (path in bundle.mediaPaths()) {
                if (!MediaPathSanitizer.isSafe(path)) continue
                val file = media.resolve(path)?.takeIf { it.isFile } ?: continue
                zip.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                included += path
            }
            // ZipOutputStream.use closes `out`; callers pass a stream they own.
        }
        return included
    }

    companion object {
        const val RECORD_ENTRY = "record.json"
    }
}
