package com.kurtz.homerecord.housemodel.search

import com.kurtz.homerecord.core.data.BreakerSearchRow
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
 * Breakers (AC-H3) are found by label, by "breaker 14" ([breakerName]) and by their panel's name; a hit opens the panel
 * item at the breaker's slot. A labeled breaker is titled by its label, an unlabeled one by its number.
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
        breakers: List<BreakerSearchRow> = emptyList(),
        breakerName: (Int) -> String = { "Breaker $it" },
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
        val breakerDocs = breakers.map { b ->
            val label = b.label.trim()
            val number = b.number.takeIf { it > 0 }?.let(breakerName)
            val amps = b.amps?.let { "$it A" }
            SearchDocument(
                SearchKind.BREAKER, b.breakerId, label.ifEmpty { number ?: b.panelName },
                subtitle = listOfNotNull(number.takeIf { label.isNotEmpty() }, b.panelName, amps).joinToString(" · "),
                fields = listOfNotNull(label.ifEmpty { null }, number, b.panelName),
                openId = b.panelItemId,
                slot = b.number.takeIf { it > 0 },
            )
        }
        return itemDocs + roomDocs + overlayDocs + breakerDocs + contactDocs + docDocs
    }
}
