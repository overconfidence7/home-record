package com.kurtz.homerecord.core.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * How a row pulled from the server lands on a device (AC-F53). The server row is already merged; this only
 * protects edits the device has not pushed yet: while the entity has pending outbox rows, a field this device
 * changed (one of [changedHere], the fields in those unsent payloads) stays if it was written after the
 * server's time for that field, and the append-only lists in [UNION_FIELDS] are
 * unioned. Every other field, `*Json` lists such as polygons and phases included, takes one side whole.
 * Local columns the server row does not carry (a newer app version's column) are kept.
 */
object RowMerge {
    /** Append-only lists merged as a union; must match `UNION_FIELDS` in backend/src/sync/merge.ts. */
    val UNION_FIELDS: Map<String, Set<String>> = mapOf("punch" to setOf("editsJson"))

    fun isUnionField(entity: String, field: String): Boolean = UNION_FIELDS[entity]?.contains(field) == true

    fun applyPulled(
        entity: String,
        local: JsonObject?,
        hasPending: Boolean,
        server: JsonObject,
        times: Map<String, Long>,
        changedHere: Set<String>? = null,
    ): JsonObject {
        if (local == null) return server
        val out = LinkedHashMap<String, JsonElement>(local)
        out.putAll(server)
        if (!hasPending) return JsonObject(out)
        val localAt = local["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
        for ((field, localValue) in local) {
            val serverValue = server[field] ?: continue
            if (field == "id" || localValue == serverValue) continue
            if (changedHere != null && field !in changedHere) continue
            val union = if (isUnionField(entity, field)) unionLists(localValue, serverValue) else null
            out[field] = when {
                union != null -> union
                localAt > (times[field] ?: 0L) -> localValue
                else -> serverValue
            }
        }
        return JsonObject(out)
    }

    /** Union by `id` (or by value), keeping existing order. Null when either side is not a JSON array. */
    fun unionLists(a: JsonElement, b: JsonElement): JsonElement? {
        val left = asArray(a) ?: return null
        val right = asArray(b) ?: return null
        val seen = left.map(::keyOf).toMutableSet()
        val merged = left + right.filter { seen.add(keyOf(it)) }
        val array = JsonArray(merged)
        return if (a is JsonPrimitive && a.isString) JsonPrimitive(array.toString()) else array
    }

    private fun asArray(e: JsonElement): JsonArray? = when {
        e is JsonArray -> e
        e is JsonPrimitive && e.isString && e.content.startsWith("[") ->
            runCatching { Json.parseToJsonElement(e.content) as? JsonArray }.getOrNull()
        else -> null
    }

    private fun keyOf(e: JsonElement): String = (e as? JsonObject)?.get("id")?.let { "id:$it" } ?: e.toString()
}
