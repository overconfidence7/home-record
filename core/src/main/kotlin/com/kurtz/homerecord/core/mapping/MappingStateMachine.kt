package com.kurtz.homerecord.core.mapping

/**
 * Where a mapping-mode session stands (AC-C42). The phone sits charging from an outlet; the homeowner flips breakers at
 * the panel, and the phone sounds an alarm when its power drops, so they know which breaker feeds that outlet.
 */
enum class MappingPhase {
    /** On the screen, not started yet. */
    IDLE,

    /** Started, but the phone is not charging; nothing can trip until it is plugged in. */
    WAITING_FOR_POWER,

    /** Charging and watching for the power to drop. */
    ARMED,

    /** The power dropped while armed: the alarm is sounding. */
    ALARM,

    /** The alarm was silenced; the homeowner picks the breaker and what the outlet feeds. */
    PICKING,

    /** Left the screen: nothing sounds any more, whatever the charger does. */
    STOPPED,
}

/** The machine's whole state. [trippedAt] is when the power last dropped while armed. */
data class MappingState(
    val phase: MappingPhase = MappingPhase.IDLE,
    val plugged: Boolean = false,
    val armedAt: Long? = null,
    val trippedAt: Long? = null,
) {
    /** True only while the alarm should be sounding. */
    val alarm: Boolean get() = phase == MappingPhase.ALARM

    /** Keep the screen on while the session can trip or is sounding (AC-C42). */
    val keepScreenOn: Boolean
        get() = phase == MappingPhase.WAITING_FOR_POWER || phase == MappingPhase.ARMED || phase == MappingPhase.ALARM
}

sealed interface MappingEvent {
    /** The charger reported plugged or unplugged at [at] (epoch millis). */
    data class Power(val plugged: Boolean, val at: Long) : MappingEvent

    /** The homeowner pressed Start. */
    data class Start(val at: Long) : MappingEvent

    /** Silence the alarm and go pick the breaker. */
    data object Silence : MappingEvent

    /** The pick was saved or skipped; get ready for the next outlet. */
    data class PickDone(val at: Long) : MappingEvent

    /** Stop button: back to the start, nothing armed. */
    data object Stop : MappingEvent

    /** The screen went away (back, home, process end). Final. */
    data object Leave : MappingEvent
}

/**
 * Pure rules for mapping mode (AC-C42 `[unit]`). A drop within [settleMs] of arming is a shaky plug, not a breaker,
 * so it goes back to waiting without an alarm. The alarm keeps sounding until silenced, even if the power comes back,
 * so it can be heard from the panel.
 */
class MappingStateMachine(private val settleMs: Long = SETTLE_MS) {

    fun reduce(state: MappingState, event: MappingEvent): MappingState {
        if (state.phase == MappingPhase.STOPPED) return state
        return when (event) {
            MappingEvent.Leave -> MappingState(phase = MappingPhase.STOPPED, plugged = state.plugged)
            MappingEvent.Stop -> MappingState(phase = MappingPhase.IDLE, plugged = state.plugged)
            is MappingEvent.Start -> when (state.phase) {
                MappingPhase.IDLE -> ready(state, event.at)
                else -> state
            }
            is MappingEvent.Power -> power(state, event)
            MappingEvent.Silence -> if (state.phase == MappingPhase.ALARM) state.copy(phase = MappingPhase.PICKING) else state
            is MappingEvent.PickDone -> if (state.phase == MappingPhase.PICKING) ready(state.copy(trippedAt = null), event.at) else state
        }
    }

    private fun ready(state: MappingState, at: Long): MappingState =
        if (state.plugged) state.copy(phase = MappingPhase.ARMED, armedAt = at)
        else state.copy(phase = MappingPhase.WAITING_FOR_POWER, armedAt = null)

    private fun power(state: MappingState, event: MappingEvent.Power): MappingState {
        val next = state.copy(plugged = event.plugged)
        return when (state.phase) {
            MappingPhase.WAITING_FOR_POWER -> if (event.plugged) next.copy(phase = MappingPhase.ARMED, armedAt = event.at) else next
            MappingPhase.ARMED -> when {
                event.plugged -> next
                state.armedAt != null && event.at - state.armedAt < settleMs -> next.copy(phase = MappingPhase.WAITING_FOR_POWER, armedAt = null)
                else -> next.copy(phase = MappingPhase.ALARM, trippedAt = event.at)
            }
            else -> next
        }
    }

    companion object {
        /** How long the phone must be charging before a drop counts as a breaker. */
        const val SETTLE_MS = 2_000L
    }
}
