package com.kurtz.homerecord.housemodel.panel

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.BreakerEntity
import com.kurtz.homerecord.core.data.BreakerLinkEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.PanelEntity
import com.kurtz.homerecord.core.data.encodeSlots
import com.kurtz.homerecord.core.data.toBreaker
import com.kurtz.homerecord.core.data.toPanel
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.LinkConfidence
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelGrid
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.SlotOccupancy
import com.kurtz.homerecord.core.model.SlotPlacement
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Item subtypes that open the panel editor (AC-C40). */
val PANEL_SUBTYPES = setOf("breaker_panel", "sub_panel")

fun isPanelSubtype(subtype: String?): Boolean = subtype in PANEL_SUBTYPES

enum class PanelField { SLOT_COUNT, MAIN_AMPS }

/** Panel facts as typed in the editor (AC-C40). */
data class PanelDraft(
    val slotCount: String = SlotOccupancy.DEFAULT_SLOTS.toString(),
    val layout: PanelLayout = PanelLayout.NORTH_AMERICAN,
    val mainAmps: String = "",
    val brand: String = "",
) {
    /** [highestUsed] is the highest slot a breaker fills; the panel cannot shrink below it. */
    fun errors(highestUsed: Int = 0): Set<PanelField> = buildSet {
        val slots = slotCount.trim().toIntOrNull()
        if (slots == null || slots !in maxOf(1, highestUsed)..SlotOccupancy.MAX_SLOTS) add(PanelField.SLOT_COUNT)
        if (mainAmps.isNotBlank() && mainAmps.trim().toIntOrNull()?.takeIf { it in 1..1000 } == null) add(PanelField.MAIN_AMPS)
    }

    companion object {
        fun of(panel: Panel) = PanelDraft(panel.slotCount.toString(), panel.layout, panel.mainAmps?.toString().orEmpty(), panel.brand.orEmpty())
    }
}

enum class BreakerField { SLOT, AMPS }

/** A breaker as typed in the editor (AC-C40). [tandem] shares [startSlot] with one single-pole breaker there. */
data class BreakerDraft(
    val id: String? = null,
    val startSlot: Int = 1,
    val poles: Int = 1,
    val amps: String = "",
    val type: BreakerType = BreakerType.STANDARD,
    val label: String = "",
    val tandem: Boolean = false,
    val roomIds: Set<String> = emptySet(),
    val itemIds: Set<String> = emptySet(),
) {
    fun ampsError(): Boolean = amps.isNotBlank() && amps.trim().toIntOrNull()?.takeIf { it in 1..400 } == null
}

sealed interface BreakerSave {
    data class Saved(val id: String) : BreakerSave
    data class Invalid(val fields: Set<BreakerField>, val placement: SlotPlacement?) : BreakerSave
}

/** One panel with its breakers, the grid they make and what each breaker feeds. */
data class PanelState(
    val panel: Panel,
    val breakers: List<Breaker>,
    val grid: PanelGrid,
    val links: Map<String, List<BreakerLinkEntity>>,
) {
    fun breaker(id: String): Breaker? = breakers.firstOrNull { it.id == id }
}

/** A room or item the breaker editor offers as something the breaker feeds. */
data class FeedOption(val id: String, val name: String)

/** What a breaker can feed: the home's rooms and its items other than the panels themselves, by name (AC-C40). */
data class FeedChoices(val rooms: List<FeedOption> = emptyList(), val items: List<FeedOption> = emptyList())

/** A breaker that feeds an item or room, as the item and room pages show it: "Main panel · Breaker 14" with a confidence dot (AC-C41). */
data class FeedingBreaker(val breaker: Breaker, val panelItemId: String, val panelName: String, val confidence: LinkConfidence)

