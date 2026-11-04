package com.kurtz.homerecord.fieldlog.changes

import androidx.compose.runtime.Immutable
import com.kurtz.homerecord.core.changeorder.ChangeOrderDraft
import com.kurtz.homerecord.core.model.ChangeOrderLineItem
import com.kurtz.homerecord.core.model.JobChangeOrder
import com.kurtz.homerecord.core.model.MoneyMath
import com.kurtz.homerecord.core.units.Money
import kotlinx.serialization.Serializable

/** One line item as typed: quantity and price stay text until saved, so "2." or "$85" can be on the way to a number. */
@Serializable
data class FormLine(val id: String, val description: String = "", val qty: String = "1", val price: String = "") {
    val qtyMilli: Long? get() = MoneyMath.parseQuantity(qty)
    val priceCents: Long? get() = if (price.isBlank()) 0L else Money.parseCents(price)
    val qtyError: Boolean get() = qtyMilli == null
    val priceError: Boolean get() = priceCents == null
    val ok: Boolean get() = !qtyError && !priceError
    /** Blank description and no price: left out on save. */
    val blank: Boolean get() = description.isBlank() && (priceCents ?: 1L) == 0L
    /** What the line comes to, or null while its quantity or price is not a number. */
    val amountCents: Long? get() = qtyMilli?.let { q -> priceCents?.let { MoneyMath.lineCents(q, it) } }

    fun toItem(): ChangeOrderLineItem? = qtyMilli?.let { q -> priceCents?.let { ChangeOrderLineItem(id, description.trim(), q, it) } }

    companion object {
        fun of(l: ChangeOrderLineItem) = FormLine(l.id, l.description, MoneyMath.formatQuantity(l.qtyMilli), if (l.unitCents == 0L) "" else Money.format(l.unitCents))
    }
}

/** The change order form as typed; kept in the saved state so rotation and process death keep it (rule 33). */
@Serializable
data class ChangeOrderFormDraft(
    val title: String = "",
    val clientName: String = "",
    val description: String = "",
    val lines: List<FormLine> = emptyList(),
    /** Job photos on the change order, in the order added (T056b). */
    val photoIds: List<String> = emptyList(),
) {
    val titleOk: Boolean get() = title.isNotBlank()
    val linesOk: Boolean get() = lines.all { it.blank || it.ok }
    /** The total of the lines that are numbers so far (AC-F37: MoneyMath, cents). */
    val totalCents: Long get() = MoneyMath.totalCents(lines.mapNotNull { it.toItem() })

    fun toDraft(): ChangeOrderDraft = ChangeOrderDraft(
        title = title.trim(), description = description, clientName = clientName,
        lines = lines.filterNot { it.blank }.mapNotNull { it.toItem() },
        photoIds = photoIds,
    )

    companion object {
        fun of(c: JobChangeOrder) = ChangeOrderFormDraft(c.title, c.clientName.orEmpty(), c.description.orEmpty(), c.lines.map(FormLine::of), c.photoIds)
    }
}

/** A job photo the change order can show; [path] is "" while the photo is not on this phone yet. */
data class CoPhoto(val id: String, val path: String, val createdAt: Long)

/** FL-ChangeOrder form: title, client, description (keyboard or voice), line items, total (AC-F37, rule 15). */
@Immutable
data class ChangeOrderFormUiState(
    val isNew: Boolean = true,
    val loading: Boolean = true,
    /** Gone, or this person may not write change orders on this job (AC-F3). */
    val failed: Boolean = false,
    /** Signed or Declined: shown, not edited (AC-F38). */
    val locked: Boolean = false,
    val draft: ChangeOrderFormDraft = ChangeOrderFormDraft(),
    val initial: ChangeOrderFormDraft = ChangeOrderFormDraft(),
    /** The phone has speech to text; hides Speak when not. */
    val canDictate: Boolean = false,
    val confirmDelete: Boolean = false,
    /** The job's photos, newest first, for the picker and the thumbnails. */
    val jobPhotos: List<CoPhoto> = emptyList(),
    /** The "Add job photos" sheet is open. */
    val pickingPhotos: Boolean = false,
    val working: Boolean = false,
    val done: Boolean = false,
) {
    val dirty: Boolean get() = draft != initial
    /** The title error shows once someone has typed and cleared it (rule 21). */
    val titleError: Boolean get() = !draft.titleOk && draft.title != initial.title
    /** The photos on the change order, in order; one no longer on the job drops out. */
    val photos: List<CoPhoto> get() = draft.photoIds.mapNotNull { id -> jobPhotos.firstOrNull { it.id == id } }
    val canSave: Boolean get() = !loading && !failed && !locked && !working && draft.titleOk && draft.linesOk
}

sealed interface ChangeOrderFormEvent {
    data class Title(val text: String) : ChangeOrderFormEvent
    data class Client(val text: String) : ChangeOrderFormEvent
    data class Description(val text: String) : ChangeOrderFormEvent
    /** Speak tapped; the route starts speech to text. */
    data object Speak : ChangeOrderFormEvent
    /** What speech to text heard; added to the end of the description. */
    data class Dictated(val text: String) : ChangeOrderFormEvent
    data object AddLine : ChangeOrderFormEvent
    data class LineDescription(val id: String, val text: String) : ChangeOrderFormEvent
    data class LineQty(val id: String, val text: String) : ChangeOrderFormEvent
    data class LinePrice(val id: String, val text: String) : ChangeOrderFormEvent
    data class RemoveLine(val id: String) : ChangeOrderFormEvent
    data object OpenPhotos : ChangeOrderFormEvent
    data object ClosePhotos : ChangeOrderFormEvent
    /** Put a job photo on the change order, or take it off. */
    data class TogglePhoto(val id: String) : ChangeOrderFormEvent
    data class RemovePhoto(val id: String) : ChangeOrderFormEvent
    /** Take photo: the route opens the job camera; photos taken from now on join the change order. */
    data object TakePhoto : ChangeOrderFormEvent
    data object Save : ChangeOrderFormEvent
    data object AskDelete : ChangeOrderFormEvent
    data object DismissDelete : ChangeOrderFormEvent
    data object Delete : ChangeOrderFormEvent
    data object Retry : ChangeOrderFormEvent
    /** Close (X); the route pops. */
    data object Close : ChangeOrderFormEvent
}
