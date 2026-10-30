package com.kurtz.homerecord.housemodel.search

import com.kurtz.homerecord.core.data.ConsumableEntity
import com.kurtz.homerecord.core.data.ContactEntity
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.model.SearchDocument
import com.kurtz.homerecord.core.model.SearchKind

/** Turns the home's rows into [SearchDocument]s for [com.kurtz.homerecord.core.model.SearchEngine] (AC-C38, AC-H3). */
object SearchIndexBuilder {
    fun build(
        items: List<ItemEntity>,
        rooms: List<RoomEntity>,
        consumables: List<ConsumableEntity>,
        contacts: List<ContactEntity>,
        documents: List<DocumentEntity>,
        pseudoNames: Map<String, String>,
        subtypeName: (String) -> String?,
    ): List<SearchDocument> {
        val roomNames = rooms.associate { it.id to it.name }
        val specsByItem = consumables.groupBy { it.itemId }
        val itemDocs = items.map { i ->
            val room = i.roomId?.let { roomNames[it] ?: pseudoNames[it] }
            val subtype = subtypeName(i.subtype)
            val specs = specsByItem[i.id].orEmpty().flatMap { listOf(it.spec, it.kind) }
            SearchDocument(
                SearchKind.ITEM, i.id, i.name,
                subtitle = listOfNotNull(i.make, i.model).joinToString(" ").ifEmpty { room },
                fields = listOfNotNull(i.name, i.model, i.make, i.serial, subtype) + specs + listOfNotNull(room),
            )
        }
        val roomDocs = rooms.map { SearchDocument(SearchKind.ROOM, it.id, it.name, fields = listOfNotNull(it.name, it.notes)) }
        val contactDocs = contacts.map {
            SearchDocument(SearchKind.CONTACT, it.id, it.name, it.trade ?: it.company, listOfNotNull(it.name, it.company, it.trade, it.phone, it.email))
        }
        val docDocs = documents.map { SearchDocument(SearchKind.DOCUMENT, it.id, it.name, it.folder.ifEmpty { null }, listOfNotNull(it.name, it.folder.ifEmpty { null })) }
        return itemDocs + roomDocs + contactDocs + docDocs
    }
}
