package com.kurtz.homerecord.housemodel.upkeep

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.ConsumableEntity
import com.kurtz.homerecord.core.data.ContactEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ServiceRecordEntity
import com.kurtz.homerecord.core.model.ConsumableKind
import com.kurtz.homerecord.housemodel.items.isIsoDate
import com.kurtz.homerecord.housemodel.items.parsePriceCents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

enum class ConsumableField { SPEC, QUANTITY, URL }
enum class ServiceField { DATE, DESCRIPTION, COST }
enum class ContactField { NAME, EMAIL }

/** A consumable as typed in the editor (AC-C35). */
data class ConsumableDraft(
    val id: String? = null,
    val kind: ConsumableKind = ConsumableKind.FILTER,
    val spec: String = "",
    val quantity: String = "1",
    val reorderUrl: String = "",
) {
    fun errors(): Set<ConsumableField> = buildSet {
        if (spec.isBlank()) add(ConsumableField.SPEC)
        if ((quantity.trim().toIntOrNull() ?: 0) < 1) add(ConsumableField.QUANTITY)
        val url = reorderUrl.trim()
        if (url.isNotEmpty() && !Regex("""https?://\S+""", RegexOption.IGNORE_CASE).matches(url)) add(ConsumableField.URL)
    }
}

/** A service record as typed in the form (AC-C36). [date] is ISO yyyy-MM-dd. */
data class ServiceDraft(
    val id: String? = null,
    val date: String = "",
    val contactId: String? = null,
    val description: String = "",
    val cost: String = "",
) {
    fun errors(): Set<ServiceField> = buildSet {
        if (!isIsoDate(date)) add(ServiceField.DATE)
        if (description.isBlank()) add(ServiceField.DESCRIPTION)
        if (cost.isNotBlank() && parsePriceCents(cost) == null) add(ServiceField.COST)
    }
}

/** A contact as typed in the form (AC-C39). */
data class ContactDraft(
    val id: String? = null,
    val name: String = "",
    val company: String = "",
    val trade: String = "",
    val phone: String = "",
    val email: String = "",
    val notes: String = "",
) {
    fun errors(): Set<ContactField> = buildSet {
        if (name.isBlank()) add(ContactField.NAME)
        if (email.isNotBlank() && !Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""").matches(email.trim())) add(ContactField.EMAIL)
    }
}

/** A contact with the items it has serviced, for the Contacts screen (AC-C39). */
data class ContactRow(val contact: ContactEntity, val serviceCount: Int, val itemIds: List<String>)

/** Consumables, service history and contacts. [onChange] feeds the sync outbox after every write. */
class UpkeepRepository(
    private val db: HomeRecordDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    fun observeConsumables(itemId: String): Flow<List<ConsumableEntity>> = db.consumableDao().observeFor(itemId)

    /** Saves a consumable on the item; null when the draft has errors. */
    suspend fun saveConsumable(itemId: String, draft: ConsumableDraft): String? {
        if (draft.errors().isNotEmpty()) return null
        val at = now()
        val existing = draft.id?.let { db.consumableDao().get(it) }
        val id = existing?.id ?: Ids.newId()
        db.consumableDao().upsert(
            ConsumableEntity(
                id = id, itemId = itemId, kind = draft.kind.name, spec = draft.spec.trim(), quantity = draft.quantity.trim().toInt(),
                reorderUrl = draft.reorderUrl.trim().ifEmpty { null }, createdAt = existing?.createdAt ?: at, updatedAt = at,
            ),
        )
        onChange("consumable", id)
        return id
    }

    suspend fun deleteConsumable(id: String) {
        db.consumableDao().softDelete(id, now())
        onChange("consumable", id)
    }

    /** Service history, newest first (AC-C36). */
    fun observeService(itemId: String): Flow<List<ServiceRecordEntity>> =
        db.serviceRecordDao().observeFor(itemId).map { rows -> rows.sortedWith(compareByDescending<ServiceRecordEntity> { it.date }.thenByDescending { it.createdAt }) }

    suspend fun saveService(itemId: String, draft: ServiceDraft): String? {
        if (draft.errors().isNotEmpty()) return null
        val at = now()
        val existing = draft.id?.let { db.serviceRecordDao().get(it) }
        val id = existing?.id ?: Ids.newId()
        db.serviceRecordDao().upsert(
            ServiceRecordEntity(
                id = id, itemId = itemId, date = draft.date, contactId = draft.contactId, description = draft.description.trim(),
                costCents = parsePriceCents(draft.cost), createdAt = existing?.createdAt ?: at, updatedAt = at,
            ),
        )
        onChange("service_record", id)
        return id
    }

    suspend fun deleteService(id: String) {
        db.serviceRecordDao().softDelete(id, now())
        onChange("service_record", id)
    }

    /** Contacts of the home by name, each with the items they have serviced (AC-C39). */
    fun observeContacts(homeId: String): Flow<List<ContactRow>> =
        combine(db.contactDao().observeFor(homeId), db.itemDao().observeFor(homeId), db.serviceRecordDao().observeAll()) { contacts, items, records ->
            val live = items.map { it.id }.toSet()
            contacts.sortedBy { it.name.lowercase() }.map { c ->
                val mine = records.filter { it.contactId == c.id && it.itemId in live }
                ContactRow(c, mine.size, mine.map { it.itemId }.distinct())
            }
        }

    suspend fun saveContact(homeId: String, draft: ContactDraft): String? {
        if (draft.errors().isNotEmpty()) return null
        val at = now()
        val existing = draft.id?.let { db.contactDao().get(it) }
        val id = existing?.id ?: Ids.newId()
        db.contactDao().upsert(
            ContactEntity(
                id = id, homeId = homeId, name = draft.name.trim(), company = draft.company.clean(), trade = draft.trade.clean(),
                phone = draft.phone.clean(), email = draft.email.clean(), notes = draft.notes.clean(),
                createdAt = existing?.createdAt ?: at, updatedAt = at,
            ),
        )
        onChange("contact", id)
        return id
    }

    suspend fun draftFor(contactId: String): ContactDraft? = db.contactDao().get(contactId)?.let {
        ContactDraft(it.id, it.name, it.company.orEmpty(), it.trade.orEmpty(), it.phone.orEmpty(), it.email.orEmpty(), it.notes.orEmpty())
    }

    /** Removes the contact; service records keep their text but lose the link. */
    suspend fun deleteContact(id: String) {
        val at = now()
        db.serviceRecordDao().listForContact(id).forEach {
            db.serviceRecordDao().upsert(it.copy(contactId = null, updatedAt = at))
            onChange("service_record", it.id)
        }
        db.contactDao().softDelete(id, at)
        onChange("contact", id)
    }

    private fun String.clean() = trim().takeIf { it.isNotEmpty() }
}
