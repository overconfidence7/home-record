package com.kurtz.homerecord.core.bundle

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * The contents of a `.homerecord` file (AC-C46): `record.json` plus the media it names.
 *
 * Records are kept as JSON objects per entity type ("home", "floor", "room", "wall", "item", …) so a
 * reader built for this version tolerates fields and entity types a later writer adds, and the
 * planner can compare and copy them without knowing every column.
 */
data class HomeBundle(
    val formatVersion: Int = FORMAT_VERSION,
    /** The app that wrote it, one of [com.kurtz.homerecord.core.data.Sources]. */
    val source: String,
    val exportedAt: Long,
    val homeId: String,
    /** Entity type → its records, each with at least `id`, `createdAt`, `updatedAt` and `source`. */
    val records: Map<String, List<BundleRecord>>,
    /** Media file names as stored under `media/` in the zip (`<uuid>.<ext>`). */
    val media: List<String> = emptyList(),
) {
    fun count(type: String): Int = records[type]?.count { !it.deleted } ?: 0

    companion object {
        const val FORMAT_VERSION = 2
        const val RECORD_JSON = "record.json"
        const val MEDIA_DIR = "media/"
        const val EXTENSION = "homerecord"
    }
}

/** One entity in a bundle. [fields] is the whole JSON object, `id` and the timestamps included. */
data class BundleRecord(val fields: JsonObject) {
    val id: String get() = string("id") ?: ""
    val createdAt: Long get() = long("createdAt") ?: 0L
    val updatedAt: Long get() = long("updatedAt") ?: 0L
    val source: String? get() = string("source")
    val deleted: Boolean get() = long("deletedAt") != null

    /** The record's content without the bookkeeping fields, for "is this the same record?" checks. */
    fun content(): Map<String, JsonElement> = fields.filterKeys { it !in BOOKKEEPING }

    private fun string(key: String): String? = (fields[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun long(key: String): Long? = (fields[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

    companion object {
        private val BOOKKEEPING = setOf("updatedAt", "source")
    }
}

/** Why a file could not be read as a bundle; the import screen words each one (docs/voice.md). */
sealed interface BundleError {
    /** Written by a newer app: `formatVersion` above [HomeBundle.FORMAT_VERSION]. */
    data class NewerVersion(val version: Int) : BundleError

    /** Not a zip, no `record.json`, or `record.json` is not a bundle. */
    data object NotABundle : BundleError

    /** An entry that would land outside the import folder (`../`, absolute) or outside `media/`. */
    data class UnsafeEntry(val name: String) : BundleError

    /** Bigger than [BundleReader.MAX_BYTES] unpacked. */
    data object TooLarge : BundleError
}

sealed interface BundleReadResult {
    data class Ok(val bundle: HomeBundle) : BundleReadResult
    data class Failed(val error: BundleError) : BundleReadResult
}
