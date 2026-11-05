package com.kurtz.homerecord.core.bundle

import com.kurtz.homerecord.core.data.ImportLedgerDao
import com.kurtz.homerecord.core.sync.RowStore
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

/** What an import would do, for the preview (AC-H17): the plan and the bundle it came from. */
data class ImportPreview(val bundle: HomeBundle, val plan: ImportPlan) {
    /** New and updated records of [type], for the preview's count rows. */
    fun incoming(type: String): Int = plan.added(type) + plan.updated(type)
}

/** What [HomeBundleImporter.apply] wrote. */
data class ImportOutcome(val homeId: String, val written: Int, val mediaCopied: Int)

/**
 * Brings a `.homerecord` into this phone (AC-H17, AC-C47): [preview] plans it against the rows this phone already
 * holds for the bundle's home (in the same portable shape the exporter writes, so an unchanged record compares equal),
 * and [apply] writes the planned records parent-first in one transaction, copies each record's media from the unpacked
 * folder into [mediaDir] and points the record's device-local column at the copy, then stores the import ledger so the
 * same bundle imported again changes nothing.
 *
 * Conflicts keep this phone's record unless the user picked Use imported ([ConflictChoice]).
 */
class HomeBundleImporter(
    private val query: HomeRowQuery,
    private val store: RowStore,
    private val ledger: ImportLedgerDao,
    private val mediaDir: File,
    /** Called for each record written, after the transaction commits, so the app can queue it for sync (r12:import-not-synced). */
    private val onWritten: suspend (type: String, id: String) -> Unit = { _, _ -> },
) {
    suspend fun preview(bundle: HomeBundle): ImportPreview {
        val local = bundle.records.mapValues { (type, records) ->
            val ids = records.map { it.id }.filter { it.isNotEmpty() }
            if (ids.isEmpty()) {
                emptyList()
            } else {
                runCatching { query.rows(type, "id", ids) }.getOrDefault(emptyList())
                    .map { BundleRecord(HomeBundleExporter.portable(type, it)) }
            }
        }
        val base = ledger.forHome(bundle.homeId).toImportBase()
        return ImportPreview(bundle, ImportPlanner.plan(bundle, local, base))
    }

    /**
     * Writes [preview]'s plan with the user's [choices]; [unpacked] is the folder [BundleReader.read] put the media in.
     * Entity types this app version does not have are skipped (a newer Field Log may send more), not refused.
     */
    suspend fun apply(
        preview: ImportPreview,
        unpacked: File,
        choices: Map<Pair<String, String>, ConflictChoice> = emptyMap(),
        knownTypes: Set<String> = TYPES,
    ): ImportOutcome {
        val writes = preview.plan.writes(choices).filter { it.type in knownTypes }
            .sortedBy { TYPES.indexOf(it.type).let { i -> if (i < 0) TYPES.size else i } }
        var copied = 0
        val rows = writes.map { step ->
            val fields = step.imported.fields.toMutableMap()
            val media = fields.remove(HomeBundleExporter.MEDIA_FIELD) as? JsonObject
            media?.forEach { (column, value) ->
                val name = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return@forEach
                if (!BundleReader.isSafeMediaName(name)) return@forEach
                val source = File(unpacked, name)
                val target = File(mediaDir, name)
                if (source.isFile) {
                    mediaDir.mkdirs()
                    source.copyTo(target, overwrite = true)
                    copied++
                }
                if (target.isFile) fields[column] = JsonPrimitive(target.absolutePath)
            }
            step.type to JsonObject(fields)
        }
        store.inTransaction {
            rows.forEach { (type, row) -> store.write(type, row) }
            ledger.setAll(preview.plan.ledgerRows(preview.bundle.homeId))
        }
        rows.forEach { (type, row) ->
            val id = (row["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (!id.isNullOrEmpty()) onWritten(type, id)
        }
        return ImportOutcome(preview.bundle.homeId, rows.size, copied)
    }

    companion object {
        /** The tables a bundle may write here, parent first: the ones [HomeBundleExporter] walks. */
        val TYPES: Set<String> = linkedSetOf(
            "home", "floor", "room", "wall", "wall_photo", "opening", "stair", "overlay", "item", "warranty",
            "consumable", "service_record", "panel", "breaker", "breaker_link", "document", "photo", "contact",
            "maintenance_task", "change_order",
        )
    }
}
