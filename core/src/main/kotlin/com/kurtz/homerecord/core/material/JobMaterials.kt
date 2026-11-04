package com.kurtz.homerecord.core.material

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.MaterialEntity
import com.kurtz.homerecord.core.data.Sources
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun MaterialEntity.toMaterial(): Material =
    Material(id, jobId, vendor?.takeIf { it.isNotBlank() }, amountCents, MaterialCategory.of(category), date, receiptPath, note?.takeIf { it.isNotBlank() })

/** A receipt as typed in the form; [save] checks it. */
data class MaterialDraft(
    val jobId: String?,
    val vendor: String,
    val amountCents: Long?,
    val category: MaterialCategory,
    val date: String,
    val note: String,
    val receiptPath: String? = null,
)

sealed interface MaterialSaveResult {
    data class Saved(val id: String) : MaterialSaveResult
    /** No job picked, or the job is gone. */
    data object NoJob : MaterialSaveResult
    /** No amount, or zero. */
    data object NoAmount : MaterialSaveResult
    /** The receipt being edited was deleted (on another phone). */
    data object Missing : MaterialSaveResult
}

/**
 * Materials and receipts (AC-F44): log one against a job, edit it, delete it. Every write is one outbox change through
 * [onChange], so it works offline and syncs to the office.
 */
class JobMaterials(
    private val db: HomeRecordDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    private val dao get() = db.materialDao()

    fun observeForCompany(companyId: String): Flow<List<Material>> = dao.observeForCompany(companyId).map { rows -> rows.map { it.toMaterial() } }

    suspend fun get(id: String): Material? = dao.get(id)?.toMaterial()

    /** Saves [draft] as a new receipt, or over [id]. Vendor and note are trimmed; blank is none. */
    suspend fun save(draft: MaterialDraft, id: String? = null): MaterialSaveResult {
        val jobId = draft.jobId?.takeIf { db.jobDao().get(it) != null } ?: return MaterialSaveResult.NoJob
        val cents = draft.amountCents?.takeIf { it > 0 } ?: return MaterialSaveResult.NoAmount
        val stamp = now()
        val existing = id?.let { dao.get(it) ?: return MaterialSaveResult.Missing }
        val row = MaterialEntity(
            id = existing?.id ?: Ids.newId(),
            jobId = jobId,
            vendor = draft.vendor.trim().ifEmpty { null },
            amountCents = cents,
            category = draft.category.name,
            date = draft.date,
            receiptPath = draft.receiptPath,
            note = draft.note.trim().ifEmpty { null },
            createdAt = existing?.createdAt ?: stamp,
            updatedAt = stamp,
            source = existing?.source ?: Sources.FIELD_LOG,
        )
        dao.upsert(row)
        onChange(ENTITY, row.id)
        return MaterialSaveResult.Saved(row.id)
    }

    /** Deletes a receipt. False when it was already gone. */
    suspend fun delete(id: String): Boolean {
        if (dao.get(id) == null) return false
        dao.softDelete(id, now())
        onChange(ENTITY, id)
        return true
    }

    companion object {
        const val ENTITY = "material"
    }
}
