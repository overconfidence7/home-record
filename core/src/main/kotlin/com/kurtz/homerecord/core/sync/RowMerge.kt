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
 * protects edits the device has not pushed yet: while the entity has pending outbox rows, a local scalar
 * stays if it was written after the server's time for that field, and `*Json` lists are unioned.
 */
object RowMerge {
    fun applyPulled(local: JsonObject?, hasPending: Boolean, server: JsonObject, times: Map<String, Long>): JsonObject {
        if (local == null || !hasPending) return server
        val localAt = local["updatedAt"]?.jsonPrimitive?.longOrNull ?: 0L
        val out = LinkedHashMap<String, JsonElement>(server)
        for ((field, localValue) in local) {
            val serverValue = server[field] ?: continue
            if (field == "id" || localValue == serverValue) continue
            val union = if (field.endsWith("Json")) unionLists(localValue, serverValue) else null
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
