package com.kurtz.homerecord.housemodel.scene

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-C20: the 3D drawn tag needs two good pixel checks in a row ([judge:hm-3d-room-blank-recurs])
class SceneDrawnGateTest {
    @AcceptanceCriterion("AC-C20")
    @Test fun oneGoodCheckIsNotEnough() {
        val gate = SceneDrawnGate()
        assertThat(gate.onCheck(true)).isFalse()
        assertThat(gate.onCheck(true)).isTrue()
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun aBlankCheckBetweenGoodOnesStartsAgain() {
        val gate = SceneDrawnGate()
        gate.onCheck(true)
        assertThat(gate.onCheck(false)).isFalse()
        assertThat(gate.onCheck(true)).isFalse()
        assertThat(gate.onCheck(true)).isTrue()
    }

    @AcceptanceCriterion("AC-C20")
    @Test fun resetForANewCameraForgetsEarlierChecks() {
        val gate = SceneDrawnGate()
        gate.onCheck(true)
        gate.reset()
        assertThat(gate.onCheck(true)).isFalse()
    }
}
