package com.kurtz.homerecord.core.mapping

import com.kurtz.homerecord.core.mapping.MappingState.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingStateMachineTest {
    @Test fun notChargingIsIdleAndCannotStart() {
        val m = MappingStateMachine(initiallyCharging = false)
        assertEquals(Idle, m.state)
        assertFalse(m.canStart)
        assertEquals(Idle, m.start())
    }

    @Test fun plugInEnablesStart() {
        val m = MappingStateMachine(false)
        assertEquals(PluggedIn, m.onPower(PowerEvent.Connected))
        assertTrue(m.canStart)
        assertEquals(Armed, m.start())
    }

    @Test fun unplugBeforeArmingGoesIdle() {
        val m = MappingStateMachine(true)
        assertEquals(Idle, m.onPower(PowerEvent.Disconnected))
    }

    @Test fun disconnectWhileArmedRaisesAlarm() {
        val m = MappingStateMachine(true)
        m.start()
        assertEquals(Alarm, m.onPower(PowerEvent.Disconnected))
    }

    @Test fun reconnectWhileArmedStaysArmed() {
        val m = MappingStateMachine(true)
        m.start()
        assertEquals(Armed, m.onPower(PowerEvent.Connected))
    }

    @Test fun stopEndsAlarmIntoConfirm() {
        val m = MappingStateMachine(true)
        m.start(); m.onPower(PowerEvent.Disconnected)
        assertEquals(Confirm, m.stop())
    }

    @Test fun reconnectEndsAlarmIntoConfirm() {
        val m = MappingStateMachine(true)
        m.start(); m.onPower(PowerEvent.Disconnected)
        assertEquals(Confirm, m.onPower(PowerEvent.Connected))
    }

    @Test fun stopOutsideAlarmIsIgnored() {
        val m = MappingStateMachine(true)
        assertEquals(PluggedIn, m.stop())
        m.start()
        assertEquals(Armed, m.stop())
    }

    @Test fun disarmReturnsToChargingState() {
        val m = MappingStateMachine(true)
        m.start()
        assertEquals(PluggedIn, m.disarm())
        m.start(); m.onPower(PowerEvent.Disconnected)
        assertEquals(Idle, m.disarm())
    }

    @Test fun confirmIgnoresPowerEvents() {
        val m = MappingStateMachine(true)
        m.start(); m.onPower(PowerEvent.Disconnected); m.stop()
        assertEquals(Confirm, m.onPower(PowerEvent.Connected))
        assertEquals(Confirm, m.onPower(PowerEvent.Disconnected))
        assertEquals(Idle, m.disarm())
    }

    @Test fun cannotStartWhileArmedOrAlarm() {
        val m = MappingStateMachine(true)
        m.start()
        assertFalse(m.canStart)
        m.onPower(PowerEvent.Disconnected)
        assertFalse(m.canStart)
        assertEquals(Alarm, m.start())
    }
}
