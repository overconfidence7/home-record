package com.kurtz.homerecord.core.mapping

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs one mapping-mode session (AC-C42): feeds the charger's changes into [MappingStateMachine] and keeps the alarm in
 * step with the state. [leave] is final: it stops the charger watch and the alarm, so nothing sounds after the screen
 * is gone.
 */
class MappingController(
    private val scope: CoroutineScope,
    private val power: PowerMonitor,
    private val alarm: MappingAlarm,
    private val now: () -> Long = System::currentTimeMillis,
    private val machine: MappingStateMachine = MappingStateMachine(),
) {
    private val _state = MutableStateFlow(MappingState())
    val state: StateFlow<MappingState> = _state.asStateFlow()

    private var watch: Job? = null
    private var sounding = false

    /** Starts watching the charger; call when the mapping screen shows. Calling it again does nothing. */
    fun attach() {
        if (watch != null || _state.value.phase == MappingPhase.STOPPED) return
        watch = scope.launch {
            power.plugged().collect { send(MappingEvent.Power(it, now())) }
        }
    }

    fun start() = send(MappingEvent.Start(now()))
    fun silence() = send(MappingEvent.Silence)
    fun pickDone() = send(MappingEvent.PickDone(now()))
    fun stop() = send(MappingEvent.Stop)

    /** The screen is gone: no alarm from here on. */
    fun leave() {
        send(MappingEvent.Leave)
        watch?.cancel()
        watch = null
    }

    private fun send(event: MappingEvent) {
        val next = machine.reduce(_state.value, event)
        _state.value = next
        if (next.alarm && !sounding) {
            sounding = true
            alarm.start()
        } else if (!next.alarm && sounding) {
            sounding = false
            alarm.stop()
        }
    }
}
