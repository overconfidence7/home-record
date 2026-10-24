package com.kurtz.homerecord.housemodel.search

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ConsumableEntity
import com.kurtz.homerecord.core.data.ContactEntity
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.model.SearchEngine
import com.kurtz.homerecord.core.model.SearchKind
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

class SearchIndexBuilderTest {
    private val rooms = listOf(RoomEntity("r1", "f1", "Garage", createdAt = 0, updatedAt = 0))
    private val items = listOf(
        ItemEntity(id = "i1", homeId = "h", roomId = "r1", category = "HVAC", subtype = "furnace", name = "Furnace", make = "Carrier", model = "59TP6A", serial = "1234A56789", createdAt = 0, updatedAt = 0),
        ItemEntity(id = "i2", homeId = "h", roomId = "pseudo:exterior", category = "HVAC", subtype = "furnace", name = "Condenser", createdAt = 0, updatedAt = 0),
    )
    private val consumables = listOf(ConsumableEntity("c1", "i1", "Filter", "16x25x1 MERV 11", createdAt = 0, updatedAt = 0))
    private val contacts = listOf(ContactEntity("k1", "h", "Maria Soto", trade = "Plumber", createdAt = 0, updatedAt = 0))
    private val documents = listOf(DocumentEntity("d1", "h", name = "Furnace manual", path = "p", mime = "application/pdf", createdAt = 0, updatedAt = 0))
    private val index = SearchIndexBuilder.build(items, rooms, consumables, contacts, documents, mapOf("pseudo:exterior" to "Exterior"))

    private fun ids(q: String) = SearchEngine.search(index, q).map { it.document.kind to it.document.id }

    @Test @AcceptanceCriterion("AC-H3")
    fun findsAnItemByModelNumberAndByConsumableSpec() {
        assertThat(ids("59TP6A")).containsExactly(SearchKind.ITEM to "i1")
        assertThat(ids("16x25x1")).containsExactly(SearchKind.ITEM to "i1")
    }

    @Test @AcceptanceCriterion("AC-H3")
    fun findsARoomAndItsItems() {
        assertThat(ids("garage")).contains(SearchKind.ROOM to "r1")
        assertThat(ids("garage")).contains(SearchKind.ITEM to "i1")
        assertThat(ids("exterior")).containsExactly(SearchKind.ITEM to "i2")
    }

    @Test @AcceptanceCriterion("AC-H3")
    fun findsContactsAndDocuments() {
        assertThat(ids("plumber")).containsExactly(SearchKind.CONTACT to "k1")
        assertThat(ids("manual")).containsExactly(SearchKind.DOCUMENT to "d1")
    }
}
