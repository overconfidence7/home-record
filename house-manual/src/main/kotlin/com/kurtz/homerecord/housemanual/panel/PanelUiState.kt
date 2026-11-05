package com.kurtz.homerecord.housemanual.panel

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelGrid

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
) {
    fun breaker(id: String): Breaker? = breakers.firstOrNull { it.id == id }

    /** Breakers that do not fit where they say they are (two phones edited the panel apart), listed under the grid. */
    val unplaced: List<Breaker> get() = grid?.unplaced.orEmpty().mapNotNull(::breaker)
}

sealed interface PanelEvent {
    data object Back : PanelEvent

    /** Tap on a slot: edit [breakerId] there, or add a breaker at [slot] when it is open (AC-C40). */
    data class OpenSlot(val slot: Int, val breakerId: String?) : PanelEvent
}
