package com.kurtz.homerecord.housemodel.items

import androidx.room.withTransaction
import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.ConsumableEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.MaintenanceTaskEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.data.WarrantyEntity
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** "Exterior" and "Whole house" stand in for a room (AC-C30); their ids are reserved and never collide with UUIDs. */
object PseudoRooms {
    const val EXTERIOR = "pseudo:exterior"
    const val WHOLE_HOUSE = "pseudo:whole_house"
    fun isPseudo(roomId: String?) = roomId == EXTERIOR || roomId == WHOLE_HOUSE
}

enum class PhotoKind(val tag: String) {
    DATA_PLATE("data-plate"), ITEM("item");

    companion object {
        fun fromTagsJson(json: String) = entries.firstOrNull { "\"${it.tag}\"" in json } ?: ITEM
    }
}

/** A photo on the form; [id] is null until it is saved. */
data class DraftPhoto(val id: String?, val path: String, val kind: PhotoKind)

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
    val photos: List<DraftPhoto> = emptyList(),
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

/** Everything the item page shows; [warranty] is null until one is looked up or entered (AC-H7). */
data class ItemDetail(
    val item: ItemEntity,
    val categoryLabel: String,
    val subtypeLabel: String,
    val roomName: String,
    val warranty: WarrantyEntity?,
    val status: WarrantyStatus,
    val photos: List<PhotoEntity>,
)

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

    /** Every live item of the home as list rows; warranty status is as of today. [pseudoNames] labels Exterior and Whole house. */
    fun observeRows(homeId: String, pseudoNames: Map<String, String>): Flow<List<ItemRow>> =
        combine(db.itemDao().observeFor(homeId), db.roomDao().observeForHome(homeId), db.warrantyDao().observeAll()) { items, rooms, warranties ->
            val roomNames = rooms.associate { it.id to it.name }
            val endsOn = warranties.associate { it.itemId to it.endsOn }
            val todayIso = today().toString()
            items.map { i ->
                val cat = runCatching { ItemCategory.valueOf(i.category) }.getOrNull()
                val label = cat?.label ?: i.category
                ItemRow(
                    id = i.id, name = i.name, make = i.make, model = i.model, categoryLabel = label,
                    categoryOrder = ItemListBuilder.categoryOrder(label), subtypeId = i.subtype, roomId = i.roomId,
                    roomName = i.roomId?.let { roomNames[it] ?: pseudoNames[it] }.orEmpty(),
                    warranty = WarrantyStatus.of(endsOn[i.id], todayIso), createdAt = i.createdAt,
                )
            }
        }

    /** HM-Home: attention rows, recent items and the shutoff count, as of today (AC-H2). */
    fun observeHomeSummary(homeId: String, pseudoNames: Map<String, String>): Flow<com.kurtz.homerecord.housemodel.home.HomeSummary> =
        combine(observeRows(homeId, pseudoNames), db.maintenanceTaskDao().observeFor(homeId)) { rows, tasks ->
            com.kurtz.homerecord.housemodel.home.HomeSummaryBuilder.build(rows, tasks, today())
        }

    /** The item page; emits null when the item is gone. */
    fun observeDetail(id: String, homeId: String, pseudoNames: Map<String, String>): Flow<ItemDetail?> =
        combine(
            db.itemDao().observeOne(id), db.roomDao().observeForHome(homeId),
            db.warrantyDao().observeFor(id), db.photoDao().observeForItem(id),
        ) { item, rooms, warranties, photos ->
            item ?: return@combine null
            val warranty = warranties.firstOrNull()
            val subtype = ItemCategory.subtype(item.subtype)
            val cat = runCatching { ItemCategory.valueOf(item.category) }.getOrNull()
            ItemDetail(
                item = item, categoryLabel = cat?.label ?: item.category, subtypeLabel = subtype?.label ?: item.subtype,
                roomName = item.roomId?.let { rid -> rooms.firstOrNull { it.id == rid }?.name ?: pseudoNames[rid] }.orEmpty(),
                warranty = warranty, status = WarrantyStatus.of(warranty?.endsOn, today().toString()), photos = photos,
            )
        }

    /** Copies the item's details under "[name] [copySuffix]"; photos stay with the original. Returns the new id. */
    suspend fun duplicate(id: String, copySuffix: String): String? {
        val item = get(id) ?: return null
        val draft = draftFor(id)?.copy(id = null, photos = emptyList()) ?: return null
        return save(item.homeId, draft.copy(name = "${draft.name} $copySuffix"))
    }

    /** The Registered toggle on the warranty section (AC-C33). No-op when the item has no warranty row. */
    suspend fun setRegistered(itemId: String, registered: Boolean) {
        val w = db.warrantyDao().listFor(itemId).firstOrNull() ?: return
        db.warrantyDao().upsert(w.copy(registered = registered, updatedAt = now()))
        onChange("warranty", w.id)
    }

    suspend fun draftFor(id: String): ItemDraft? = get(id)?.let {
        ItemDraft(
            id = it.id, subtypeId = it.subtype, roomId = it.roomId, name = it.name, make = it.make.orEmpty(),
            model = it.model.orEmpty(), serial = it.serial.orEmpty(), manufacturedOn = it.manufacturedOn.orEmpty(),
            purchasedOn = it.purchasedOn.orEmpty(), inServiceOn = it.inServiceOn.orEmpty(),
            price = it.priceCents?.let { c -> "${c / 100}.${(c % 100).toString().padStart(2, '0')}" }.orEmpty(),
            vendor = it.vendor.orEmpty(), manualUrl = it.manualUrl.orEmpty(), notes = it.notes.orEmpty(),
            photos = db.photoDao().listForItem(id).map { p -> DraftPhoto(p.id, p.path, PhotoKind.fromTagsJson(p.tagsJson)) },
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
            savePhotos(homeId, id, draft, at)
        }
        return id
    }

    private suspend fun savePhotos(homeId: String, itemId: String, draft: ItemDraft, at: Long) {
        val kept = draft.photos.mapNotNull { it.id }.toSet()
        db.photoDao().listForItem(itemId).filter { it.id !in kept }.forEach {
            db.photoDao().softDelete(it.id, at)
            onChange("photo", it.id)
        }
        draft.photos.filter { it.id == null }.forEach {
            val pid = Ids.newId()
            db.photoDao().upsert(
                PhotoEntity(
                    id = pid, homeId = homeId, roomId = draft.roomId?.takeUnless(PseudoRooms::isPseudo), itemId = itemId,
                    path = it.path, takenAt = at, tagsJson = "[\"${it.kind.tag}\"]", createdAt = at, updatedAt = at,
                ),
            )
            onChange("photo", pid)
        }
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
