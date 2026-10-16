package com.kurtz.homerecord.core.mapping

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakesTest {
    @Test fun alarmPlayerTogglesAndCountsStartsOnce() {
        val p = FakeAlarmPlayer()
        p.start(); p.start()
        assertTrue(p.isPlaying)
        assertEquals(1, p.startCount)
        p.stop()
        assertFalse(p.isPlaying)
    }

    @Test fun monitorEventsDriveMachineToAlarm() = runTest(UnconfinedTestDispatcher()) {
        val monitor = FakeChargingMonitor(charging = true)
        val machine = MappingStateMachine(monitor.isCharging())
        machine.start()
        val job = launch { machine.onPower(monitor.events().first()) }
        monitor.flow.emit(PowerEvent.Disconnected)
        job.join()
        assertEquals(MappingState.Alarm, machine.state)
    }
}
