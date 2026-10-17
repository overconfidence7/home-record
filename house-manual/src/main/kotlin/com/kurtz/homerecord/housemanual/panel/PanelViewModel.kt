package com.kurtz.homerecord.housemanual.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.logic.LegendRow
import com.kurtz.homerecord.core.logic.LoadEstimator
import com.kurtz.homerecord.core.logic.LoadForm
import com.kurtz.homerecord.core.logic.LoadResult
import com.kurtz.homerecord.core.logic.LegendRowBuilder
import com.kurtz.homerecord.core.logic.PanelInsights
import com.kurtz.homerecord.core.logic.PanelInsightsResult
import com.kurtz.homerecord.core.logic.PanelProfileInput
import com.kurtz.homerecord.core.logic.PanelProfileError
import com.kurtz.homerecord.core.logic.PanelProfileResult
import com.kurtz.homerecord.core.logic.PanelProfileRule
import com.kurtz.homerecord.core.logic.ShrinkCheck
import com.kurtz.homerecord.core.logic.SlotCountRule
import com.kurtz.homerecord.core.logic.SlotOccupancy
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.LoadProfileRepository
import com.kurtz.homerecord.core.repo.PanelRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** HM-Panel: first panel setup (AC-5), photo (AC-6) and the slot grid data. */
@OptIn(ExperimentalCoroutinesApi::class)
class PanelViewModel(
    private val homes: HomeRepository,
    private val panels: PanelRepository,
    private val loads: LoadProfileRepository,
    private val defaultPanelName: String,
    private val currentYear: () -> Int = { java.time.Year.now().value },
) : ViewModel() {
    private data class Form(
        val slotCount: String = SlotCountRule.DEFAULT.toString(),
        val layout: PanelLayout = PanelLayout.NA_NUMBERED,
        val slotEdit: String? = null,
        val blockedAt: Int? = null,
        val profile: PanelProfileInput = PanelProfileInput(),
        val profileEdit: PanelProfileInput? = null,
        val profileErrors: Set<PanelProfileError> = emptySet(),
        val insights: PanelInsightsResult? = null,
        val loadForm: LoadForm? = null,
        val loadResult: LoadResult? = null,
    )

    private data class Content(
        val loaded: Boolean = false,
        val panel: Panel? = null,
        val breakers: List<Breaker> = emptyList(),
        val allPanels: List<Panel> = emptyList(),
        val allBreakers: List<Breaker> = emptyList(),
    )

    private val form = MutableStateFlow(Form())

    private val content = homes.observeHome().flatMapLatest { home ->
        if (home == null) {
            flowOf(Content())
        } else {
            combine(panels.observePanels(home.id), panels.observeBreakersForHome(home.id)) { list, all ->
                val panel = list.minByOrNull { it.createdAt }
                Content(true, panel, all.filter { it.panelId == panel?.id }, list, all)
            }
        }
    }

    val state: StateFlow<PanelUiState> = combine(content, form) { c, f ->
        val panelNames = c.allPanels.associate { it.id to it.name }
        val feedOptions = c.panel?.let { p ->
            PanelProfileRule.feedCandidates(p.id, c.allBreakers).map {
                FeedOption(it.id, panelNames[it.panelId].orEmpty(), it.slots.minOrNull() ?: 0, it.label)
            }
        }.orEmpty()
        PanelUiState(
            c.loaded, c.panel, c.breakers, f.slotCount, f.layout, f.slotEdit, f.blockedAt,
            f.profile, f.profileEdit, f.profileErrors, feedOptions, PanelProfileRule.feedsByBreaker(c.allPanels), f.insights,
            f.loadForm, f.loadResult,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PanelUiState())

    fun onEvent(event: PanelEvent) {
        when (event) {
            is PanelEvent.SlotCountChanged -> form.update { it.copy(slotCount = event.value) }
            is PanelEvent.LayoutChanged -> form.update { it.copy(layout = event.layout) }
            PanelEvent.SavePanel -> savePanel()
            is PanelEvent.ProfileChanged -> form.update {
                if (it.profileEdit != null) it.copy(profileEdit = event.input, profileErrors = emptySet())
                else it.copy(profile = event.input, profileErrors = emptySet())
            }
            PanelEvent.EditProfile -> state.value.panel?.let { p ->
                form.update { it.copy(profileEdit = PanelProfileInput.from(p), profileErrors = emptySet()) }
            }
            PanelEvent.CancelProfile -> form.update { it.copy(profileEdit = null, profileErrors = emptySet()) }
            PanelEvent.SaveProfile -> saveProfile()
            PanelEvent.ShowInsights -> showInsights()
            PanelEvent.DismissInsights -> form.update { it.copy(insights = null) }
            PanelEvent.OpenLoadEstimate -> openLoadEstimate()
            is PanelEvent.LoadFormChanged -> form.update { it.copy(loadForm = event.form, loadResult = null) }
            PanelEvent.CalculateLoad -> calculateLoad()
            PanelEvent.CloseLoadEstimate -> form.update { it.copy(loadForm = null, loadResult = null) }
            is PanelEvent.SlotEditChanged -> form.update { it.copy(slotEdit = event.value, blockedAt = null) }
            PanelEvent.ApplySlotCount -> applySlotCount()
            is PanelEvent.SlotLongPressed -> makeTandem(event.slot)
            PanelEvent.TakePhoto, PanelEvent.ShareLegend, PanelEvent.OpenMapping, is PanelEvent.OpenBreaker -> Unit // handled by the route
        }
    }

    /** Stores a freshly captured photo (relative media path) on the current panel. */
    fun onPhotoCaptured(relativePath: String) {
        viewModelScope.launch {
            val panel = state.value.panel ?: return@launch
            panels.setPanelPhoto(panel.id, relativePath)
        }
    }

    /** AC-23: legend rows for the current panel; [typeLabel] supplies the localized breaker type. */
    suspend fun legendRows(feedsNote: (String) -> String = { "" }, typeLabel: (Breaker) -> String): List<LegendRow> {
        val panel = state.value.panel ?: return emptyList()
        val home = homes.getHome() ?: return emptyList()
        val roomNames = homes.getRooms(home.id).associate { it.id to it.name }
        val roomsByBreaker = panels.observeBreakerRoomsForHome(home.id).first().groupBy({ it.breakerId }, { it.roomId })
        return LegendRowBuilder.build(
            panel.slotCount,
            panels.observeBreakers(panel.id).first(),
            { b -> roomsByBreaker[b.id].orEmpty().mapNotNull { roomNames[it] } },
            typeLabel,
            { b -> state.value.feedNotes[b.id]?.let(feedsNote) },
        )
    }

    /** AC-106/AC-113: recomputed from the database on every open; nothing is written. */
    private fun showInsights() {
        viewModelScope.launch {
            val panel = state.value.panel ?: return@launch
            val home = homes.getHome() ?: return@launch
            val result = PanelInsights.compute(
                panel,
                panels.observeBreakers(panel.id).first(),
                homes.getRooms(home.id),
                panels.observeBreakerRoomsForHome(home.id).first(),
                panels.observeEndpointsForHome(home.id).first(),
            )
            form.update { it.copy(insights = result) }
        }
    }

    /** AC-114: prefill square feet from the Home and everything else from the saved LoadProfile. */
    private fun openLoadEstimate() {
        viewModelScope.launch {
            val home = homes.getHome() ?: return@launch
            form.update { it.copy(loadForm = LoadForm.from(home.squareFeet, loads.get(home.id)), loadResult = null) }
        }
    }

    /** AC-114/AC-115: saves the inputs as the LoadProfile (and square feet on the Home), then estimates. */
    private fun calculateLoad() {
        val loadForm = form.value.loadForm ?: return
        val panel = state.value.panel ?: return
        val input = loadForm.toInput(panel.ratingAmps) ?: return
        viewModelScope.launch {
            val home = homes.getHome() ?: return@launch
            loads.save(home.id, input.appliances, input.hvac, input.hvacVa, input.stripVa)
            if (home.squareFeet != input.squareFeet) homes.updateHome(home.copy(squareFeet = input.squareFeet))
            form.update { it.copy(loadResult = LoadEstimator.estimate(input)) }
        }
    }

    private fun applySlotCount() {
        val panel = state.value.panel ?: return
        val count = SlotCountRule.parse(form.value.slotEdit ?: return) ?: return
        viewModelScope.launch {
            when (val result = panels.setSlotCount(panel.id, count)) {
                ShrinkCheck.Ok -> form.update { it.copy(slotEdit = null, blockedAt = null) }
                is ShrinkCheck.Blocked -> form.update { it.copy(blockedAt = result.highestOccupied) }
            }
        }
    }

    /** AC-10: long-press on a lone one-pole breaker splits its slot into a tandem pair. */
    private fun makeTandem(slot: Int) {
        val candidate = SlotOccupancy.tandemCandidate(slot, state.value.breakers) ?: return
        viewModelScope.launch { panels.makeTandem(candidate.id) }
    }

    private fun savePanel() {
        val count = SlotCountRule.parse(form.value.slotCount) ?: return
        val layout = form.value.layout
        val profile = when (val r = PanelProfileRule.validate(form.value.profile, currentYear())) {
            is PanelProfileResult.Valid -> r.profile
            is PanelProfileResult.Invalid -> return form.update { it.copy(profileErrors = r.errors) }
        }
        viewModelScope.launch {
            val home = homes.getHome() ?: return@launch
            if (panels.observePanels(home.id).first().isNotEmpty()) return@launch
            panels.createPanel(home.id, defaultPanelName, count, layout, profile = profile)
        }
    }

    /** AC-101: validates and stores the details editor on the current panel. */
    private fun saveProfile() {
        val panel = state.value.panel ?: return
        val input = form.value.profileEdit ?: return
        val profile = when (val r = PanelProfileRule.validate(input, currentYear())) {
            is PanelProfileResult.Valid -> r.profile
            is PanelProfileResult.Invalid -> return form.update { it.copy(profileErrors = r.errors) }
        }
        viewModelScope.launch {
            panels.updateProfile(panel.id, profile)
            form.update { it.copy(profileEdit = null, profileErrors = emptySet()) }
        }
    }
}
