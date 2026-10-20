package com.kurtz.homerecord.core.sync

import android.content.ContentValues
import androidx.room.RoomDatabase
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Reads and writes whole entity rows as JSON, soft-deleted rows included, so sync is independent of each DAO. */
interface RowStore {
    suspend fun read(entity: String, id: String): JsonObject?
    suspend fun write(entity: String, row: JsonObject)
}

/** [RowStore] over the Room database's tables; [entity] is the table name and is checked against the schema. */
class RoomRowStore(private val db: RoomDatabase) : RowStore {
    private val tables: Set<String> by lazy {
        val names = mutableSetOf<String>()
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use {
            while (it.moveToNext()) names += it.getString(0)
        }
        names
    }

    override suspend fun read(entity: String, id: String): JsonObject? {
        require(entity in tables) { "Unknown entity $entity" }
        return db.openHelper.readableDatabase.query("SELECT * FROM $entity WHERE id = ?", arrayOf(id)).use { c ->
            if (!c.moveToFirst()) return@use null
            JsonObject(
                (0 until c.columnCount).associate { i ->
                    c.getColumnName(i) to when (c.getType(i)) {
                        android.database.Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(c.getLong(i))
                        android.database.Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(c.getDouble(i))
                        android.database.Cursor.FIELD_TYPE_STRING -> JsonPrimitive(c.getString(i))
                        else -> JsonNull
                    }
                },
            )
        }
    }

    override suspend fun write(entity: String, row: JsonObject) {
        require(entity in tables) { "Unknown entity $entity" }
        val values = ContentValues()
        for ((column, value) in row) {
            val p = value as? JsonPrimitive
            when {
                p == null || p is JsonNull -> values.putNull(column)
                p.isString -> values.put(column, p.content)
                p.booleanOrNull != null -> values.put(column, if (p.booleanOrNull == true) 1 else 0)
                p.longOrNull != null -> values.put(column, p.longOrNull)
                else -> values.put(column, p.doubleOrNull)
            }
        }
        db.openHelper.writableDatabase.insert(entity, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
        db.invalidationTracker.refreshVersionsAsync()
    }
}
