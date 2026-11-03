package com.kurtz.homerecord.housemodel.ui

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-C28: back on the wall screen (H11: the room's Overlays list opens the wall with the overlay picked; back returns)
@AcceptanceCriterion("AC-C28")
class WallBackTest {
    @Test fun formAndDrawingAreSteppedOutOfFirst() {
        assertThat(WallBack.step(hasForm = true, drawing = true, selectedId = "o1", arrivalId = null, fromArrow = true)).isEqualTo(WallBackStep.CloseForm)
        assertThat(WallBack.step(hasForm = false, drawing = true, selectedId = null, arrivalId = null, fromArrow = true)).isEqualTo(WallBackStep.CancelDrawing)
    }

    @Test fun systemBackUnpicksAnOverlayPickedHere() {
        assertThat(WallBack.step(false, false, selectedId = "o2", arrivalId = null, fromArrow = false)).isEqualTo(WallBackStep.Unpick)
        assertThat(WallBack.step(false, false, selectedId = "o2", arrivalId = "o1", fromArrow = false)).isEqualTo(WallBackStep.Unpick)
    }

    @Test fun systemBackLeavesWhenTheOverlayIsTheOneTheWallOpenedOn() {
        assertThat(WallBack.step(false, false, selectedId = "o1", arrivalId = "o1", fromArrow = false)).isEqualTo(WallBackStep.Leave)
    }

    @Test fun topBarArrowLeavesPastASelection() {
        assertThat(WallBack.step(false, false, selectedId = "o2", arrivalId = null, fromArrow = true)).isEqualTo(WallBackStep.Leave)
    }

    @Test fun nothingOpenLeaves() {
        assertThat(WallBack.step(false, false, selectedId = null, arrivalId = "o1", fromArrow = false)).isEqualTo(WallBackStep.Leave)
    }
}
