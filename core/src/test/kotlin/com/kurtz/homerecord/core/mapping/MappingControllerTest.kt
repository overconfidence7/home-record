package com.kurtz.homerecord.core.mapping

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

private class FakePower(initial: Boolean) : PowerMonitor {
    val flow = MutableStateFlow(initial)
    override fun plugged(): Flow<Boolean> = flow
}

private class FakeAlarm : MappingAlarm {
    var playing = false
    var starts = 0
    override fun start() { playing = true; starts++ }
    override fun stop() { playing = false }
}

// AC-C42: the charger drives the alarm; leaving the screen silences it for good
@OptIn(ExperimentalCoroutinesApi::class)
@AcceptanceCriterion("AC-C42")
class MappingControllerTest {
    private var clock = 0L

    @Test fun unplugWhileArmedStartsTheAlarmAndSilenceStopsIt() = runTest(UnconfinedTestDispatcher()) {
        val power = FakePower(true)
        val alarm = FakeAlarm()
        val c = MappingController(backgroundScope, power, alarm, { clock })
        c.attach()
        c.start()
        assertThat(c.state.value.phase).isEqualTo(MappingPhase.ARMED)
        clock = 10_000
        power.flow.value = false
        assertThat(alarm.playing).isTrue()
        power.flow.value = true
        assertThat(alarm.playing).isTrue()
        assertThat(alarm.starts).isEqualTo(1)
        c.silence()
        assertThat(alarm.playing).isFalse()
        assertThat(c.state.value.phase).isEqualTo(MappingPhase.PICKING)
        clock = 20_000
        c.pickDone()
        assertThat(c.state.value.phase).isEqualTo(MappingPhase.ARMED)
    }

    @Test fun leavingStopsTheAlarmAndIgnoresTheCharger() = runTest(UnconfinedTestDispatcher()) {
        val power = FakePower(true)
        val alarm = FakeAlarm()
        val c = MappingController(backgroundScope, power, alarm, { clock })
        c.attach()
        c.start()
        clock = 10_000
        power.flow.value = false
        assertThat(alarm.playing).isTrue()
        c.leave()
        assertThat(alarm.playing).isFalse()
        power.flow.value = true
        clock = 30_000
        power.flow.value = false
        c.attach()
        c.start()
        assertThat(alarm.playing).isFalse()
        assertThat(alarm.starts).isEqualTo(1)
        assertThat(c.state.value.phase).isEqualTo(MappingPhase.STOPPED)
    }

    @Test fun leavingWhileArmedMeansNoAlarmOnALaterDrop() = runTest(UnconfinedTestDispatcher()) {
        val power = FakePower(true)
        val alarm = FakeAlarm()
        val c = MappingController(backgroundScope, power, alarm, { clock })
        c.attach()
        c.start()
        c.leave()
        clock = 60_000
        power.flow.value = false
        assertThat(alarm.starts).isEqualTo(0)
        assertThat(c.state.value.keepScreenOn).isFalse()
    }

    @Test fun resumeAfterLeavingStartsFreshAndUnarmed() = runTest(UnconfinedTestDispatcher()) {
        val power = FakePower(true)
        val alarm = FakeAlarm()
        val c = MappingController(backgroundScope, power, alarm, { clock })
        c.attach()
        c.start()
        c.leave()
        c.resume()
        assertThat(c.state.value.phase).isEqualTo(MappingPhase.IDLE)
        clock = 10_000
        power.flow.value = false
        assertThat(alarm.starts).isEqualTo(0)
        power.flow.value = true
        c.start()
        clock = 20_000
        power.flow.value = false
        assertThat(alarm.playing).isTrue()
    }
}
