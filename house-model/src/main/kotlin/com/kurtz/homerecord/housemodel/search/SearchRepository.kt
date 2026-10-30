package com.kurtz.homerecord.housemodel.search

import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.model.SearchDocument
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** [subtypeName] is [com.kurtz.homerecord.housemodel.items.ItemRepository.subtypeName], so "furnace" finds a furnace named "Basement unit". */
class SearchRepository(private val db: HomeRecordDatabase, private val subtypeName: (String) -> String?) {
    /** Everything searchable in the home, rebuilt when any of it changes. [pseudoNames] labels Exterior and Whole house. */
    fun observeIndex(homeId: String, pseudoNames: Map<String, String>): Flow<List<SearchDocument>> =
        combine(
            db.itemDao().observeFor(homeId),
            db.roomDao().observeForHome(homeId),
            db.consumableDao().observeForHome(homeId),
            db.contactDao().observeFor(homeId),
            db.documentDao().observeFor(homeId),
        ) { items, rooms, consumables, contacts, documents ->
            SearchIndexBuilder.build(items, rooms, consumables, contacts, documents, pseudoNames, subtypeName)
        }
}
