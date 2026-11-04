package com.kurtz.homerecord.core.changeorder

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.ChangeOrderEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.model.ChangeOrderLineItem
import com.kurtz.homerecord.core.model.ChangeOrderStatus
import com.kurtz.homerecord.core.model.JobChangeOrder
import com.kurtz.homerecord.core.model.MoneyMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the change order form saves (AC-F37). */
data class ChangeOrderDraft(
    val title: String,
    val description: String? = null,
    val lines: List<ChangeOrderLineItem> = emptyList(),
    val clientName: String? = null,
)

fun ChangeOrderEntity.toModel(): JobChangeOrder {
    val lines = ChangeOrderLineItem.decode(linesJson)
    return JobChangeOrder(
        id = id, jobId = jobId, title = title, description = description, lines = lines,
        // The stored total is what was signed; a draft's total is always its lines'.
        totalCents = if (ChangeOrderStatus.of(status) == ChangeOrderStatus.DRAFT) MoneyMath.totalCents(lines) else totalCents,
        clientName = clientName, status = ChangeOrderStatus.of(status), signedAt = signedAt, signedBy = signedBy,
        createdAt = createdAt, updatedAt = updatedAt,
    )
}

/**
 * A job's change orders (AC-F37): add, edit while Draft, delete a Draft. Every write is one outbox change through
 * [onChange], so change orders work offline and sync (AC-F52). The total is computed by [MoneyMath], never typed.
 */
class JobChangeOrders(
    private val db: HomeRecordDatabase,
    private val now: () -> Long = System::currentTimeMillis,
    private val onChange: suspend (entity: String, id: String) -> Unit = { _, _ -> },
) {
    private val dao get() = db.changeOrderDao()

    /** Newest first. */
    fun observeForJob(jobId: String): Flow<List<JobChangeOrder>> =
        dao.observeFor(jobId).map { rows -> rows.map { it.toModel() }.sortedWith(compareByDescending<JobChangeOrder> { it.createdAt }.thenBy { it.id }) }

    suspend fun get(id: String): JobChangeOrder? = dao.get(id)?.toModel()

    /** Adds a Draft. Returns its id, or null when the title is blank or the job is gone. */
    suspend fun add(jobId: String, draft: ChangeOrderDraft): String? {
        val clean = draft.cleaned() ?: return null
        if (db.jobDao().get(jobId) == null) return null
        val id = Ids.newId()
        val stamp = now()
        dao.upsert(
            ChangeOrderEntity(
                id = id, jobId = jobId, title = clean.title, description = clean.description,
                linesJson = ChangeOrderLineItem.encode(clean.lines), totalCents = MoneyMath.totalCents(clean.lines),
                clientName = clean.clientName, status = ChangeOrderStatus.DRAFT.name, createdAt = stamp, updatedAt = stamp,
                source = Sources.FIELD_LOG,
            ),
        )
        onChange(ENTITY, id)
        return id
    }

    /** Saves the form over a Draft. False when blank, gone, or no longer a Draft (a Signed order is read only, AC-F38). */
    suspend fun update(id: String, draft: ChangeOrderDraft): Boolean {
        val clean = draft.cleaned() ?: return false
        val row = dao.get(id) ?: return false
        if (ChangeOrderStatus.of(row.status) != ChangeOrderStatus.DRAFT) return false
        dao.upsert(
            row.copy(
                title = clean.title, description = clean.description, linesJson = ChangeOrderLineItem.encode(clean.lines),
                totalCents = MoneyMath.totalCents(clean.lines), clientName = clean.clientName, updatedAt = now(),
            ),
        )
        onChange(ENTITY, id)
        return true
    }

    /** Deletes a Draft; a Signed or Declined order stays on record. Returns false when nothing was deleted. */
    suspend fun delete(id: String): Boolean {
        val row = dao.get(id) ?: return false
        if (ChangeOrderStatus.of(row.status) != ChangeOrderStatus.DRAFT) return false
        dao.softDelete(id, now())
        onChange(ENTITY, id)
        return true
    }

    private fun ChangeOrderDraft.cleaned(): ChangeOrderDraft? {
        val t = title.trim()
        if (t.isEmpty()) return null
        return copy(
            title = t,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            clientName = clientName?.trim()?.takeIf { it.isNotEmpty() },
            lines = lines.map { it.copy(description = it.description.trim()) }.filter { it.description.isNotEmpty() || it.unitCents > 0 },
        )
    }

    companion object {
        const val ENTITY = "change_order"
    }
}
