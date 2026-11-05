package com.kurtz.homerecord.housemanual.panel

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelGrid
import com.kurtz.homerecord.core.model.SlotPlacement
import com.kurtz.homerecord.housemodel.panel.BreakerDraft
import com.kurtz.homerecord.housemodel.panel.FeedOption

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
}

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
}
