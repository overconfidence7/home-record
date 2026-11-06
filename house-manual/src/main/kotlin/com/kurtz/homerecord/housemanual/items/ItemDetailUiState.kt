package com.kurtz.homerecord.housemanual.items

import com.kurtz.homerecord.core.lookup.RecallInfo
import com.kurtz.homerecord.core.warranty.WarrantyTerms
import com.kurtz.homerecord.core.data.ConsumableEntity
import com.kurtz.homerecord.core.data.ContactEntity
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.core.data.ServiceRecordEntity
import com.kurtz.homerecord.housemodel.items.ItemDetail
import com.kurtz.homerecord.housemodel.overlays.OverlayLink
import com.kurtz.homerecord.housemodel.panel.FeedingBreaker
import com.kurtz.homerecord.housemodel.upkeep.ConsumableDraft
import com.kurtz.homerecord.housemodel.upkeep.ServiceDraft

data class ItemDetailUiState(
    val loading: Boolean = true,
    val detail: ItemDetail? = null,
    val confirmDelete: Boolean = false,
    val lookupBusy: Boolean = false,
    val lookupNote: LookupNote? = null,
    /** Terms waiting for the user to confirm, edit or discard (AC-C34). */
    val review: WarrantyTerms? = null,
    val consumables: List<ConsumableEntity> = emptyList(),
    /** Newest first (AC-C36). */
    val service: List<ServiceRecordEntity> = emptyList(),
    /** The home's contacts, for the service form picker (AC-C39). */
    val contacts: List<ContactEntity> = emptyList(),
    /** Documents attached to this item, newest first (AC-C37). */
    val documents: List<DocumentEntity> = emptyList(),
    /** The home's other documents that can be attached to this item (AC-C37). */
    val attachable: List<DocumentEntity> = emptyList(),
    val showAttach: Boolean = false,
    /** Set when a picked or scanned file was not a PDF or photo. */
    val documentNotice: Boolean = false,
    val recalls: RecallUi = RecallUi.Idle,
    val consumableForm: ConsumableDraft? = null,
    val serviceForm: ServiceDraft? = null,
    /** The open upkeep form differs from what it opened with: Save is enabled and Close asks before discarding. */
    val formDirty: Boolean = false,
    /** Save is on for a new record (a tap shows what is missing, rule 21) and for an edit once something changed. */
    val formCanSave: Boolean = false,
    /** Set once the user taps Save on a form with errors, so required fields show their messages. */
    val showFormErrors: Boolean = false,
    /** A consumable or service record waiting on "Delete …?" (rule 16); null when no confirm is showing. */
    val confirmUpkeepDelete: UpkeepDelete? = null,
    /** Pipes, wires and boxes drawn on the walls that serve this item (AC-C26), for the Links section. */
    val overlayLinks: List<OverlayLink> = emptyList(),
    /** The breakers that feed this item (AC-C41), panel by panel in slot order, for the Power chips. */
    val feeding: List<FeedingBreaker> = emptyList(),
    /** The shutoff that isolates this item (AC-C45): "Shutoff: Kitchen sink cold valve"; null while none is linked. */
    val shutoff: ShutoffChoice? = null,
    /** The home's shutoffs this item can link to (never itself), mains first, for the picker. */
    val shutoffChoices: List<ShutoffChoice> = emptyList(),
    /** The "Which shutoff turns this off?" sheet is open. */
    val showShutoffPicker: Boolean = false,
    /** The picked shutoff could not be linked (deleted on another phone meanwhile); the section says so (r13). */
    val shutoffRefused: Boolean = false,
    /** Overflow "Move to room": the sheet is open (SPEC HM-Item). */
    val showMove: Boolean = false,
    /** The rooms the item can move to, bottom floor first, then Exterior and Whole house. */
    val moveChoices: List<MoveChoice> = emptyList(),
    /** A one-shot navigation the route performs, then answers with [ItemDetailEvent.NavHandled]. */
    val nav: ItemDetailNav? = null,
)

/** A shutoff item as the item page's Shutoff link and picker show it (AC-C45). */
/** A room in the Move to room sheet; [floorName] is blank for Exterior and Whole house. */
data class MoveChoice(val id: String, val name: String, val floorName: String = "")

data class ShutoffChoice(val id: String, val name: String, val roomName: String = "")

/** The upkeep record a Delete confirm names: [label] is its size or part number, or what was done. */
sealed interface UpkeepDelete {
    val id: String
    val label: String
    data class Consumable(override val id: String, override val label: String) : UpkeepDelete
    data class Service(override val id: String, override val label: String) : UpkeepDelete
}

