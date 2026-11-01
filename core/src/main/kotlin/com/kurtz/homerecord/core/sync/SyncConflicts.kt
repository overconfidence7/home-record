package com.kurtz.homerecord.core.sync

import com.kurtz.homerecord.core.data.SyncConflictDao
import com.kurtz.homerecord.core.data.SyncConflictEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** What happened to a change the conflict log lists (AC-F53). */
enum class ConflictOutcome {
    /** Another phone changed the same field later; its value was kept and this phone's was dropped (`older`). */
    NewerKept,

    /** The Owner or Office edited this punch; their edit stands over the crew phone's (`authoritative`). */
    OfficeKept,

    /** The server turned the change down; it stays on this phone only (`refused:*`). */
    Refused,

    /** A change from another phone could not be saved here (`unstored:*`). */
    NotStored,
}

/**
 * One line of the conflict log: a record, what happened, and the fields involved in plain words (empty when the whole
 * change was refused or not stored). [label] is the record's name or title, null when it has none.
 */
data class ConflictEntry(
    val entity: String,
    val entityId: String,
    val label: String?,
    val outcome: ConflictOutcome,
    val fields: List<String>,
    val at: Long,
)

/** Pure rules of the conflict log (AC-F53): which notes are worth a line, how they read, and how they group. */
object SyncConflicts {
    /** Bookkeeping columns: the server reports them in a conflict, but nobody typed them. */
    private val BOOKKEEPING = setOf("id", "updatedAt", "createdAt")

    /** Fields tried in order for a record's name. */
    private val LABEL_FIELDS = listOf("name", "title", "filename", "caption", "day")

    fun outcomeOf(reason: String): ConflictOutcome = when {
        reason == "authoritative" -> ConflictOutcome.OfficeKept
        reason.startsWith("refused") -> ConflictOutcome.Refused
        reason.startsWith("unstored") -> ConflictOutcome.NotStored
        else -> ConflictOutcome.NewerKept
    }

    /** False for a field-level note about a bookkeeping column only; whole-change notes (field "") always count. */
    fun worthLogging(note: ConflictNote): Boolean = note.field !in BOOKKEEPING

    /** The record's name as stored on this phone, for the log line; null when it has none or is gone. */
    fun labelOf(row: JsonObject?): String? = LABEL_FIELDS.firstNotNullOfOrNull { field ->
        (row?.get(field) as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()?.takeIf(String::isNotEmpty)
    }

    /**
     * A column name in plain words: `dueDate` → "due date", `assigneeId` → "assignee", `editsJson` → "edits",
     * `deletedAt` → "deleted", a punch's `at` → "time". Empty for a whole-change note.
     */
    fun fieldWords(field: String): String {
        if (field.isEmpty()) return ""
        if (field == "at") return "time"
        var f = field
        for (suffix in listOf("Json", "Id", "At")) if (f.length > suffix.length && f.endsWith(suffix)) f = f.removeSuffix(suffix)
        return f.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replace('_', ' ').lowercase()
    }

    /**
     * Groups log rows into lines, newest first: rows of one record, outcome and time (one sync pass) become one line
     * listing each field once.
     */
    fun entries(rows: List<SyncConflictEntity>): List<ConflictEntry> =
        rows.sortedWith(compareByDescending<SyncConflictEntity> { it.at }.thenByDescending { it.id })
            .groupBy { listOf(it.entity, it.entityId, outcomeOf(it.reason).name, it.at.toString()) }
            .values.map { group ->
                val first = group.first()
                ConflictEntry(
                    entity = first.entity,
                    entityId = first.entityId,
                    label = group.firstNotNullOfOrNull { it.label },
                    outcome = outcomeOf(first.reason),
                    fields = group.map { fieldWords(it.field) }.filter(String::isNotEmpty).distinct().sorted(),
                    at = first.at,
                )
            }
}

/**
 * The device-local conflict log (AC-F53): [SyncEngine] writes each pass's [ConflictNote]s here with the record's name,
 * the newest [KEEP] rows are kept, and FL-Sync reads the [entries].
 */
class SyncConflictLog(private val dao: SyncConflictDao, private val store: RowStore) {
    val entries: Flow<List<ConflictEntry>> = dao.observeRecent(KEEP).map(SyncConflicts::entries)

    suspend fun record(tenant: TenantRef, notes: List<ConflictNote>, at: Long) {
        val worth = notes.filter(SyncConflicts::worthLogging)
        if (worth.isEmpty()) return
        val labels = worth.map { it.entity to it.entityId }.distinct().associateWith { (entity, id) ->
            runCatching { SyncConflicts.labelOf(store.read(entity, id)) }.getOrNull()
        }
        dao.add(
            worth.map {
                SyncConflictEntity(
                    tenantKey = tenant.key, entity = it.entity, entityId = it.entityId, field = it.field,
                    reason = it.reason, label = labels[it.entity to it.entityId], at = at,
                )
            },
        )
        dao.trim(KEEP)
    }

    suspend fun clear() = dao.clear()

    companion object {
        const val KEEP = 200
    }
}
