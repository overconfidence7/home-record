package com.kurtz.homerecord.core.mapping

/** Power-source changes reported by the [ChargingMonitor]. */
sealed interface PowerEvent {
    data object Connected : PowerEvent
    data object Disconnected : PowerEvent
}

/** Where the one-person mapping flow is. */
enum class MappingState { Idle, PluggedIn, Armed, Alarm, Confirm }

/** Pure transition rules for mapping mode (AC-35, AC-37, AC-38). */
class MappingStateMachine(initiallyCharging: Boolean) {
    var state: MappingState = if (initiallyCharging) MappingState.PluggedIn else MappingState.Idle
        private set

    private var charging: Boolean = initiallyCharging

    /** Start is only offered while the device reports charging and nothing else is in progress. */
    val canStart: Boolean get() = state == MappingState.PluggedIn

    fun onPower(event: PowerEvent): MappingState {
        charging = event == PowerEvent.Connected
        state = when (state) {
            MappingState.Idle, MappingState.PluggedIn -> idleOrPlugged()
            MappingState.Armed -> if (charging) MappingState.Armed else MappingState.Alarm
            MappingState.Alarm -> if (charging) MappingState.Confirm else MappingState.Alarm
            MappingState.Confirm -> MappingState.Confirm
        }
        return state
    }

    /** Arms the alarm. Ignored unless [canStart]. */
    fun start(): MappingState {
        if (canStart) state = MappingState.Armed
        return state
    }

    /** User taps Stop on the alarm screen. */
    fun stop(): MappingState {
        if (state == MappingState.Alarm) state = MappingState.Confirm
        return state
    }

    /** Leaves the armed or alarm state without confirming (screen exit or cancel). */
    fun disarm(): MappingState {
        state = idleOrPlugged()
        return state
    }

    private fun idleOrPlugged() = if (charging) MappingState.PluggedIn else MappingState.Idle
}