/** Panels, breakers and what they feed (AC-C40). [onChange] feeds the sync outbox after every write. */
class PanelRepository(
    private val db: HomeRecordDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    /** The panel of a Breaker panel or Sub-panel item, created with the defaults the first time it is opened. */
    suspend fun ensurePanel(itemId: String): String {
        db.panelDao().listFor(itemId).firstOrNull()?.let { return it.id }
        val at = now()
        val id = Ids.newId()
        db.panelDao().upsert(PanelEntity(id = id, itemId = itemId, createdAt = at, updatedAt = at))
        onChange("panel", id)
        return id
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observe(itemId: String): Flow<PanelState?> = db.panelDao().observeFor(itemId).flatMapLatest { panels ->
        val row = panels.firstOrNull() ?: return@flatMapLatest flowOf(null)
        val panel = row.toPanel()
        combine(db.breakerDao().observeFor(row.id), db.breakerLinkDao().observeForPanel(row.id)) { breakerRows, links ->
            val breakers = breakerRows.map { it.toBreaker() }.sortedWith(compareBy<Breaker> { it.number }.thenBy { it.createdAt })
            PanelState(panel, breakers, SlotOccupancy.grid(panel.layout, panel.slotCount, breakers), links.groupBy { it.breakerId })
        }
    }

    /** Rooms and items a breaker of this home can feed, by name; panels and sub-panels are not offered (AC-C40). */
    fun observeChoices(homeId: String): Flow<FeedChoices> =
        combine(db.roomDao().observeForHome(homeId), db.itemDao().observeFor(homeId)) { rooms, items ->
            val byName = compareBy<FeedOption, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id }
            FeedChoices(
                rooms = rooms.map { FeedOption(it.id, it.name) }.sortedWith(byName),
                items = items.filterNot { isPanelSubtype(it.subtype) }.map { FeedOption(it.id, it.name) }.sortedWith(byName),
            )
        }

    /** Breakers that feed [itemId], panel by panel, slot order (AC-C41). */
    fun observeFeedingItem(itemId: String): Flow<List<FeedingBreaker>> = db.breakerLinkDao().observeFeedingItem(itemId).map(::feeding)

    /** Breakers that feed [roomId], panel by panel, slot order (AC-C41). */
    fun observeFeedingRoom(roomId: String): Flow<List<FeedingBreaker>> = db.breakerLinkDao().observeFeedingRoom(roomId).map(::feeding)

    private fun feeding(rows: List<com.kurtz.homerecord.core.data.BreakerFeedRow>): List<FeedingBreaker> =
        rows.groupBy { it.breakerId }.values.map { links ->
            // One breaker linked twice (a second phone added the same link) shows once, at its best confidence.
            val best = links.minBy { LinkConfidence.of(it.linkConfidence).ordinal }
            FeedingBreaker(best.toBreaker(), best.panelItemId, best.panelName, LinkConfidence.of(best.linkConfidence))
        }.sortedWith(compareBy<FeedingBreaker> { it.panelName.lowercase() }.thenBy { it.panelItemId }.thenBy { it.breaker.number })

    /** Saves panel facts; false when the draft has errors (a slot count below the breakers in use is one). */
    suspend fun savePanel(panelId: String, draft: PanelDraft): Boolean {
        val row = db.panelDao().get(panelId) ?: return false
        val breakers = db.breakerDao().listFor(panelId).map { it.toBreaker() }
        if (draft.errors(SlotOccupancy.highestUsed(breakers)).isNotEmpty()) return false
        if (draft.layout != PanelLayout.of(row.layout) && breakers.isNotEmpty()) return false
        db.panelDao().upsert(
            row.copy(
                slotCount = draft.slotCount.trim().toInt(), layout = draft.layout.name,
                mainAmps = draft.mainAmps.trim().toIntOrNull(), brand = draft.brand.trim().ifEmpty { null }, updatedAt = now(),
            ),
        )
        onChange("panel", panelId)
        return true
    }

    suspend fun setPhoto(panelId: String, path: String?) {
        val row = db.panelDao().get(panelId) ?: return
        db.panelDao().upsert(row.copy(photoPath = path, updatedAt = now()))
        onChange("panel", panelId)
    }

    /** The editor's draft for a breaker already in the panel. */
    suspend fun draftFor(breakerId: String): BreakerDraft? {
        val b = db.breakerDao().get(breakerId)?.toBreaker() ?: return null
        val links = db.breakerLinkDao().listFor(breakerId)
        val sharing = db.breakerDao().listFor(b.panelId).count { it.id != b.id && b.number in it.toBreaker().slots }
        return BreakerDraft(
            id = b.id, startSlot = b.number, poles = b.poles, amps = b.amps?.toString().orEmpty(), type = b.type, label = b.label,
            tandem = sharing > 0, roomIds = links.mapNotNull { it.roomId }.toSet(), itemIds = links.mapNotNull { it.itemId }.toSet(),
        )
    }

    /** Saves the breaker and the rooms and items it feeds; a link the homeowner picks by hand is Likely (AC-C40, AC-C41). */
    suspend fun saveBreaker(panelId: String, draft: BreakerDraft): BreakerSave {
        val panel = db.panelDao().get(panelId)?.toPanel() ?: return BreakerSave.Invalid(setOf(BreakerField.SLOT), SlotPlacement.OffPanel)
        val others = db.breakerDao().listFor(panelId).filter { it.id != draft.id }.map { it.toBreaker() }
        val placement = SlotOccupancy.place(panel.layout, panel.slotCount, others, draft.startSlot, draft.poles, draft.tandem)
        val fields = buildSet {
            if (placement !is SlotPlacement.Ok) add(BreakerField.SLOT)
            if (draft.ampsError()) add(BreakerField.AMPS)
        }
        if (fields.isNotEmpty() || placement !is SlotPlacement.Ok) return BreakerSave.Invalid(fields, placement)
        val at = now()
        val existing = draft.id?.let { db.breakerDao().get(it) }
        val id = existing?.id ?: Ids.newId()
        db.breakerDao().upsert(
            BreakerEntity(
                id = id, panelId = panelId, slotsJson = encodeSlots(placement.slots), poles = draft.poles,
                amps = draft.amps.trim().toIntOrNull(), type = draft.type.name, label = draft.label.trim(),
                confidence = existing?.confidence ?: LinkConfidence.UNKNOWN.name,
                createdAt = existing?.createdAt ?: at, updatedAt = at,
            ),
        )
        onChange("breaker", id)
        syncLinks(id, draft.roomIds, draft.itemIds, at)
        return BreakerSave.Saved(id)
    }

    /** Removes the breaker and its links; the slots open up again. */
    suspend fun deleteBreaker(id: String) {
        val at = now()
        db.breakerLinkDao().listFor(id).forEach {
            db.breakerLinkDao().softDelete(it.id, at)
            onChange("breaker_link", it.id)
        }
        db.breakerDao().softDelete(id, at)
        onChange("breaker", id)
    }

    /**
     * Mapping mode found it (AC-C42): the breaker feeds [roomId] and/or [itemId], so those links become Confirmed (made
     * when missing) and the breaker itself is marked Confirmed. False when the breaker is gone or nothing was picked.
     */
    suspend fun confirmLink(breakerId: String, roomId: String?, itemId: String?, endpointLabel: String? = null): Boolean {
        if (roomId == null && itemId == null) return false
        val breaker = db.breakerDao().get(breakerId)?.takeIf { it.deletedAt == null } ?: return false
        val at = now()
        val current = db.breakerLinkDao().listFor(breakerId)
        val label = endpointLabel?.trim()?.ifBlank { null }
        fun confirm(existing: BreakerLinkEntity?, room: String?, item: String?) = existing?.copy(
            confidence = LinkConfidence.CONFIRMED.name, endpointLabel = label ?: existing.endpointLabel, updatedAt = at,
        ) ?: BreakerLinkEntity(
            id = Ids.newId(), breakerId = breakerId, roomId = room, itemId = item, endpointLabel = label,
            confidence = LinkConfidence.CONFIRMED.name, createdAt = at, updatedAt = at,
        )
        val links = listOfNotNull(
            roomId?.let { id -> confirm(current.firstOrNull { it.roomId == id }, id, null) },
            itemId?.let { id -> confirm(current.firstOrNull { it.itemId == id }, null, id) },
        )
        links.forEach {
            db.breakerLinkDao().upsert(it)
            onChange("breaker_link", it.id)
        }
        db.breakerDao().upsert(breaker.copy(confidence = LinkConfidence.CONFIRMED.name, updatedAt = at))
        onChange("breaker", breakerId)
        return true
    }

    private suspend fun syncLinks(breakerId: String, roomIds: Set<String>, itemIds: Set<String>, at: Long) {
        val current = db.breakerLinkDao().listFor(breakerId)
        current.filter { (it.roomId != null && it.roomId !in roomIds) || (it.itemId != null && it.itemId !in itemIds) }.forEach {
            db.breakerLinkDao().softDelete(it.id, at)
            onChange("breaker_link", it.id)
        }
        val haveRooms = current.mapNotNull { it.roomId }.toSet()
        val haveItems = current.mapNotNull { it.itemId }.toSet()
        val added = (roomIds - haveRooms).map { BreakerLinkEntity(id = Ids.newId(), breakerId = breakerId, roomId = it, confidence = LinkConfidence.LIKELY.name, createdAt = at, updatedAt = at) } +
            (itemIds - haveItems).map { BreakerLinkEntity(id = Ids.newId(), breakerId = breakerId, itemId = it, confidence = LinkConfidence.LIKELY.name, createdAt = at, updatedAt = at) }
        added.forEach {
            db.breakerLinkDao().upsert(it)
            onChange("breaker_link", it.id)
        }
    }
}
