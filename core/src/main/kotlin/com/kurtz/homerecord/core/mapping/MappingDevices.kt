package com.kurtz.homerecord.core.mapping

import kotlinx.coroutines.flow.Flow

/** Reports whether the device is charging and emits changes while collected. */
interface ChargingMonitor {
    fun isCharging(): Boolean

    /** Cold flow: registers the receiver on collect, unregisters on cancel (AC-41). */
    fun events(): Flow<PowerEvent>
}

/** Loud repeating tone plus vibration. */
interface AlarmPlayer {
    val isPlaying: Boolean
    fun start()
    fun stop()
}
