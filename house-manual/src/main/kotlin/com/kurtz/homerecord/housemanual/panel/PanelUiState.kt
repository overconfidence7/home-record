package com.kurtz.homerecord.housemanual.panel

import com.kurtz.homerecord.core.logic.LoadForm
import com.kurtz.homerecord.core.logic.LoadResult
import com.kurtz.homerecord.core.logic.MainBreakerRule
import com.kurtz.homerecord.core.logic.PanelInsightsResult
import com.kurtz.homerecord.core.logic.PanelProfileError
import com.kurtz.homerecord.core.logic.PanelProfileInput
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
    /** Profile fields on the first-time setup form (AC-101). */
    val profileInput: PanelProfileInput = PanelProfileInput(),
    /** Profile editor on an existing panel; null while closed. */
    val profileEdit: PanelProfileInput? = null,
    val profileErrors: Set<PanelProfileError> = emptySet(),
    /** Breakers from other panels that can feed a sub-panel (AC-102). */
    val feedOptions: List<FeedOption> = emptyList(),
    /** Breaker id -> name of the sub-panel it feeds. */
    val feedNotes: Map<String, String> = emptyMap(),
    /** AC-106: insights computed when the sheet was opened; null while it is closed. */
    val insights: PanelInsightsResult? = null,
    /** AC-114: estimate form while the load sheet is open; null otherwise. */
    val loadForm: LoadForm? = null,
    /** AC-116: result card once Calculate succeeded. */
    val loadResult: LoadResult? = null,
) {
    val mainBreaker: Breaker? get() = MainBreakerRule.mainOf(breakers)

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
    /** Any change to the profile form (setup or editor). */
    data class ProfileChanged(val input: PanelProfileInput) : PanelEvent
    data object EditProfile : PanelEvent
    data object CancelProfile : PanelEvent
    data object SaveProfile : PanelEvent
    data object TakePhoto : PanelEvent
    /** AC-22: build the legend PDF and open the share sheet (handled by the route). */
    data object ShareLegend : PanelEvent
    /** Open HM-Mapping (handled by the route). */
    data object OpenMapping : PanelEvent
    data object ShowInsights : PanelEvent
    data object DismissInsights : PanelEvent
    data object OpenLoadEstimate : PanelEvent
    data class LoadFormChanged(val form: LoadForm) : PanelEvent
    data object CalculateLoad : PanelEvent
    data object CloseLoadEstimate : PanelEvent
    /** Open HM-Breaker for [slot]: an existing [breakerId], or an empty slot/tandem half. */
    data class OpenBreaker(val slot: Int, val breakerId: String?, val tandemIndex: Int? = null) : PanelEvent
    data class SlotLongPressed(val slot: Int) : PanelEvent
    data class SlotEditChanged(val value: String) : PanelEvent
    data object ApplySlotCount : PanelEvent
}
