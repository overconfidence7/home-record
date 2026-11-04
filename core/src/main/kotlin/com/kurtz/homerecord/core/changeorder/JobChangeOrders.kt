package com.kurtz.homerecord.core.changeorder

import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.data.ChangeOrderEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.model.ChangeOrderLineItem
import com.kurtz.homerecord.core.model.ChangeOrderPhotos
import com.kurtz.homerecord.core.model.ChangeOrderStatus
import com.kurtz.homerecord.core.model.JobChangeOrder
import com.kurtz.homerecord.core.model.MoneyMath
import com.kurtz.homerecord.core.model.Signature
import com.kurtz.homerecord.core.model.SignatureStroke
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** What the change order form saves (AC-F37). */
data class ChangeOrderDraft(
    val title: String,
    val description: String? = null,
    val lines: List<ChangeOrderLineItem> = emptyList(),
    val clientName: String? = null,
    /** Job photos on the change order, in order (T056b). */
    val photoIds: List<String> = emptyList(),
)

fun ChangeOrderEntity.toModel(): JobChangeOrder {
    val lines = ChangeOrderLineItem.decode(linesJson)
    return JobChangeOrder(
        id = id, jobId = jobId, title = title, description = description, lines = lines,
        // The stored total is what was signed; a draft's total is always its lines'.
        totalCents = if (ChangeOrderStatus.of(status) == ChangeOrderStatus.DRAFT) MoneyMath.totalCents(lines) else totalCents,
        clientName = clientName, status = ChangeOrderStatus.of(status), signedAt = signedAt, signedBy = signedBy,
        createdAt = createdAt, updatedAt = updatedAt, photoIds = ChangeOrderPhotos.decode(photoIdsJson),
        signaturePath = signaturePath, signature = Signature.decode(signatureJson), signedOnWeb = signedVia == SIGNED_VIA_WEB,
    )
}

/** `change_order.signedVia` for a signature the client gave on the share page (AC-F40, backend AC-B36). */
const val SIGNED_VIA_WEB = "web"


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
                photoIdsJson = ChangeOrderPhotos.encode(jobPhotos(jobId, clean.photoIds)),
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
                totalCents = MoneyMath.totalCents(clean.lines), clientName = clean.clientName,
                photoIdsJson = ChangeOrderPhotos.encode(jobPhotos(row.jobId, clean.photoIds)), updatedAt = now(),
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

    /**
     * Signs a Draft on the phone (AC-F38): stores the signature image, the signer's name and the time, freezes the
     * total and makes it Signed, after which it is read only. False when gone, not a Draft, or the name is blank.
     */
    suspend fun sign(id: String, signerName: String, signaturePath: String, strokes: List<SignatureStroke> = emptyList()): Boolean {
        val name = signerName.trim()
        if (name.isEmpty() || signaturePath.isBlank()) return false
        val row = dao.get(id) ?: return false
        if (ChangeOrderStatus.of(row.status) != ChangeOrderStatus.DRAFT) return false
        val stamp = now()
        dao.upsert(
            row.copy(
                status = ChangeOrderStatus.SIGNED.name, signedBy = name, signedAt = stamp, signaturePath = signaturePath,
                signatureJson = strokes.takeIf { it.isNotEmpty() }?.let(Signature::encode),
                totalCents = MoneyMath.totalCents(ChangeOrderLineItem.decode(row.linesJson)), updatedAt = stamp,
            ),
        )
        onChange(ENTITY, id)
        return true
    }

    /**
     * The signature image of a Signed order on this phone. One signed on another phone or on the share page (T058b)
     * arrives as strokes only; [draw] makes the PNG once and its path is kept as this phone's local column (never
     * pushed, so no outbox change). Null when the order has no signature or the image could not be drawn.
     */
    suspend fun signatureImage(id: String, draw: suspend (List<SignatureStroke>) -> String?): String? {
        val row = dao.get(id) ?: return null
        row.signaturePath?.takeIf { java.io.File(it).exists() }?.let { return it }
        val strokes = Signature.decode(row.signatureJson).takeIf { it.isNotEmpty() } ?: return null
        val path = draw(strokes) ?: return null
        dao.get(id)?.let { dao.upsert(it.copy(signaturePath = path)) }
        return path
    }

    /**
     * "Duplicate to change" (AC-F38): a new Draft on the same job with the title, description, client, lines and
     * photos of [id], new line ids, no signature. Returns the new id, or null when [id] is gone.
     */
    suspend fun duplicate(id: String, newLineId: () -> String = Ids::newId): String? {
        val src = dao.get(id)?.toModel() ?: return null
        return add(
            src.jobId,
            ChangeOrderDraft(
                title = src.title, description = src.description, clientName = src.clientName,
                lines = src.lines.map { it.copy(id = newLineId()) }, photoIds = src.photoIds,
            ),
        )
    }

    /**
     * The ids that are photos of this job, once each, in the order given. A photo deleted since or from another job
     * is left off, so the PDF (T057) never asks for a picture that is not the job's.
     */
    private suspend fun jobPhotos(jobId: String, ids: List<String>): List<String> =
        ids.distinct().filter { id -> db.photoDao().get(id)?.jobId == jobId }

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
