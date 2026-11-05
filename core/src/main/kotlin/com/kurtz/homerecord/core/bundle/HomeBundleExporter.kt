package com.kurtz.homerecord.core.bundle

import com.kurtz.homerecord.core.sync.LocalColumns
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/** Rows of a table whose [column] is one of [values], soft-deleted ones included ([com.kurtz.homerecord.core.sync.RoomRowStore.readWhere]). */
fun interface HomeRowQuery {
    suspend fun rows(table: String, column: String, values: Collection<String>): List<JsonObject>
}

/** A home ready to write as a `.homerecord`: the bundle and, for each media name, the file on this phone. */
data class ExportedHome(val bundle: HomeBundle, val mediaFiles: Map<String, File>) {
    fun mediaFile(name: String): File? = mediaFiles[name]
}

/**
 * Collects one home's records for a `.homerecord` bundle (AC-C46, AC-F51), straight from the Room tables so every
 * column travels (no per-entity mapper to forget a field), the house model's tables walked from the home down:
 * floors, rooms, walls, wall photos, openings, stairs, overlays, items and their warranty, consumables, service
 * records, panel, breakers and breaker links, documents, photos, contacts and reminders, plus the change orders of the
 * jobs on this home that the client signed.
 *
 * Device-local columns (a file's `path`, every `*Path`, [LocalColumns]) are not copied: each one that names a file
 * on this phone becomes an entry in the record's [MEDIA_FIELD] object (column → media name) and the file goes under
 * `media/`, so the importing phone can point the column at its own copy.
 */
class HomeBundleExporter(private val query: HomeRowQuery, private val now: () -> Long = System::currentTimeMillis) {

    suspend fun export(homeId: String, source: String): ExportedHome? {
        val records = linkedMapOf<String, List<JsonObject>>()
        suspend fun take(table: String, column: String, values: Collection<String>): List<JsonObject> {
            val rows = if (values.isEmpty()) emptyList() else query.rows(table, column, values)
            records[table] = (records[table].orEmpty() + rows).distinctBy { it.id() }
            return rows
        }

        val home = take("home", "id", listOf(homeId)).firstOrNull() ?: return null
        if (home.isDeleted()) return null
        val floorIds = take("floor", "homeId", listOf(homeId)).ids()
        val roomIds = take("room", "floorId", floorIds).ids()
        val wallIds = take("wall", "roomId", roomIds).ids()
        take("wall_photo", "roomId", roomIds)
        take("wall_photo", "wallId", wallIds)
        take("opening", "wallId", wallIds)
        take("stair", "roomId", roomIds)
        take("overlay", "homeId", listOf(homeId))
        val itemIds = take("item", "homeId", listOf(homeId)).ids()
        take("warranty", "itemId", itemIds)
        take("consumable", "itemId", itemIds)
        take("service_record", "itemId", itemIds)
        val panelIds = take("panel", "itemId", itemIds).ids()
        val breakerIds = take("breaker", "panelId", panelIds).ids()
        take("breaker_link", "breakerId", breakerIds)
        take("document", "homeId", listOf(homeId))
        take("photo", "homeId", listOf(homeId))
        take("contact", "homeId", listOf(homeId))
        take("maintenance_task", "homeId", listOf(homeId))
        val jobIds = query.rows("job", "homeId", listOf(homeId)).ids()
        val signed = if (jobIds.isEmpty()) emptyList() else query.rows("change_order", "jobId", jobIds).filter { it.string("status") == SIGNED }
        records["change_order"] = signed

        val mediaFiles = linkedMapOf<String, File>()
        val bundleRecords = records.filterValues { it.isNotEmpty() }.mapValues { (table, rows) ->
            rows.map { row -> BundleRecord(portable(table, row, mediaFiles)) }
        }
        return ExportedHome(
            bundle = HomeBundle(
                source = source,
                exportedAt = now(),
                homeId = homeId,
                records = bundleRecords,
                media = mediaFiles.keys.toList(),
            ),
            mediaFiles = mediaFiles,
        )
    }

    companion object {
        /** The record field naming its media: column (e.g. `path`, `originalPath`) → name under `media/`. */
        const val MEDIA_FIELD = "_media"
        private const val SIGNED = "SIGNED"

        /**
         * [row] as it travels: device-local columns out, and each one that names an existing file on this phone
         * recorded in [MEDIA_FIELD] (and added to [mediaFiles]). Used for the local side of an import plan too, so
         * both sides of a comparison have the same shape.
         */
        fun portable(table: String, row: JsonObject, mediaFiles: MutableMap<String, File>? = null): JsonObject {
            val media = linkedMapOf<String, JsonPrimitive>()
            for ((column, value) in row) {
                if (!LocalColumns.isLocal(table, column)) continue
                val path = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() } ?: continue
                val file = File(path)
                val name = file.name
                if (!BundleReader.isSafeMediaName(name)) continue
                if (mediaFiles != null) {
                    if (!file.isFile) continue
                    mediaFiles[name] = file
                }
                media[column] = JsonPrimitive(name)
            }
            val kept = LocalColumns.strip(table, row).toMutableMap()
            if (media.isNotEmpty()) kept[MEDIA_FIELD] = JsonObject(media)
            return JsonObject(kept)
        }
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.id(): String = string("id").orEmpty()

private fun JsonObject.isDeleted(): Boolean = (this["deletedAt"] as? JsonPrimitive)?.let { !it.isString && it.content != "null" } == true

private fun List<JsonObject>.ids(): List<String> = map { it.id() }.filter { it.isNotEmpty() }
