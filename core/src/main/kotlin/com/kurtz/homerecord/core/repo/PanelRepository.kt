package com.kurtz.homerecord.core.repo

import androidx.room.withTransaction
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toDomain
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.logic.MainBreakerRule
import com.kurtz.homerecord.core.logic.PanelProfile
import com.kurtz.homerecord.core.logic.ShrinkCheck
import com.kurtz.homerecord.core.logic.SlotOccupancy
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerRoom
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.Confidence
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.EndpointKind
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.Source
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class PanelRepository(
    private val db: HomeRecordDatabase,
    private val media: MediaFileStore,
    private val source: Source,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    fun observePanels(homeId: String): Flow<List<Panel>> =
        db.panelDao().observeForHome(homeId).map { list -> list.map { it.toDomain() } }

    suspend fun getPanel(id: String): Panel? = db.panelDao().get(id)?.toDomain()

    suspend fun createPanel(
        homeId: String,
        name: String,
        slotCount: Int,
        layout: PanelLayout,
        locationRoomId: String? = null,
        profile: PanelProfile? = null,
    ): Panel {
        val now = clock.now()
        val base = Panel(ids.next(), homeId, name.trim(), locationRoomId, null, slotCount, layout, now, now, source)
        val panel = profile?.applyTo(base) ?: base
        db.panelDao().upsert(panel.toEntity())
        return panel
    }

    suspend fun updatePanel(panel: Panel) {
        db.panelDao().upsert(panel.copy(updatedAt = clock.now()).toEntity())
    }

    /** Replaces the panel photo and removes the previous file, if any. */
    suspend fun setPanelPhoto(panelId: String, relativePath: String?) {
        val panel = getPanel(panelId) ?: return
        if (panel.photoPath != relativePath) media.delete(panel.photoPath)
        updatePanel(panel.copy(photoPath = relativePath))
    }

    /** AC-101: saves the profile fields of an existing panel. */
    suspend fun updateProfile(panelId: String, profile: PanelProfile) {
        val panel = getPanel(panelId) ?: return
        updatePanel(profile.applyTo(panel))
    }

    suspend fun deletePanel(id: String): Unit = db.withTransaction {
        val panel = db.panelDao().get(id) ?: return@withTransaction
        val fedBy = db.breakerDao().getAll().filter { it.panelId == id }.map { it.id }.toSet()
        detachFeeds(fedBy)
        media.delete(panel.photoPath)
        db.panelDao().delete(panel)
    }

    /** Sub-panels fed by any of [breakerIds] lose their feed link (AC-102). */
    private suspend fun detachFeeds(breakerIds: Set<String>) {
        if (breakerIds.isEmpty()) return
        val now = clock.now()
        db.panelDao().getAll().filter { it.feedBreakerId in breakerIds }.forEach {
            db.panelDao().upsert(it.copy(feedBreakerId = null, updatedAt = now))
        }
    }

    fun observeBreakers(panelId: String): Flow<List<Breaker>> =
        db.breakerDao().observeForPanel(panelId).map { list -> list.map { it.toDomain() } }

    suspend fun getBreaker(id: String): Breaker? = db.breakerDao().get(id)?.toDomain()

    /**
     * Inserts (blank id) or updates a breaker; returns the stored copy. Saving a main breaker clears the
     * flag on the panel's previous main (AC-103).
     */
    suspend fun saveBreaker(breaker: Breaker): Breaker = db.withTransaction {
        val now = clock.now()
        val stored = if (breaker.id.isBlank()) {
            breaker.copy(id = ids.next(), createdAt = now, updatedAt = now, source = source)
        } else {
            breaker.copy(updatedAt = now)
        }
        if (stored.isMain) {
            val siblings = db.breakerDao().getAll().filter { it.panelId == stored.panelId }.map { it.toDomain() }
            MainBreakerRule.toClear(siblings, stored.id).forEach {
                db.breakerDao().upsert(it.copy(isMain = false, updatedAt = now).toEntity())
            }
        }
        db.breakerDao().upsert(stored.toEntity())
        stored
    }

    /** Deletes a breaker, its room links, and detaches (not deletes) its endpoints. */
    suspend fun deleteBreaker(id: String): Unit = db.withTransaction {
        val breaker = db.breakerDao().get(id) ?: return@withTransaction
        val now = clock.now()
        db.endpointDao().getAll().filter { it.breakerId == id }.forEach {
            db.endpointDao().upsert(it.copy(breakerId = null, updatedAt = now))
        }
        db.breakerRoomDao().deleteForBreaker(id)
        detachFeeds(setOf(id))
        db.breakerDao().delete(breaker)
    }

    /** AC-10: turns a one-pole breaker into tandem half 0 and adds an empty half 1 in the same slot. */
    suspend fun makeTandem(breakerId: String, emptyLabel: String = ""): Breaker? = db.withTransaction {
        val first = getBreaker(breakerId) ?: return@withTransaction null
        if (first.poles != 1 || first.tandemIndex != null) return@withTransaction null
        saveBreaker(first.copy(tandemIndex = 0))
        saveBreaker(first.copy(id = "", label = emptyLabel, amps = null, type = BreakerType.STANDARD,
            confidence = Confidence.UNKNOWN, tandemIndex = 1))
    }

    /** AC-11: updates the slot count; false (and no change) if occupied slots would be dropped. */
    suspend fun setSlotCount(panelId: String, slotCount: Int): ShrinkCheck = db.withTransaction {
        val panel = getPanel(panelId) ?: return@withTransaction ShrinkCheck.Ok
        val check = SlotOccupancy.checkShrink(slotCount, observeBreakers(panelId).first())
        if (check is ShrinkCheck.Ok) updatePanel(panel.copy(slotCount = slotCount))
        check
    }

    /** Breakers of every panel in the home. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeBreakersForHome(homeId: String): Flow<List<Breaker>> =
        db.panelDao().observeForHome(homeId).flatMapLatest { panels ->
            if (panels.isEmpty()) flowOf(emptyList())
            else combine(panels.map { observeBreakers(it.id) }) { lists -> lists.flatMap { it } }
        }

    fun observeBreakerRoomsForHome(homeId: String): Flow<List<BreakerRoom>> =
        db.breakerRoomDao().observeForHome(homeId).map { list -> list.map { it.toDomain() } }

    fun observeEndpointsForHome(homeId: String): Flow<List<Endpoint>> =
        db.endpointDao().observeForHome(homeId).map { list -> list.map { it.toDomain() } }

    fun observeBreakerRoomsForRoom(roomId: String): Flow<List<BreakerRoom>> =
        db.breakerRoomDao().observeForRoom(roomId).map { list -> list.map { it.toDomain() } }

    fun observeBreakerRooms(breakerId: String): Flow<List<BreakerRoom>> =
        db.breakerRoomDao().observeForBreaker(breakerId).map { list -> list.map { it.toDomain() } }

    suspend fun linkRoom(breakerId: String, roomId: String): BreakerRoom {
        val existing = db.breakerRoomDao().getAll().firstOrNull { it.breakerId == breakerId && it.roomId == roomId }
        if (existing != null) return existing.toDomain()
        val now = clock.now()
        val link = BreakerRoom(ids.next(), breakerId, roomId, now, now, source)
        db.breakerRoomDao().upsert(link.toEntity())
        return link
    }

    suspend fun unlinkRoom(breakerId: String, roomId: String) {
        db.breakerRoomDao().getAll()
            .filter { it.breakerId == breakerId && it.roomId == roomId }
            .forEach { db.breakerRoomDao().delete(it) }
    }

    fun observeEndpointsForBreaker(breakerId: String): Flow<List<Endpoint>> =
        db.endpointDao().observeForBreaker(breakerId).map { list -> list.map { it.toDomain() } }

    fun observeEndpointsForRoom(roomId: String): Flow<List<Endpoint>> =
        db.endpointDao().observeForRoom(roomId).map { list -> list.map { it.toDomain() } }

    suspend fun saveEndpoint(endpoint: Endpoint): Endpoint {
        val now = clock.now()
        val stored = if (endpoint.id.isBlank()) {
            endpoint.copy(id = ids.next(), createdAt = now, updatedAt = now, source = source)
        } else {
            endpoint.copy(updatedAt = now)
        }
        db.endpointDao().upsert(stored.toEntity())
        return stored
    }

    /**
     * AC-40: links an endpoint to [breakerId] and marks the breaker Confirmed. Uses the existing
     * [endpointId] when given, otherwise creates an outlet named [newLabel] in [roomId].
     */
    suspend fun confirmMapping(breakerId: String, roomId: String, endpointId: String?, newLabel: String?): Endpoint? =
        db.withTransaction {
            val breaker = getBreaker(breakerId) ?: return@withTransaction null
            val endpoint = if (endpointId != null) {
                db.endpointDao().get(endpointId)?.toDomain()?.copy(breakerId = breakerId)
            } else {
                val label = newLabel?.trim().orEmpty()
                if (label.isEmpty()) null
                else Endpoint(
                    id = "", breakerId = breakerId, roomId = roomId, kind = EndpointKind.OUTLET, label = label,
                    createdAt = 0, updatedAt = 0, source = source,
                )
            } ?: return@withTransaction null
            val saved = saveEndpoint(endpoint)
            saveBreaker(breaker.copy(confidence = Confidence.CONFIRMED))
            linkRoom(breakerId, saved.roomId)
            saved
        }

    suspend fun deleteEndpoint(id: String) {
        val endpoint = db.endpointDao().get(id) ?: return
        db.endpointDao().delete(endpoint)
    }
}
