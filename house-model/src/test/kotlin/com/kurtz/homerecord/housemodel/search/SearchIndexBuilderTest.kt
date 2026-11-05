package com.kurtz.homerecord.housemodel.search

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.BreakerSearchRow
import com.kurtz.homerecord.core.data.ConsumableEntity
import com.kurtz.homerecord.core.data.ContactEntity
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.OverlayEntity
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
    private val overlays = listOf(
        OverlayEntity("o1", "h", roomId = "r1", wallId = "w1", kind = "water_cold", label = "Sink cold supply", createdAt = 0, updatedAt = 0),
        OverlayEntity("o2", "h", roomId = "r1", wallId = "w1", kind = "outlet", label = "", createdAt = 0, updatedAt = 0),
        OverlayEntity("o3", "h", roomId = null, wallId = null, kind = "drain", label = "Laundry standpipe", createdAt = 0, updatedAt = 0),
    )
    private val kindNames = mapOf("water_cold" to "Cold water", "outlet" to "Outlet", "drain" to "Drain")
    private val index = SearchIndexBuilder.build(
        items, rooms, consumables, contacts, documents, mapOf("pseudo:exterior" to "Exterior"),
        overlays = overlays, overlayKindName = { kindNames.getValue(it.wire) },
    ) { if (it == "furnace") "Gas furnace" else null }

    private fun ids(q: String) = SearchEngine.search(index, q).map { it.document.kind to it.document.id }

    // cr:seed-strings: the type's name comes from the resolver (string resources in the app), not from :core
    @Test @AcceptanceCriterion("AC-H3")
    fun findsAnItemByItsTypeName() {
        assertThat(ids("gas")).containsExactly(SearchKind.ITEM to "i1", SearchKind.ITEM to "i2")
    }

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

    @Test @AcceptanceCriterion("AC-C28", "AC-C38")
    fun findsOverlaysByLabelAndByKindNameAndWords() {
        assertThat(ids("sink")).containsExactly(SearchKind.OVERLAY to "o1")
        assertThat(ids("cold water")).containsExactly(SearchKind.OVERLAY to "o1")
        // the kind's search words: "pipe" finds supply and drain lines, "receptacle" an outlet
        assertThat(ids("pipe")).containsExactly(SearchKind.OVERLAY to "o1", SearchKind.OVERLAY to "o3")
        assertThat(ids("receptacle")).containsExactly(SearchKind.OVERLAY to "o2")
    }

    @Test @AcceptanceCriterion("AC-C28")
    fun anUnlabeledOverlayIsTitledByItsKindAndCarriesItsKindForTheIcon() {
        val outlet = index.single { it.id == "o2" }
        assertThat(outlet.title).isEqualTo("Outlet")
        assertThat(outlet.subtitle).isEqualTo("Garage")
        assertThat(outlet.subkind).isEqualTo("outlet")
        val sink = index.single { it.id == "o1" }
        assertThat(sink.subtitle).isEqualTo("Cold water · Garage")
        assertThat(index.single { it.id == "o3" }.subtitle).isEqualTo("Drain")
    }

    @Test @AcceptanceCriterion("AC-C38")
    fun findsOverlaysByTheirRoom() {
        assertThat(ids("garage")).containsAtLeast(SearchKind.ROOM to "r1", SearchKind.OVERLAY to "o1", SearchKind.OVERLAY to "o2")
        assertThat(ids("garage")).doesNotContain(SearchKind.OVERLAY to "o3")
    }

    // AC-H3: a breaker is found by its label, by "breaker 14" and by its panel; the hit opens the panel item at its slot.
    @Test @AcceptanceCriterion("AC-H3")
    fun findsABreakerByLabelNumberAndPanelAndOpensItsPanelAtTheSlot() {
        val breakers = listOf(
            BreakerSearchRow("b14", "[14,16]", 20, "Kitchen counters", "panel-item", "Main panel"),
            BreakerSearchRow("b3", "[3]", null, "", "sub-item", "Garage sub-panel"),
        )
        val withBreakers = SearchIndexBuilder.build(
            items, rooms, consumables, contacts, documents, emptyMap(), breakers = breakers, breakerName = { "Breaker $it" },
        ) { null }
        fun hits(q: String) = SearchEngine.search(withBreakers, q).map { it.document }.filter { it.kind == SearchKind.BREAKER }

        val counters = hits("counters").single()
        assertThat(counters.title).isEqualTo("Kitchen counters")
        assertThat(counters.subtitle).isEqualTo("Breaker 14 · Main panel · 20 A")
        assertThat(counters.openId).isEqualTo("panel-item")
        assertThat(counters.slot).isEqualTo(14)
        assertThat(hits("breaker 14").map { it.id }).containsExactly("b14")
        // An unlabeled breaker is titled by its number and found by its panel's name.
        val unlabeled = hits("garage").single()
        assertThat(unlabeled.title).isEqualTo("Breaker 3")
        assertThat(unlabeled.subtitle).isEqualTo("Garage sub-panel")
        assertThat(unlabeled.slot).isEqualTo(3)
    }
}
