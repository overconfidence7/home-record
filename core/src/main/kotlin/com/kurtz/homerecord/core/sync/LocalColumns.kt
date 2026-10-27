package com.kurtz.homerecord.core.sync

import kotlinx.serialization.json.JsonObject

/**
 * Columns that describe this phone, not the shared record (cr:sync-local-columns): where a file's bytes sit on this
 * phone (`path` and every `*Path` column) and whether a job file is kept Available offline (AC-F20). They are never
 * pushed, and a pulled row never overwrites them, so another phone's cache path or offline choice cannot land here.
 * The shared way to reach a file is its `r2Key`.
 */
object LocalColumns {
    private val EXTRA: Map<String, Set<String>> = mapOf("job_file" to setOf("offline"))

    fun isLocal(entity: String, column: String): Boolean =
        column == "path" || column.endsWith("Path") || EXTRA[entity]?.contains(column) == true

    /** [row] without its device-local columns. */
    fun strip(entity: String, row: JsonObject): JsonObject = JsonObject(row.filterKeys { !isLocal(entity, it) })
}
