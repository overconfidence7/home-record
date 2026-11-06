package com.kurtz.homerecord.core.bundle

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

/** Writes a `.homerecord` zip (AC-C46): `record.json` first, then each media file under `media/`. */
object BundleWriter {
    /** [mediaFile] maps a name in [HomeBundle.media] to the file on this phone; names it cannot find are left out. */
    fun write(bundle: HomeBundle, out: OutputStream, mediaFile: (String) -> File?) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(HomeBundle.RECORD_JSON))
            zip.write(json.encodeToString(JsonObject.serializer(), toJson(bundle)).toByteArray())
            zip.closeEntry()
            bundle.media.distinct().forEach { name ->
                require(BundleReader.isSafeMediaName(name)) { "unsafe media name" }
                val file = mediaFile(name)?.takeIf { it.isFile } ?: return@forEach
                zip.putNextEntry(ZipEntry(HomeBundle.MEDIA_DIR + name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun toJson(bundle: HomeBundle): JsonObject = buildJsonObject {
        put("formatVersion", bundle.formatVersion)
        put("source", bundle.source)
        put("exportedAt", bundle.exportedAt)
        put("homeId", bundle.homeId)
        put("records", JsonObject(bundle.records.mapValues { (_, list) -> JsonArray(list.map { it.fields }) }))
        put("media", JsonArray(bundle.media.map { JsonPrimitive(it) }))
    }
}

/**
 * Reads a `.homerecord` zip (AC-C46). Media are unpacked into [mediaDir] (a fresh folder per import);
 * nothing is written outside it. A newer `formatVersion` fails cleanly before any media is unpacked,
 * fields and entity types this version does not know are kept as they are.
 */
object BundleReader {
    /** Unpacked size cap: a large home with every wall photographed at three stages stays well under it. */
    const val MAX_BYTES = 2L * 1024 * 1024 * 1024

    /** `record.json` is read into memory, so it gets its own cap far below [MAX_BYTES]; a real home's is a few MB. */
    const val RECORD_MAX_BYTES = 64L * 1024 * 1024

    private val SAFE_MEDIA = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

    fun isSafeMediaName(name: String): Boolean = SAFE_MEDIA.matches(name) && ".." !in name

    fun read(input: InputStream, mediaDir: File, maxBytes: Long = MAX_BYTES, recordMaxBytes: Long = RECORD_MAX_BYTES): BundleReadResult {
        val root = mediaDir.canonicalFile
        val unpacked = mutableListOf<File>()
        var bundle: HomeBundle? = null
        var total = 0L
        fun fail(error: BundleError): BundleReadResult {
            unpacked.forEach { it.delete() }
            return BundleReadResult.Failed(error)
        }
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    when {
                        entry.isDirectory -> if (name != HomeBundle.MEDIA_DIR) return fail(BundleError.UnsafeEntry(name))
                        name == HomeBundle.RECORD_JSON -> {
                            val bytes = zip.readCapped(minOf(recordMaxBytes, maxBytes - total)) ?: return fail(BundleError.TooLarge)
                            total += bytes.size
                            when (val parsed = parse(bytes.decodeToString())) {
                                is BundleReadResult.Ok -> bundle = parsed.bundle
                                is BundleReadResult.Failed -> return fail(parsed.error)
                            }
                        }
                        name.startsWith(HomeBundle.MEDIA_DIR) && isSafeMediaName(name.removePrefix(HomeBundle.MEDIA_DIR)) -> {
                            val target = File(root, name.removePrefix(HomeBundle.MEDIA_DIR)).canonicalFile
                            if (target.parentFile != root) return fail(BundleError.UnsafeEntry(name))
                            root.mkdirs()
                            val written = target.outputStream().use { zip.copyCapped(it, maxBytes - total) }
                            unpacked += target
                            if (written < 0) return fail(BundleError.TooLarge)
                            total += written
                        }
                        // Any other path (../, absolute, a folder that is not media/) is refused, not skipped:
                        // a file that tries it was not written by HomeRecord.
                        else -> return fail(BundleError.UnsafeEntry(name))
                    }
                }
            }
        } catch (_: ZipException) {
            return fail(BundleError.NotABundle)
        } catch (_: IOException) {
            return fail(BundleError.NotABundle)
        }
        val found = bundle ?: return fail(BundleError.NotABundle)
        return BundleReadResult.Ok(found)
    }

    /** Parses `record.json`; exposed for tests and for the share-sheet preview. */
    fun parse(text: String): BundleReadResult {
        val obj = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            ?: return BundleReadResult.Failed(BundleError.NotABundle)
        val version = (obj["formatVersion"] as? JsonPrimitive)?.intOrNull
            ?: return BundleReadResult.Failed(BundleError.NotABundle)
        if (version > HomeBundle.FORMAT_VERSION) return BundleReadResult.Failed(BundleError.NewerVersion(version))
        if (version < HomeBundle.FORMAT_VERSION) return BundleReadResult.Failed(BundleError.NotABundle)
        val homeId = (obj["homeId"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return BundleReadResult.Failed(BundleError.NotABundle)
        val records = (obj["records"] as? JsonObject)?.mapValues { (_, list) ->
            (list as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.let(::BundleRecord) }.filter { it.id.isNotBlank() }
        } ?: return BundleReadResult.Failed(BundleError.NotABundle)
        val media = (obj["media"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .filter(::isSafeMediaName)
        return BundleReadResult.Ok(
            HomeBundle(
                formatVersion = version,
                source = (obj["source"] as? JsonPrimitive)?.content.orEmpty(),
                exportedAt = (obj["exportedAt"] as? JsonPrimitive)?.longOrNull ?: 0L,
                homeId = homeId,
                records = records,
                media = media,
            ),
        )
    }

    /** Reads the rest of the entry, or null past [limit] bytes. */
    private fun InputStream.readCapped(limit: Long): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        return if (copyCapped(buffer, limit) < 0) null else buffer.toByteArray()
    }

    /** Copies the rest of the entry; -1 once more than [limit] bytes have come (a zip bomb stops there). */
    private fun InputStream.copyCapped(out: OutputStream, limit: Long): Long {
        val chunk = ByteArray(64 * 1024)
        var copied = 0L
        while (true) {
            val n = read(chunk)
            if (n < 0) return copied
            copied += n
            if (copied > limit) return -1
            out.write(chunk, 0, n)
        }
    }
}
