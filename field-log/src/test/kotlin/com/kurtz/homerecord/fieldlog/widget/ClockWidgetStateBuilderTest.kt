package com.kurtz.homerecord.fieldlog.widget

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.punch.ClockStatus
import org.junit.Test

// AC-F17 (widget shows clock status and the job)
class ClockWidgetStateBuilderTest {
    private val names = mapOf("j1" to "Garage subpanel")

    @Test fun outShowsNotClockedIn() {
        assertThat(ClockWidgetStateBuilder.build(ClockStatus.Out, names)).isEqualTo(ClockWidgetState.Out)
    }

    @Test fun workingShowsJobAndShiftStart() {
        val s = ClockWidgetStateBuilder.build(ClockStatus.Working("j1", 1_000), names)
        assertThat(s).isEqualTo(ClockWidgetState.Working("Garage subpanel", 1_000))
    }

    @Test fun breakShowsWhenTheBreakStarted() {
        val s = ClockWidgetStateBuilder.build(ClockStatus.OnBreak("j1", 1_000, 2_000), names)
        assertThat(s).isEqualTo(ClockWidgetState.OnBreak("Garage subpanel", 2_000))
    }

    @Test fun unknownOrMissingJobHasNoName() {
        assertThat(ClockWidgetStateBuilder.build(ClockStatus.Working(null, 5), names)).isEqualTo(ClockWidgetState.Working(null, 5))
        assertThat(ClockWidgetStateBuilder.build(ClockStatus.Working("gone", 5), names)).isEqualTo(ClockWidgetState.Working(null, 5))
    }
}
