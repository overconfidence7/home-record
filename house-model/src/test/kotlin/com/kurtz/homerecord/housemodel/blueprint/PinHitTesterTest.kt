package com.kurtz.homerecord.housemodel.blueprint

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-F35: a tap on a task pin's head opens that task; the head is a 48 dp target above the pin's tip.
@AcceptanceCriterion("AC-F35")
class PinHitTesterTest {
    private val view = BlueprintView(scale = 2f, originX = 10f, originY = 10f)
    private val outlet = BlueprintPin("t1", "Move the outlet", xIn = 50, yIn = 50, number = 1)
    private val patch = BlueprintPin("t2", "Patch drywall", xIn = 60, yIn = 50, number = 2)

    @Test fun headSitsAboveTheTip() {
        assertThat(PinHitTester.head(outlet, view, headLiftPx = 40f)).isEqualTo(110f to 70f)
    }

    @Test fun tapOnTheHeadFindsThePinAndATapAwayFindsNone() {
        assertThat(PinHitTester.pinAt(listOf(outlet), view, 115f, 75f, 40f, 48f)?.id).isEqualTo("t1")
        assertThat(PinHitTester.pinAt(listOf(outlet), view, 110f, 200f, 40f, 48f)).isNull()
        assertThat(PinHitTester.pinAt(emptyList(), view, 110f, 70f, 40f, 48f)).isNull()
    }

    @Test fun nearestHeadWinsWhenPinsOverlap() {
        // Heads at x 110 and 130: a tap at 126 is nearer the second.
        assertThat(PinHitTester.pinAt(listOf(outlet, patch), view, 126f, 70f, 40f, 48f)?.id).isEqualTo("t2")
        assertThat(PinHitTester.pinAt(listOf(outlet, patch), view, 112f, 70f, 40f, 48f)?.id).isEqualTo("t1")
    }
}
