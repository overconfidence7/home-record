package com.kurtz.homerecord.core.mapping

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeChargingMonitor(var charging: Boolean = false) : ChargingMonitor {
    val flow = MutableSharedFlow<PowerEvent>(extraBufferCapacity = 8)
    override fun isCharging() = charging
    override fun events(): Flow<PowerEvent> = flow
}

class FakeAlarmPlayer : AlarmPlayer {
    override var isPlaying = false
        private set
    var startCount = 0
        private set
    override fun start() { if (!isPlaying) startCount++; isPlaying = true }
    override fun stop() { isPlaying = false }
}
