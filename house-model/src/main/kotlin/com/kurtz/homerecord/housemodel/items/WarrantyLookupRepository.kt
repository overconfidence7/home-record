package com.kurtz.homerecord.housemodel.items

import androidx.room.withTransaction
import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.WarrantyEntity
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.warranty.WarrantyLookupApi
import com.kurtz.homerecord.core.warranty.WarrantyTerms
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

sealed interface LookupOutcome {
    /** The item has no make or model, so there is nothing to look up. */
    data object NeedsMakeModel : LookupOutcome

    /** A confirmed warranty is already stored; only "Look up again" fetches twice (AC-C34). */
    data object AlreadyStored : LookupOutcome

    /** Terms came back; the user confirms, edits or discards them with [WarrantyLookupRepository.confirm] / [discard]. */
    data class Found(val terms: WarrantyTerms) : LookupOutcome

    /** Offline: queued and run by [WarrantyLookupRepository.runPending] when connectivity returns. */
    data object Queued : LookupOutcome

    data object Failed : LookupOutcome
}

/**
 * Warranty lookup for items (AC-C34). A Warranty row's `confidence` doubles as its lookup state:
 * [PENDING] (queued offline), `REVIEW_<level>` (fetched from the queue, waiting for the user's
 * confirmation: the item's badge), `HIGH`/`MEDIUM`/`LOW` (confirmed lookup) or `MANUAL`.
 */
class WarrantyLookupRepository(
    private val db: HomeRecordDatabase,
    private val api: WarrantyLookupApi,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    suspend fun lookup(itemId: String, force: Boolean = false): LookupOutcome {
        val item = db.itemDao().get(itemId) ?: return LookupOutcome.Failed
        val make = item.make?.takeIf { it.isNotBlank() }
        val model = item.model?.takeIf { it.isNotBlank() }
        if (make == null || model == null) return LookupOutcome.NeedsMakeModel
        val stored = current(itemId)
        if (!force && stored != null && stored.isConfirmed()) return LookupOutcome.AlreadyStored
        return when (val r = api.lookup(make, model, item.subtype, item.serial)) {
            is ApiResult.Ok -> LookupOutcome.Found(r.value)
            is ApiResult.Err -> if (r.failure == ApiFailure.Offline && (stored == null || stored.confidence == PENDING)) { queue(itemId, stored); LookupOutcome.Queued } else LookupOutcome.Failed
        }
    }

    /** Runs every queued lookup; results wait on the item as `REVIEW_*` rows. Returns the item ids that now have terms to review. */
    suspend fun runPending(): List<String> {
        val ready = mutableListOf<String>()
        for (row in db.warrantyDao().listPending()) {
            val item = db.itemDao().get(row.itemId)
            val make = item?.make; val model = item?.model
            if (item == null || make.isNullOrBlank() || model.isNullOrBlank()) { remove(row); continue }
            val r = api.lookup(make, model, item.subtype, item.serial)
            if (r is ApiResult.Ok) {
                write(row.copy(confidence = "REVIEW_${r.value.confidence}"), r.value, item.inServiceOn)
                ready += row.itemId
            } else if ((r as ApiResult.Err).failure == ApiFailure.Offline) break
        }
        return ready
    }

    /** Terms waiting for the user's confirmation (the badge), if any. */
    suspend fun reviewTerms(itemId: String): WarrantyTerms? =
        current(itemId)?.takeIf { it.confidence.startsWith(REVIEW) }?.let { termsOf(it) }

    suspend fun hasPending(itemId: String): Boolean = current(itemId)?.confidence == PENDING

    /** Stores [terms] as the item's warranty. [edited] marks hand-changed values as manual. */
    suspend fun confirm(itemId: String, terms: WarrantyTerms, edited: Boolean = false) {
        val item = db.itemDao().get(itemId) ?: return
        val base = current(itemId) ?: blank(itemId)
        val level = if (edited) "MANUAL" else terms.confidence
        write(base.copy(confidence = level, lookedUpAt = now()), terms, item.inServiceOn)
    }

    /** Throws the lookup result away; a previously confirmed warranty stays as it was. */
    suspend fun discard(itemId: String) {
        val row = current(itemId) ?: return
        if (!row.isConfirmed()) remove(row)
    }

    private suspend fun queue(itemId: String, stored: WarrantyEntity?) {
        if (stored != null) return
        val row = blank(itemId).copy(confidence = PENDING)
        db.warrantyDao().upsert(row)
        onChange("warranty", row.id)
    }

    private suspend fun write(row: WarrantyEntity, terms: WarrantyTerms, inServiceOn: String?) {
        val at = now()
        db.withTransaction {
            db.warrantyDao().upsert(
                row.copy(
                    partsMonths = terms.partsMonths, laborMonths = terms.laborMonths,
                    specialTermsJson = Json.encodeToString(ListSerializer(String.serializer()), terms.specialTerms),
                    endsOn = WarrantyStatus.endsOn(inServiceOn, terms.partsMonths, terms.laborMonths),
                    registrationRequired = terms.registrationRequired, registrationUrl = terms.registrationUrl,
                    sourceUrl = terms.sourceUrl, summary = terms.summary.ifBlank { null },
                    lookedUpAt = row.lookedUpAt ?: at, updatedAt = at,
                ),
            )
            onChange("warranty", row.id)
        }
    }

    private suspend fun remove(row: WarrantyEntity) {
        db.warrantyDao().softDelete(row.id, now())
        onChange("warranty", row.id)
    }

    private suspend fun current(itemId: String) = db.warrantyDao().listFor(itemId).firstOrNull()

    private fun blank(itemId: String) = now().let { WarrantyEntity(id = Ids.newId(), itemId = itemId, createdAt = it, updatedAt = it) }

    private fun WarrantyEntity.isConfirmed() = confidence != PENDING && !confidence.startsWith(REVIEW)

    private fun termsOf(r: WarrantyEntity) = WarrantyTerms(
        partsMonths = r.partsMonths, laborMonths = r.laborMonths,
        specialTerms = runCatching { Json.decodeFromString(ListSerializer(String.serializer()), r.specialTermsJson) }.getOrDefault(emptyList()),
        registrationRequired = r.registrationRequired, registrationUrl = r.registrationUrl, sourceUrl = r.sourceUrl,
        confidence = r.confidence.removePrefix(REVIEW), summary = r.summary.orEmpty(),
    )

    companion object {
        const val PENDING = "PENDING"
        const val REVIEW = "REVIEW_"
    }
}
