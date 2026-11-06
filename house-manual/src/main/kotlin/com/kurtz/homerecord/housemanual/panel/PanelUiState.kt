package com.kurtz.homerecord.housemanual.panel

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelGrid
import com.kurtz.homerecord.core.model.SlotPlacement
import com.kurtz.homerecord.housemodel.panel.BreakerDraft
import com.kurtz.homerecord.housemodel.panel.FeedOption
import com.kurtz.homerecord.housemodel.panel.PanelDraft
import com.kurtz.homerecord.housemodel.panel.PanelField
import com.kurtz.homerecord.core.media.CaptureTarget

/** HM-Panel (AC-C40): the panel facts, the slot grid and any breaker whose slots clash with another. */
data class PanelUiState(
    val loading: Boolean = true,
    /** The panel item's name ("Main panel"), the screen title. */
    val name: String = "",
    val panel: Panel? = null,
    val breakers: List<Breaker> = emptyList(),
    val grid: PanelGrid? = null,
    /** Set when the panel could not be opened (its item is gone). */
    val missing: Boolean = false,
    /** HM-Breaker, open over the grid; null when closed. */
    val editor: BreakerEditorState? = null,
    /** The panel facts form (slot count, layout, main amps, brand); null when closed. */
    val facts: PanelFactsState? = null,
    /** "Panel photo" sheet: take one, choose one, or remove it. */
    val photoSheet: Boolean = false,
    /** Set while the route should open the camera ([PanelPhotoLaunch.target]) or the photo picker (null target). */
    val photoLaunch: PanelPhotoLaunch? = null,
    /** The picked photo could not be read. */
    val photoFailed: Boolean = false,
    /** The slot an item's breaker chip opened the panel at (AC-H7): scrolled into view and marked for 2 s; null after. */
    val highlightSlot: Int? = null,
    /** Legend PDF ready for the share sheet (AC-C43); the route opens it, then sends LegendHandled. */
    val legendUri: String? = null,
    val legendFailed: Boolean = false,
    val legendBusy: Boolean = false,
) {
    fun breaker(id: String): Breaker? = breakers.firstOrNull { it.id == id }

    /** Breakers that do not fit where they say they are (two phones edited the panel apart), listed under the grid. */
    val unplaced: List<Breaker> get() = grid?.unplaced.orEmpty().mapNotNull(::breaker)
}

/**
 * HM-Breaker (AC-C40): the breaker being added or edited. [placement] is where the draft would sit among the other
 * breakers now (a two-pole that runs into a taken slot says so before Save); [rooms] and [items] are what it can feed.
 */
data class BreakerEditorState(
    val draft: BreakerDraft,
    val dirty: Boolean = false,
    val showErrors: Boolean = false,
    val placement: SlotPlacement? = null,
    val rooms: List<FeedOption> = emptyList(),
    val items: List<FeedOption> = emptyList(),
    val confirmDelete: Boolean = false,
) {
    val isNew: Boolean get() = draft.id == null
    val slotProblem: SlotPlacement? get() = placement?.takeIf { it !is SlotPlacement.Ok }
    val ampsError: Boolean get() = showErrors && draft.ampsError()
    val canSave: Boolean get() = slotProblem == null && (dirty || isNew)

    /** What the breaker feeds as picked so far, rooms then items, in the pickers' order (AC-C41). */
    val feeds: List<String> get() = rooms.filter { it.id in draft.roomIds }.map { it.name } + items.filter { it.id in draft.itemIds }.map { it.name }
}

/**
 * Panel facts as typed (AC-C40). [highestUsed] is the highest slot a breaker fills (the slot count cannot go below it);
 * [layoutLocked] when breakers are labeled, since switching between two-column and DIN would move every one of them.
 */
data class PanelFactsState(
    val draft: PanelDraft,
    val dirty: Boolean = false,
    val showErrors: Boolean = false,
    val highestUsed: Int = 0,
    val layoutLocked: Boolean = false,
) {
    val errors: Set<PanelField> get() = if (showErrors) draft.errors(highestUsed) else emptySet()
}

data class PanelPhotoLaunch(val target: CaptureTarget?)

sealed interface PanelEvent {
    data object Back : PanelEvent

    /** Tap an open slot: a new breaker there. */
    data class OpenSlot(val slot: Int) : PanelEvent

    /** Tap a breaker (or one half of a tandem): edit it. */
    data class OpenBreaker(val id: String) : PanelEvent

    /** Touch and hold a single-pole breaker: a second breaker in the same slot, as a tandem. */
    data class AddTandem(val slot: Int) : PanelEvent
    data class ChangeBreaker(val draft: BreakerDraft) : PanelEvent
    data object SaveBreaker : PanelEvent
    data object CloseEditor : PanelEvent
    data object AskDeleteBreaker : PanelEvent
    data object ConfirmDeleteBreaker : PanelEvent
    data object CancelDeleteBreaker : PanelEvent
    data object EditFacts : PanelEvent

    /** Top bar "Map breakers": opens HM-Mapping (AC-C42). */
    data object Map : PanelEvent

    /** "Share legend": one PDF page with a row per slot (AC-C43). */
    data object ShareLegend : PanelEvent
    data object LegendHandled : PanelEvent
    data class ChangeFacts(val draft: PanelDraft) : PanelEvent
    data object SaveFacts : PanelEvent
    data object CloseFacts : PanelEvent
    data object OpenPhotoSheet : PanelEvent
    data object ClosePhotoSheet : PanelEvent
    data object TakePhoto : PanelEvent
    data object ChoosePhoto : PanelEvent
    data object RemovePhoto : PanelEvent
    data object PhotoLaunchHandled : PanelEvent
    data class CameraResult(val ok: Boolean) : PanelEvent
    data class GalleryResult(val uri: String?) : PanelEvent
}
