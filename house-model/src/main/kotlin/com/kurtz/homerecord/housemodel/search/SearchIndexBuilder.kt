package com.kurtz.homerecord.housemodel.search

import com.kurtz.homerecord.core.data.ConsumableEntity
import com.kurtz.homerecord.core.data.ContactEntity
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.OverlayEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.model.SearchDocument
import com.kurtz.homerecord.core.model.SearchKind
import com.kurtz.homerecord.housemodel.overlays.OverlayKind

/**
 * Turns the home's rows into [SearchDocument]s for [com.kurtz.homerecord.core.model.SearchEngine] (AC-C38, AC-H3).
 * Overlays (AC-C28) are found by label, by their kind's shown name ([overlayKindName]) and by the kind's search words
 * ("pipe" finds supply, drain, vent and gas lines), then by their room; an unlabeled overlay is titled by its kind.
 */
object SearchIndexBuilder {
    fun build(
        items: List<ItemEntity>,
        rooms: List<RoomEntity>,
        consumables: List<ConsumableEntity>,
        contacts: List<ContactEntity>,
        documents: List<DocumentEntity>,
        pseudoNames: Map<String, String>,
        overlays: List<OverlayEntity> = emptyList(),
        overlayKindName: (OverlayKind) -> String = { it.wire },
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
        val overlayDocs = overlays.map { o ->
            val kind = OverlayKind.fromWire(o.kind)
            val kindName = overlayKindName(kind)
            val room = o.roomId?.let { roomNames[it] }
            val label = o.label.trim()
            SearchDocument(
                SearchKind.OVERLAY, o.id, label.ifEmpty { kindName },
                subtitle = listOfNotNull(kindName.takeIf { label.isNotEmpty() }, room).joinToString(" · ").ifEmpty { null },
                fields = listOfNotNull(label.ifEmpty { null }, kindName) + kind.terms + listOfNotNull(room),
                subkind = kind.wire,
            )
        }
        return itemDocs + roomDocs + overlayDocs + contactDocs + docDocs
    }
}
