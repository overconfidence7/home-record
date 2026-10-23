package com.kurtz.homerecord.core.model

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

class SearchEngineTest {
    private val docs = listOf(
        SearchDocument(SearchKind.ITEM, "i1", "Garage freezer", "Garage", listOf("Garage freezer", "Frigidaire", "FFFH17F2QW", "Chest freezer")),
        SearchDocument(SearchKind.BREAKER, "b1", "Freezer", "Panel, slot 14", listOf("Freezer")),
        SearchDocument(SearchKind.ITEM, "i2", "Furnace", "Basement", listOf("Furnace", "Carrier", "59SC5A", "16x25x1 MERV 8")),
        SearchDocument(SearchKind.ROOM, "r1", "Kitchen", null),
        SearchDocument(SearchKind.CONTACT, "c1", "Dana Ruiz", "Plumber", listOf("Dana Ruiz", "Ruiz Plumbing", "plumber")),
    )

    @Test
    @AcceptanceCriterion("AC-C38")
    fun freezerFindsTheItemAndTheBreaker() {
        val hits = SearchEngine.search(docs, "freezer").map { it.document.id }
        assertThat(hits).containsExactly("b1", "i1").inOrder() // exact title beats prefix-word match
    }

    @Test
    @AcceptanceCriterion("AC-C38")
    fun consumableSpecAndModelFindTheItem() {
        assertThat(SearchEngine.search(docs, "16x25").map { it.document.id }).containsExactly("i2")
        assertThat(SearchEngine.search(docs, "59sc").map { it.document.id }).containsExactly("i2")
    }

    @Test
    @AcceptanceCriterion("AC-C38")
    fun prefixOutranksSubstringAndAllTokensAreRequired() {
        assertThat(SearchEngine.search(docs, "kit").first().document.id).isEqualTo("r1")
        assertThat(SearchEngine.search(docs, "garage chest").map { it.document.id }).containsExactly("i1")
        assertThat(SearchEngine.search(docs, "garage furnace")).isEmpty()
    }

    @Test
    @AcceptanceCriterion("AC-C38")
    fun blankQueryAndPunctuationAreHandled() {
        assertThat(SearchEngine.search(docs, "  ")).isEmpty()
        assertThat(SearchEngine.search(docs, "ruiz, plumbing").map { it.document.id }).containsExactly("c1")
    }
}
