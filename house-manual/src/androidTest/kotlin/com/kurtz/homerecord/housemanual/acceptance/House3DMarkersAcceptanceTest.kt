package com.kurtz.homerecord.housemanual.acceptance

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer
import com.kurtz.homerecord.housemodel.blueprint.LayerSet
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayDepth
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.scene.FloorScene
import com.kurtz.homerecord.housemodel.scene.ItemSpot
import com.kurtz.homerecord.housemodel.scene.MarkerTarget
import com.kurtz.homerecord.housemodel.scene.SceneMarker
import com.kurtz.homerecord.housemodel.scene.SceneMarkers
import com.kurtz.homerecord.housemodel.ui.House3DEvent
import com.kurtz.homerecord.housemodel.ui.House3DScreen
import com.kurtz.homerecord.housemodel.ui.House3DUiState
import com.kurtz.homerecord.housemodel.walls.WallKey
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** HM-3D markers on the device: layer chips on the 3D screen, and each shown marker opens its record (AC-C23). */
@RunWith(AndroidJUnit4::class)
class House3DMarkersAcceptanceTest {
    @get:Rule val rule = createComposeRule()

    private val kitchen = PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)
    private val scene = FloorScene.build(listOf(kitchen), 96, emptyMap())
    private val pipe = Overlay(
        "o1", "h", "k", "w-k0", OverlayKind.WATER_COLD, "Cold to sink", null, OverlayDepth.BEHIND_DRYWALL,
        listOf(OverlayPoint(0.2f, 0.6f), OverlayPoint(0.8f, 0.6f)), WallStage.ROUGH_IN, null, null, 0L,
    )
    private val markers = SceneMarkers.place(scene, listOf(pipe), mapOf("w-k0" to WallKey("k", 0)), listOf(ItemSpot("fridge", "k", 30, 30, "Fridge")))

    @AcceptanceCriterion("AC-C23")
    @Test fun layerChipsShowMarkersAndAShownMarkerOpensItsRecord() {
        val events = mutableListOf<House3DEvent>()
        val opened = mutableListOf<SceneMarker>()
        val state = House3DUiState(
            loading = false, homeName = "Maple St", floors = listOf(FloorSummary("m", "Main", 96, false, 1)), floorId = "m",
            scene = scene, markers = markers, layers = LayerSet(setOf(BlueprintLayer.PLUMBING, BlueprintLayer.ITEMS)),
        )
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                    House3DScreen(state, { events += it }, {}, {}, onOpenMarker = { opened += it })
                }
            }
        }
        rule.onNodeWithTag("hm_3d_scene").assertIsDisplayed()
        rule.onNodeWithTag("hm_layer_electrical").performClick()
        assertThat(events).containsExactly(House3DEvent.ToggleLayer(BlueprintLayer.ELECTRICAL))
        val actions = rule.onNodeWithTag("hm_3d_scene").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertThat(actions.map { it.label }).containsExactly("Open Cold to sink", "Open Fridge")
        actions.forEach { it.action() }
        assertThat(opened.map { it.target }).containsExactly(MarkerTarget.OverlayMark("o1"), MarkerTarget.ItemMark("fridge"))
    }
}
