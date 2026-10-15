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
    /** Text in the slot-count editor on an existing panel; null until edited. */
    val slotEditInput: String? = null,
    /** Highest occupied slot that blocked the last shrink attempt (AC-11). */
    val shrinkBlockedAt: Int? = null,
) {
    val canApplySlotCount: Boolean get() = SlotCountRule.parse(slotEditInput ?: panel?.slotCount?.toString().orEmpty()) != null

    val canSave: Boolean get() = SlotCountRule.parse(slotCountInput) != null

    fun breakerAt(slot: Int): Breaker? = breakers.firstOrNull { slot in it.slots }

    /** The breakers sharing [slot] as a tandem pair, indexed 0 and 1 (either may be null), or null if not a tandem slot. */
    fun tandemAt(slot: Int): List<Breaker?>? {
        val here = breakers.filter { slot in it.slots }
        if (here.none { it.tandemIndex != null }) return null
        return listOf(here.firstOrNull { it.tandemIndex == 0 }, here.firstOrNull { it.tandemIndex == 1 })
    }
}

sealed interface PanelEvent {
    data class SlotCountChanged(val value: String) : PanelEvent
    data class LayoutChanged(val layout: PanelLayout) : PanelEvent
    data object SavePanel : PanelEvent
    data object TakePhoto : PanelEvent
    /** Open HM-Breaker for [slot]: an existing [breakerId], or an empty slot/tandem half. */
    data class OpenBreaker(val slot: Int, val breakerId: String?, val tandemIndex: Int? = null) : PanelEvent
    data class SlotLongPressed(val slot: Int) : PanelEvent
    data class SlotEditChanged(val value: String) : PanelEvent
    data object ApplySlotCount : PanelEvent
}
