package com.kurtz.homerecord.housemodel.items

import androidx.room.withTransaction
import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.ConsumableEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.MaintenanceTaskEntity
import com.kurtz.homerecord.core.model.ItemCategory
import java.time.LocalDate

/** "Exterior" and "Whole house" stand in for a room (AC-C30); their ids are reserved and never collide with UUIDs. */
object PseudoRooms {
    const val EXTERIOR = "pseudo:exterior"
    const val WHOLE_HOUSE = "pseudo:whole_house"
    fun isPseudo(roomId: String?) = roomId == EXTERIOR || roomId == WHOLE_HOUSE
}

/** What the ItemEdit form holds. [id] is null for a new item. */
data class ItemDraft(
    val id: String? = null,
    val subtypeId: String? = null,
    val roomId: String? = null,
    val name: String = "",
    val make: String = "",
    val model: String = "",
    val serial: String = "",
    val manufacturedOn: String = "",
    val purchasedOn: String = "",
    val inServiceOn: String = "",
    val price: String = "",
    val vendor: String = "",
    val manualUrl: String = "",
    val notes: String = "",
) {
    /** Price in cents, or null when blank or not a number. */
    val priceCents: Long? get() = parsePriceCents(price)

    fun errors(): Set<ItemField> = buildSet {
        if (subtypeId == null || ItemCategory.subtype(subtypeId) == null) add(ItemField.SUBTYPE)
        if (roomId.isNullOrBlank()) add(ItemField.ROOM)
        if (name.isBlank()) add(ItemField.NAME)
        if (price.isNotBlank() && priceCents == null) add(ItemField.PRICE)
        listOf(ItemField.MANUFACTURED to manufacturedOn, ItemField.PURCHASED to purchasedOn, ItemField.IN_SERVICE to inServiceOn)
            .forEach { (f, v) -> if (v.isNotBlank() && !isIsoDate(v)) add(f) }
    }
}

enum class ItemField { SUBTYPE, ROOM, NAME, PRICE, MANUFACTURED, PURCHASED, IN_SERVICE }

internal fun isIsoDate(s: String) = runCatching { LocalDate.parse(s) }.isSuccess

/** "1,299.50" or "$1299" to cents; null when it isn't a non-negative amount. */
internal fun parsePriceCents(raw: String): Long? {
    val t = raw.trim().removePrefix("$").replace(",", "")
    if (!Regex("""\d+(\.\d{1,2})?""").matches(t)) return null
    val (whole, frac) = t.split('.').let { it[0] to (it.getOrNull(1) ?: "") }
    return whole.toLong() * 100 + frac.padEnd(2, '0').toLong()
}

/**
 * Items for a home. Creating one seeds the consumables and reminders its subtype calls for (AC-C30, AC-C35).
 * [onChange] is called with the entity name and id after every write so the sync outbox can pick it up.
 */
class ItemRepository(
    private val db: HomeRecordDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    suspend fun get(id: String): ItemEntity? = db.itemDao().get(id)

    suspend fun draftFor(id: String): ItemDraft? = get(id)?.let {
        ItemDraft(
            id = it.id, subtypeId = it.subtype, roomId = it.roomId, name = it.name, make = it.make.orEmpty(),
            model = it.model.orEmpty(), serial = it.serial.orEmpty(), manufacturedOn = it.manufacturedOn.orEmpty(),
            purchasedOn = it.purchasedOn.orEmpty(), inServiceOn = it.inServiceOn.orEmpty(),
            price = it.priceCents?.let { c -> "${c / 100}.${(c % 100).toString().padStart(2, '0')}" }.orEmpty(),
            vendor = it.vendor.orEmpty(), manualUrl = it.manualUrl.orEmpty(), notes = it.notes.orEmpty(),
        )
    }

    /** Saves a valid draft; returns the item id, or null when [ItemDraft.errors] is not empty. */
    suspend fun save(homeId: String, draft: ItemDraft): String? {
        if (draft.errors().isNotEmpty()) return null
        val subtype = ItemCategory.subtype(draft.subtypeId ?: return null) ?: return null
        val at = now()
        val existing = draft.id?.let { db.itemDao().get(it) }
        val id = existing?.id ?: Ids.newId()
        db.withTransaction {
            db.itemDao().upsert(
                ItemEntity(
                    id = id, homeId = homeId, roomId = draft.roomId, category = subtype.category.name, subtype = subtype.id,
                    name = draft.name.trim(), make = draft.make.clean(), model = draft.model.clean(), serial = draft.serial.clean(),
                    manufacturedOn = draft.manufacturedOn.clean(), purchasedOn = draft.purchasedOn.clean(),
                    inServiceOn = draft.inServiceOn.clean(), priceCents = draft.priceCents, vendor = draft.vendor.clean(),
                    manualUrl = draft.manualUrl.clean(), notes = draft.notes.clean(),
                    planX = existing?.planX, planY = existing?.planY, wallId = existing?.wallId,
                    poweredByBreakerId = existing?.poweredByBreakerId, shutoffItemId = existing?.shutoffItemId,
                    createdAt = existing?.createdAt ?: at, updatedAt = at, source = existing?.source ?: com.kurtz.homerecord.core.data.Sources.HOUSE_MANUAL,
                ),
            )
            onChange("item", id)
            if (existing == null) seedDefaults(homeId, id, subtype, at)
        }
        return id
    }

    private suspend fun seedDefaults(homeId: String, itemId: String, subtype: com.kurtz.homerecord.core.model.ItemSubtype, at: Long) {
        subtype.consumables.forEach {
            val cid = Ids.newId()
            db.consumableDao().upsert(ConsumableEntity(id = cid, itemId = itemId, kind = it.kind.name, spec = it.spec, createdAt = at, updatedAt = at))
            onChange("consumable", cid)
        }
        subtype.reminders.forEach {
            val tid = Ids.newId()
            db.maintenanceTaskDao().upsert(
                MaintenanceTaskEntity(
                    id = tid, homeId = homeId, itemId = itemId, title = it.title, intervalMonths = it.intervalMonths,
                    dueOn = today().plusMonths(it.intervalMonths.toLong()).toString(), createdAt = at, updatedAt = at,
                ),
            )
            onChange("maintenance_task", tid)
        }
    }

    suspend fun delete(id: String) {
        db.itemDao().softDelete(id, now())
        onChange("item", id)
    }

    private fun String.clean() = trim().takeIf { it.isNotEmpty() }
}
