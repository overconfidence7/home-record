package com.kurtz.homerecord.core.sync

import android.content.ContentValues
import androidx.room.RoomDatabase
import androidx.room.withTransaction
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

    /** Runs [block] as one transaction, so a pulled row and a local edit never interleave. */
    suspend fun <T> inTransaction(block: suspend () -> T): T = block()
}

/**
 * [RowStore] over the Room database's tables; [entity] is the table name and is checked against the schema.
 * [write] keeps only the table's columns, so a row from a newer app version with extra fields still lands, and fills a
 * missing NOT NULL column with its empty value ("" or 0), so a pulled row without this phone's local columns
 * ([LocalColumns], e.g. a photo's `path`) can be stored.
 */
class RoomRowStore(private val db: RoomDatabase) : RowStore {
    private val tables: Set<String> by lazy {
        val names = mutableSetOf<String>()
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'table'").use {
            while (it.moveToNext()) names += it.getString(0)
        }
        names
    }

    private class Column(val notNull: Boolean, val type: String, val hasDefault: Boolean)

    private val columns = java.util.concurrent.ConcurrentHashMap<String, Map<String, Column>>()

    private fun columnsOf(entity: String): Map<String, Column> = columns.getOrPut(entity) {
        val out = mutableMapOf<String, Column>()
        db.openHelper.readableDatabase.query("PRAGMA table_info(`$entity`)").use {
            val name = it.getColumnIndexOrThrow("name")
            val type = it.getColumnIndexOrThrow("type")
            val notNull = it.getColumnIndexOrThrow("notnull")
            val default = it.getColumnIndexOrThrow("dflt_value")
            while (it.moveToNext()) {
                out[it.getString(name)] = Column(it.getInt(notNull) == 1, it.getString(type).uppercase(), !it.isNull(default))
            }
        }
        out
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

    override suspend fun <T> inTransaction(block: suspend () -> T): T = db.withTransaction(block)

    override suspend fun write(entity: String, row: JsonObject) {
        require(entity in tables) { "Unknown entity $entity" }
        val values = ContentValues()
        val known = columnsOf(entity)
        for ((column, value) in row) {
            if (column !in known) continue
            val p = value as? JsonPrimitive
            when {
                p == null || p is JsonNull -> values.putNull(column)
                p.isString -> values.put(column, p.content)
                p.booleanOrNull != null -> values.put(column, if (p.booleanOrNull == true) 1 else 0)
                p.longOrNull != null -> values.put(column, p.longOrNull)
                else -> values.put(column, p.doubleOrNull)
            }
        }
        for ((column, info) in known) {
            if (column in row || !info.notNull || info.hasDefault) continue
            when (info.type) {
                "TEXT" -> values.put(column, "")
                "REAL" -> values.put(column, 0.0)
                "BLOB" -> values.put(column, ByteArray(0))
                else -> values.put(column, 0L)
            }
        }
        db.openHelper.writableDatabase.insert(entity, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
        db.invalidationTracker.refreshVersionsAsync()
    }
}
