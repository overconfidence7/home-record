package com.kurtz.homerecord.housemanual.mapping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.mapping.MappingAlarm
import com.kurtz.homerecord.core.mapping.MappingController
import com.kurtz.homerecord.core.mapping.PowerMonitor
import com.kurtz.homerecord.housemodel.panel.FeedChoices
import com.kurtz.homerecord.housemodel.panel.PanelRepository
import com.kurtz.homerecord.housemodel.panel.PanelState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * HM-Mapping (AC-C42): the charger and alarm run through [MappingController]; after the alarm the homeowner picks the
 * breaker and the room or item the outlet feeds, and [PanelRepository.confirmLink] marks that link Confirmed. The route
 * calls [leave] when the screen goes away (back or app in the background), so nothing sounds after that.
 */
class MappingViewModel(
    private val repo: PanelRepository,
    itemId: String,
    itemName: Flow<String?>,
    choices: Flow<FeedChoices>,
    power: PowerMonitor,
    alarm: MappingAlarm,
    now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val controller = MappingController(viewModelScope, power, alarm, now)

    private data class Pick(val breakerId: String? = null, val roomId: String? = null, val itemId: String? = null)

    private val pick = MutableStateFlow(Pick())
    private val confirmed = MutableStateFlow(0)
    /** True while a Save is writing; a second tap must not confirm the link twice (R13). */
    private var saving = false

    private val panel: Flow<PanelState?> = repo.observe(itemId)

    val state: StateFlow<MappingUiState> = combine(
        itemName, panel.onStart { emit(null) }, choices.onStart { emit(FeedChoices()) }, controller.state,
        combine(pick, confirmed) { p, c -> p to c },
    ) { name, p, c, m, (pk, count) ->
        MappingUiState(
            loading = p == null,
            panelName = name.orEmpty(),
            phase = m.phase, plugged = m.plugged, keepScreenOn = m.keepScreenOn,
            breakers = p?.breakers.orEmpty().sortedBy { it.number }.map { MappingBreaker(it.id, it.number, it.label, it.amps) },
            rooms = c.rooms, items = c.items,
            breakerId = pk.breakerId, roomId = pk.roomId, itemId = pk.itemId,
            confirmed = count,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MappingUiState())

    init {
        controller.attach()
    }

    /** The screen is gone or in the background: stop the alarm and stop watching the charger. */
    fun leave() = controller.leave()

    fun onEvent(event: MappingEvent) {
        when (event) {
            MappingEvent.Back -> Unit
            MappingEvent.Start -> controller.start()
            MappingEvent.Stop -> {
                pick.value = Pick()
                controller.stop()
            }
            MappingEvent.Silence -> controller.silence()
            MappingEvent.Resume -> controller.resume()
            is MappingEvent.PickBreaker -> pick.value = pick.value.copy(breakerId = event.id.takeUnless { it == pick.value.breakerId })
            is MappingEvent.PickRoom -> pick.value = pick.value.copy(roomId = event.id.takeUnless { it == pick.value.roomId })
            is MappingEvent.PickItem -> pick.value = pick.value.copy(itemId = event.id.takeUnless { it == pick.value.itemId })
            MappingEvent.Skip -> {
                pick.value = Pick()
                controller.pickDone()
            }
            MappingEvent.Save -> {
                val p = pick.value
                val breaker = p.breakerId ?: return
                if (p.roomId == null && p.itemId == null || saving) return
                saving = true
                viewModelScope.launch {
                    try {
                        if (repo.confirmLink(breaker, p.roomId, p.itemId)) confirmed.value += 1
                        pick.value = Pick()
                        controller.pickDone()
                    } finally {
                        saving = false
                    }
                }
            }
        }
    }

    override fun onCleared() {
        controller.leave()
    }
}
