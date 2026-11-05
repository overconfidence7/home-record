package com.kurtz.homerecord.housemodel.search

import com.kurtz.homerecord.core.data.BreakerSearchRow
import com.kurtz.homerecord.core.data.ConsumableEntity
import com.kurtz.homerecord.core.data.ContactEntity
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.OverlayEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.model.SearchDocument
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * [overlayKindName] is the overlay kind's shown name (string resources in the app), so "cold water" finds a pipe.
 * [breakerName] is "Breaker 14" from string resources.
 * [subtypeName] is [com.kurtz.homerecord.housemodel.items.ItemRepository.subtypeName], so "furnace" finds a furnace named "Basement unit".
 */
class SearchRepository(
    private val db: HomeRecordDatabase,
    private val overlayKindName: (OverlayKind) -> String = { it.wire },
    private val subtypeName: (String) -> String?,
    private val breakerName: (Int) -> String = { "Breaker $it" },
) {
    /** Everything searchable in the home, rebuilt when any of it changes. [pseudoNames] labels Exterior and Whole house. */
    fun observeIndex(homeId: String, pseudoNames: Map<String, String>): Flow<List<SearchDocument>> =
        combine(
            db.itemDao().observeFor(homeId),
            db.roomDao().observeForHome(homeId),
            db.consumableDao().observeForHome(homeId),
            db.contactDao().observeFor(homeId),
            db.documentDao().observeFor(homeId),
            db.overlayDao().observeFor(homeId),
            db.breakerDao().observeForHome(homeId),
        ) { rows ->
            @Suppress("UNCHECKED_CAST")
            SearchIndexBuilder.build(
                items = rows[0] as List<ItemEntity>,
                rooms = rows[1] as List<RoomEntity>,
                consumables = rows[2] as List<ConsumableEntity>,
                contacts = rows[3] as List<ContactEntity>,
                documents = rows[4] as List<DocumentEntity>,
                pseudoNames = pseudoNames,
                overlays = rows[5] as List<OverlayEntity>,
                overlayKindName = overlayKindName,
                subtypeName = subtypeName,
                breakers = rows[6] as List<BreakerSearchRow>,
                breakerName = breakerName,
            )
        }
}
