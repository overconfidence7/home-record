package com.kurtz.homerecord.housemanual.panel

import com.kurtz.homerecord.core.logic.SlotCountRule
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelLayout

data class PanelUiState(
    val loaded: Boolean = false,
    val panel: Panel? = null,
    val breakers: List<Breaker> = emptyList(),
    val slotCountInput: String = SlotCountRule.DEFAULT.toString(),
    val layoutInput: PanelLayout = PanelLayout.NA_NUMBERED,
) {
    val canSave: Boolean get() = SlotCountRule.parse(slotCountInput) != null

    fun breakerAt(slot: Int): Breaker? = breakers.firstOrNull { slot in it.slots }
}

sealed interface PanelEvent {
    data class SlotCountChanged(val value: String) : PanelEvent
    data class LayoutChanged(val layout: PanelLayout) : PanelEvent
    data object SavePanel : PanelEvent
    data object TakePhoto : PanelEvent
    data class SlotTapped(val slot: Int) : PanelEvent
}
