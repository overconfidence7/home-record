package com.kurtz.homerecord.core.mapping

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-C42: MappingStateMachine rules (arm while charging, alarm on drop, pick, leave is final)
@AcceptanceCriterion("AC-C42")
class MappingStateMachineTest {
    private val machine = MappingStateMachine(settleMs = 2_000)

    private fun run(vararg events: MappingEvent, from: MappingState = MappingState()): MappingState =
        events.fold(from) { s, e -> machine.reduce(s, e) }

    @Test fun startWhileChargingArms() {
        val s = run(MappingEvent.Power(true, 0), MappingEvent.Start(1_000))
        assertThat(s.phase).isEqualTo(MappingPhase.ARMED)
        assertThat(s.keepScreenOn).isTrue()
        assertThat(s.alarm).isFalse()
    }

    @Test fun startUnpluggedWaitsThenArmsWhenPlugged() {
        val waiting = run(MappingEvent.Power(false, 0), MappingEvent.Start(1_000))
        assertThat(waiting.phase).isEqualTo(MappingPhase.WAITING_FOR_POWER)
        assertThat(waiting.keepScreenOn).isTrue()
        assertThat(run(MappingEvent.Power(false, 5_000), from = waiting).phase).isEqualTo(MappingPhase.WAITING_FOR_POWER)
        val armed = run(MappingEvent.Power(true, 6_000), from = waiting)
        assertThat(armed.phase).isEqualTo(MappingPhase.ARMED)
        assertThat(armed.armedAt).isEqualTo(6_000)
    }

    @Test fun powerDropWhileArmedSoundsTheAlarm() {
        val s = run(MappingEvent.Power(true, 0), MappingEvent.Start(0), MappingEvent.Power(false, 30_000))
        assertThat(s.phase).isEqualTo(MappingPhase.ALARM)
        assertThat(s.alarm).isTrue()
        assertThat(s.trippedAt).isEqualTo(30_000)
    }

    @Test fun dropRightAfterArmingIsAShakyPlugNotABreaker() {
        val s = run(MappingEvent.Power(true, 0), MappingEvent.Start(0), MappingEvent.Power(false, 1_500))
        assertThat(s.phase).isEqualTo(MappingPhase.WAITING_FOR_POWER)
        assertThat(s.alarm).isFalse()
    }

    @Test fun alarmKeepsSoundingWhenPowerComesBackUntilSilenced() {
        val alarm = run(MappingEvent.Power(true, 0), MappingEvent.Start(0), MappingEvent.Power(false, 10_000), MappingEvent.Power(true, 12_000))
        assertThat(alarm.alarm).isTrue()
        val picking = run(MappingEvent.Silence, from = alarm)
        assertThat(picking.phase).isEqualTo(MappingPhase.PICKING)
        assertThat(picking.alarm).isFalse()
        assertThat(picking.trippedAt).isEqualTo(10_000)
    }

    @Test fun pickDoneRearmsWhenChargingOrWaitsForTheNextOutlet() {
        val picking = run(MappingEvent.Power(true, 0), MappingEvent.Start(0), MappingEvent.Power(false, 10_000), MappingEvent.Silence)
        val waiting = run(MappingEvent.PickDone(20_000), from = picking)
        assertThat(waiting.phase).isEqualTo(MappingPhase.WAITING_FOR_POWER)
        assertThat(waiting.trippedAt).isNull()
        val replugged = run(MappingEvent.Power(true, 25_000), MappingEvent.Silence, MappingEvent.PickDone(26_000), from = picking)
        assertThat(replugged.phase).isEqualTo(MappingPhase.ARMED)
        assertThat(replugged.armedAt).isEqualTo(26_000)
    }

    @Test fun eventsOutOfPlaceChangeNothing() {
        val idle = MappingState(plugged = true)
        assertThat(run(MappingEvent.Silence, MappingEvent.PickDone(1), from = idle)).isEqualTo(idle)
        assertThat(run(MappingEvent.Power(false, 1), from = idle).phase).isEqualTo(MappingPhase.IDLE)
        assertThat(idle.keepScreenOn).isFalse()
    }

    @Test fun stopGoesBackToIdle() {
        val s = run(MappingEvent.Power(true, 0), MappingEvent.Start(0), MappingEvent.Power(false, 10_000), MappingEvent.Stop)
        assertThat(s.phase).isEqualTo(MappingPhase.IDLE)
        assertThat(s.alarm).isFalse()
        assertThat(s.keepScreenOn).isFalse()
    }

    @Test fun leaveIsFinalNoAlarmAfterward() {
        val left = run(MappingEvent.Power(true, 0), MappingEvent.Start(0), MappingEvent.Leave)
        assertThat(left.phase).isEqualTo(MappingPhase.STOPPED)
        val after = run(MappingEvent.Power(false, 60_000), MappingEvent.Start(61_000), from = left)
        assertThat(after.phase).isEqualTo(MappingPhase.STOPPED)
        assertThat(after.alarm).isFalse()
        assertThat(after.keepScreenOn).isFalse()
    }
}
