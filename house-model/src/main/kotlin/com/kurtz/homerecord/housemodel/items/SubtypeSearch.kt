package com.kurtz.homerecord.housemodel.items

import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.ItemSubtype

/**
 * Filters the item Type sheet. Every word of [query] must start a word of the subtype's label or its
 * category's label, ignoring case, so "furn" finds Furnace and "heating" lists every Heating and cooling type.
 * Categories with no match are left out; a blank query returns every category in order.
 */
object SubtypeSearch {
    fun matching(query: String): List<Pair<ItemCategory, List<ItemSubtype>>> {
        val terms = words(query)
        return ItemCategory.entries.mapNotNull { category ->
            val subtypes = category.subtypes.filter { terms.isEmpty() || matches(terms, words(it.label) + words(category.label)) }
            if (subtypes.isEmpty()) null else category to subtypes
        }
    }

    private fun matches(terms: List<String>, haystack: List<String>): Boolean =
        terms.all { term -> haystack.any { it.startsWith(term) } }

    private fun words(text: String): List<String> =
        text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
}
