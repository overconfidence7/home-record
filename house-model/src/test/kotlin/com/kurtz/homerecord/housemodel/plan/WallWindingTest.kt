package com.kurtz.homerecord.housemodel.plan

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// R10b: wall space reads left to right from inside the room whichever way the corners are stored. AC-C20
@AcceptanceCriterion("AC-C20")
class WallWindingTest {
    private val clockwise = listOf(Pt(0, 0), Pt(144, 0), Pt(144, 120), Pt(0, 120))

    @Test fun cornersClockwiseOnThePlanRunForward() {
        assertThat(WallWinding.runsBackward(clockwise)).isFalse()
        assertThat(WallWinding.signedArea2(clockwise)).isEqualTo(2L * 144 * 120)
    }

    @Test fun cornersCounterClockwiseRunBackward() {
        assertThat(WallWinding.runsBackward(clockwise.reversed())).isTrue()
    }

    @Test fun wallXAndAlongAreInversesBothWays() {
        assertThat(WallWinding.wallX(0.25f, backward = false)).isEqualTo(0.25f)
        assertThat(WallWinding.wallX(0.25f, backward = true)).isEqualTo(0.75f)
        listOf(false, true).forEach { b ->
            assertThat(WallWinding.along(WallWinding.wallX(0.3f, b), b)).isWithin(1e-6f).of(0.3f)
        }
    }

    @Test fun aDegenerateOutlineRunsForward() {
        assertThat(WallWinding.runsBackward(listOf(Pt(0, 0), Pt(10, 0)))).isFalse()
    }
}
