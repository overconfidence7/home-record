package com.kurtz.homerecord.housemodel.overlays

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import org.junit.Test

// AC-C28: a tap picks the overlay under the finger so it can be edited, and a handle under the finger so its point can be dragged.
class OverlayHitTestTest {
    private val pipe = listOf(OverlayPoint(100f, 100f), OverlayPoint(300f, 100f), OverlayPoint(300f, 400f))
    private val outlet = listOf(OverlayPoint(500f, 500f))

    @Test
    @AcceptanceCriterion("AC-C28")
    fun aTapAlongARunPicksItEvenBetweenItsPoints() {
        assertThat(OverlayHitTest.overlayAt(listOf(pipe, outlet), 200f, 110f, 24f)).isEqualTo(0)
        assertThat(OverlayHitTest.overlayAt(listOf(pipe, outlet), 290f, 250f, 24f)).isEqualTo(0)
    }

    @Test
    @AcceptanceCriterion("AC-C28")
    fun aTapNearASpotPicksItAndATapOnBareWallPicksNothing() {
        assertThat(OverlayHitTest.overlayAt(listOf(pipe, outlet), 510f, 515f, 24f)).isEqualTo(1)
        assertThat(OverlayHitTest.overlayAt(listOf(pipe, outlet), 700f, 50f, 24f)).isNull()
    }

    @Test
    fun theNearestWinsAndATieGoesToTheOneOnTop() {
        val close = listOf(OverlayPoint(0f, 10f), OverlayPoint(100f, 10f))
        val far = listOf(OverlayPoint(0f, 20f), OverlayPoint(100f, 20f))
        assertThat(OverlayHitTest.overlayAt(listOf(close, far), 50f, 12f, 24f)).isEqualTo(0)
        val same = listOf(OverlayPoint(0f, 10f), OverlayPoint(100f, 10f))
        assertThat(OverlayHitTest.overlayAt(listOf(close, same), 50f, 12f, 24f)).isEqualTo(1)
    }

    @Test
    @AcceptanceCriterion("AC-C28")
    fun aHandleIsFoundByItsNearestPoint() {
        assertThat(OverlayHitTest.pointAt(pipe, 295f, 105f, 24f)).isEqualTo(1)
        assertThat(OverlayHitTest.pointAt(pipe, 200f, 100f, 24f)).isNull()
    }

    @Test
    fun anOverlayWithNoPointsIsNeverHit() {
        assertThat(OverlayHitTest.overlayAt(listOf(emptyList()), 0f, 0f, 1000f)).isNull()
        assertThat(OverlayHitTest.distance(listOf(OverlayPoint(3f, 4f), OverlayPoint(3f, 4f)), 0f, 0f)).isEqualTo(5f)
    }
}
