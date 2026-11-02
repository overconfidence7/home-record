package com.kurtz.homerecord.housemodel.overlays

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric for org.json in the points codec.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OverlayKindTest {
    @Test
    fun everyKindHasAUniqueWireNameThatReadsBack() {
        assertThat(OverlayKind.entries.map { it.wire }.toSet()).hasSize(OverlayKind.entries.size)
        OverlayKind.entries.forEach { assertThat(OverlayKind.fromWire(it.wire)).isEqualTo(it) }
        assertThat(OverlayKind.fromWire("sprinkler")).isEqualTo(OverlayKind.OTHER)
        assertThat(OverlayKind.fromWire(null)).isEqualTo(OverlayKind.OTHER)
    }

    @Test
    fun everySystemLayerHasKindsAndTheElectricalLayerHoldsBoxesAndDevices() {
        BlueprintLayer.entries.filter { it != BlueprintLayer.ITEMS }.forEach { assertThat(OverlayKind.onLayer(it)).isNotEmpty() }
        assertThat(OverlayKind.onLayer(BlueprintLayer.ELECTRICAL)).containsExactly(
            OverlayKind.ELECTRICAL_WIRE, OverlayKind.JUNCTION_BOX, OverlayKind.OUTLET, OverlayKind.SWITCH, OverlayKind.LIGHT,
        )
        assertThat(OverlayKind.onLayer(BlueprintLayer.ITEMS)).isEmpty()
    }

    @Test
    fun runsDrawAsLinesAndDevicesAsPoints() {
        assertThat(OverlayKind.WATER_HOT.shape).isEqualTo(OverlayShape.LINE)
        assertThat(OverlayKind.OUTLET.shape).isEqualTo(OverlayShape.POINT)
    }

    @Test
    fun depthReadsBackAndDefaultsBehindDrywall() {
        OverlayDepth.entries.forEach { assertThat(OverlayDepth.fromStored(it.name)).isEqualTo(it) }
        assertThat(OverlayDepth.fromStored("IN_WALL")).isEqualTo(OverlayDepth.BEHIND_DRYWALL)
    }

    @Test
    fun pointsJsonRoundTripsAndDamageReadsEmpty() {
        val points = listOf(OverlayPoint(0.25f, 0.5f), OverlayPoint(1f, 0f))
        assertThat(OverlayPoints.decode(OverlayPoints.encode(points))).isEqualTo(points)
        assertThat(OverlayPoints.decode("not json")).isEmpty()
        assertThat(OverlayPoints.decode("""[{"x":0.1},{"x":0.2,"y":0.3}]""")).containsExactly(OverlayPoint(0.2f, 0.3f))
    }
}
