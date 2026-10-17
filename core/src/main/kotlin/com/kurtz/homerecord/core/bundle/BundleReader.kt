package com.kurtz.homerecord.core.bundle

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

data class BundleContents(val record: RecordBundle, val extractedMedia: List<String>)

/** Reads a `.homerecord` zip. Media is extracted into a staging directory, never straight into app storage. */
class BundleReader {
    /**
     * Parses [input] and extracts safe `media/` entries into [stagingDir] (created if needed),
     * keeping their relative paths. Entries with unsafe names are ignored. Throws
     * [BundleFormatException] for anything unreadable or from a newer format.
     */
    fun read(input: InputStream, stagingDir: File): BundleContents {
        var recordText: String? = null
        val extracted = mutableListOf<String>()
        val root = stagingDir.apply { mkdirs() }.canonicalFile
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    when {
                        entry.name == BundleWriter.RECORD_ENTRY -> {
                            recordText = readLimited(zip, MAX_RECORD_BYTES).toString(Charsets.UTF_8)
                        }
                        MediaPathSanitizer.isSafe(entry.name) -> {
                            val target = File(root, entry.name).canonicalFile
                            if (target.parentFile?.parentFile != root) continue
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            extracted += entry.name
                        }
                    }
                }
            }
        } catch (e: ZipException) {
            throw BundleFormatException("This file is not a HomeRecord bundle.", e)
        } catch (e: IOException) {
            throw BundleFormatException("This bundle could not be read.", e)
        }
        val text = recordText ?: throw BundleFormatException("This file is not a HomeRecord bundle (no record.json).")
        return BundleContents(RecordJson.decode(text), extracted)
    }

    private fun readLimited(zip: ZipInputStream, max: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val n = zip.read(chunk)
            if (n < 0) break
            buffer.write(chunk, 0, n)
            if (buffer.size() > max) throw BundleFormatException("This bundle's record is too large.")
        }
        return buffer.toByteArray()
    }

    private companion object {
        const val MAX_RECORD_BYTES = 32 * 1024 * 1024
    }
}