/** Navigation the ViewModel asks for after a delete or duplicate; the route owns the back stack. */
sealed interface ItemDetailNav {
    data object Closed : ItemDetailNav
    data class OpenCopy(val id: String) : ItemDetailNav
}

/** The "Check recalls" result (AC-B12). */
sealed interface RecallUi {
    data object Idle : RecallUi
    data object Busy : RecallUi
    data object NeedsMake : RecallUi
    data object Offline : RecallUi
    data object Failed : RecallUi
    data class Done(val recalls: List<RecallInfo>) : RecallUi
}

enum class LookupNote { NeedsMakeModel, Failed, Queued }

sealed interface ItemDetailEvent {
    data object Edit : ItemDetailEvent
    data object Duplicate : ItemDetailEvent
    data object AskDelete : ItemDetailEvent
    data object CancelDelete : ItemDetailEvent
    data object ConfirmDelete : ItemDetailEvent
    /** "Look up warranty" ([again] false) or "Look up again" ([again] true). */
    data class LookUp(val again: Boolean) : ItemDetailEvent
    data object OpenReview : ItemDetailEvent
    data object CheckRecalls : ItemDetailEvent
    data class ConfirmTerms(val partsMonths: Int?, val laborMonths: Int?) : ItemDetailEvent
    data object DiscardTerms : ItemDetailEvent
    data class SetRegistered(val registered: Boolean) : ItemDetailEvent
    data class OpenLink(val url: String) : ItemDetailEvent
    /** Opens the consumable form: blank, or [id]'s row when editing. */
    data class OpenConsumable(val id: String?) : ItemDetailEvent
    data class ChangeConsumable(val draft: ConsumableDraft) : ItemDetailEvent
    data object SaveConsumable : ItemDetailEvent
    /** Delete in the consumable form: asks first ([ItemDetailUiState.confirmUpkeepDelete]). */
    data class AskDeleteConsumable(val id: String) : ItemDetailEvent
    data class OpenService(val id: String?) : ItemDetailEvent
    data class ChangeService(val draft: ServiceDraft) : ItemDetailEvent
    data object SaveService : ItemDetailEvent
    /** Delete in the service form: asks first. */
    data class AskDeleteService(val id: String) : ItemDetailEvent
    data object CancelUpkeepDelete : ItemDetailEvent
    /** Deletes the record [ItemDetailUiState.confirmUpkeepDelete] names and closes its form. */
    data object ConfirmUpkeepDelete : ItemDetailEvent
    /** Closes whichever upkeep form is open without saving. */
    data object CloseForm : ItemDetailEvent
    data object UploadDocument : ItemDetailEvent
    data object ScanDocument : ItemDetailEvent
    data class DocumentPicked(val uri: String) : ItemDetailEvent
    data object DocumentScanFailed : ItemDetailEvent
    data object DismissDocumentNotice : ItemDetailEvent
    data class OpenDocument(val id: String) : ItemDetailEvent
    /** A Links row: open the wall of [link]'s overlay with it picked. */
    data class OpenOverlay(val link: OverlayLink) : ItemDetailEvent
    /** The Where row: open Place on plan (AC-C30 position). */
    data object Place : ItemDetailEvent
    /** The Panel row of a Breaker panel or Sub-panel item: open HM-Panel (AC-C40). */
    data object OpenPanel : ItemDetailEvent
    /** A Power chip: open [breaker]'s panel scrolled to its slot (AC-H7). */
    data class OpenBreaker(val breaker: FeedingBreaker) : ItemDetailEvent
    /** The Shutoff chip: open the shutoff item's page (AC-H7). */
    data class OpenShutoff(val id: String) : ItemDetailEvent
    data object ShowShutoffPicker : ItemDetailEvent
    data object ShowMove : ItemDetailEvent
    data object HideMove : ItemDetailEvent
    data class MoveTo(val roomId: String) : ItemDetailEvent
    /** The room chip under the hero: opens the item's room (AC-H7); routed, never reaches the ViewModel. */
    data class OpenRoom(val roomId: String) : ItemDetailEvent
    data object DismissShutoffNotice : ItemDetailEvent
    data object HideShutoffPicker : ItemDetailEvent
    /** Links [id] as the shutoff that isolates this item, or clears the link when null (AC-C45). */
    data class PickShutoff(val id: String?) : ItemDetailEvent
    data object ShowAttach : ItemDetailEvent
    data object HideAttach : ItemDetailEvent
    data class AttachDocument(val id: String) : ItemDetailEvent
    data object Back : ItemDetailEvent
    /** The route has acted on [ItemDetailUiState.nav]. */
    data object NavHandled : ItemDetailEvent
}
