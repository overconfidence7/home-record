package com.kurtz.homerecord.housemodel.documents

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.DocumentFiles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Where a document hangs: the whole home, one room or one item (AC-C37). */
sealed interface DocumentScope {
    data object Home : DocumentScope
    data class Room(val roomId: String) : DocumentScope
    data class Item(val itemId: String) : DocumentScope
}

/** A folder and its files, for the Documents screen. */
data class DocumentFolder(val name: String, val documents: List<DocumentEntity>)

fun isSupportedDocumentMime(mime: String): Boolean = mime == "application/pdf" || mime.startsWith("image/")

/** Documents attached to the home, a room or an item, filed in folders. [onChange] feeds the sync outbox. */
class DocumentRepository(
    private val db: HomeRecordDatabase,
    private val files: DocumentFiles,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    /** Folders sorted by name, unfiled files first under ""; files inside a folder newest first. */
    fun observeFolders(homeId: String, scope: DocumentScope? = null): Flow<List<DocumentFolder>> =
        db.documentDao().observeFor(homeId).map { rows ->
            rows.filter { scope == null || matches(it, scope) }
                .groupBy { it.folder }
                .map { (name, docs) -> DocumentFolder(name, docs.sortedByDescending { it.createdAt }) }
                .sortedBy { it.name.lowercase() }
        }

    /** Copies [uri] into storage and files it; null when it is not a readable PDF or image. */
    suspend fun add(homeId: String, scope: DocumentScope, uri: String, folder: String = "", name: String? = null): String? {
        val stored = files.import(uri) ?: return null
        if (!isSupportedDocumentMime(stored.mime)) {
            files.discard(stored.path)
            return null
        }
        val at = now()
        val id = Ids.newId()
        db.documentDao().upsert(
            DocumentEntity(
                id = id, homeId = homeId,
                roomId = (scope as? DocumentScope.Room)?.roomId,
                itemId = (scope as? DocumentScope.Item)?.itemId,
                folder = folder.trim(), name = name?.trim().takeUnless { it.isNullOrEmpty() } ?: stored.name,
                path = stored.path, mime = stored.mime, size = stored.size, createdAt = at, updatedAt = at,
            ),
        )
        onChange("document", id)
        return id
    }

    suspend fun rename(id: String, name: String): Boolean {
        val row = db.documentDao().get(id) ?: return false
        if (name.isBlank()) return false
        db.documentDao().upsert(row.copy(name = name.trim(), updatedAt = now()))
        onChange("document", id)
        return true
    }

    suspend fun moveToFolder(id: String, folder: String) {
        val row = db.documentDao().get(id) ?: return
        db.documentDao().upsert(row.copy(folder = folder.trim(), updatedAt = now()))
        onChange("document", id)
    }

    /** Re-attaches a document to another home, room or item. */
    suspend fun attach(id: String, scope: DocumentScope) {
        val row = db.documentDao().get(id) ?: return
        db.documentDao().upsert(
            row.copy(roomId = (scope as? DocumentScope.Room)?.roomId, itemId = (scope as? DocumentScope.Item)?.itemId, updatedAt = now()),
        )
        onChange("document", id)
    }

    suspend fun delete(id: String) {
        val row = db.documentDao().get(id) ?: return
        db.documentDao().softDelete(id, now())
        files.discard(row.path)
        onChange("document", id)
    }

    private fun matches(doc: DocumentEntity, scope: DocumentScope) = when (scope) {
        DocumentScope.Home -> doc.roomId == null && doc.itemId == null
        is DocumentScope.Room -> doc.roomId == scope.roomId
        is DocumentScope.Item -> doc.itemId == scope.itemId
    }
}
