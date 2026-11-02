package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.items.ItemDetailEvent
import com.kurtz.homerecord.housemanual.items.ItemLinksSection
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayDepth
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayLink
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.walls.WallStage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C26: the item page's Links lists the overlays linked to the item; one on a wall opens that wall.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemLinksSectionTest {
    @get:Rule val rule = createComposeRule()

    private fun overlay(id: String, kind: OverlayKind, label: String, wallId: String?) = Overlay(
        id, "h1", "r1", wallId, kind, label, null, OverlayDepth.BEHIND_DRYWALL, listOf(OverlayPoint(0.1f, 0.1f), OverlayPoint(0.5f, 0.1f)),
        WallStage.ROUGH_IN, null, "furnace", 0L,
    )

    @Test
    @AcceptanceCriterion("AC-C26")
    fun linkedOverlaysShowWithTheirRoomAndAWallOneOpensIt() {
        val gas = OverlayLink(overlay("o1", OverlayKind.GAS, "Furnace gas line", "w1"), "Utility room")
        val onPlan = OverlayLink(overlay("o2", OverlayKind.DUCT, "", null), "Utility room")
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { ItemLinksSection(listOf(gas, onPlan)) { events += it } } }
        rule.onNodeWithText("Links").assertExists()
        rule.onNodeWithText("Gas · Utility room · Rough-in stage").assertExists()
        rule.onNodeWithTag("hm_item_detail_overlay_o1").performClick()
        // An overlay on the plan has no wall to open yet.
        rule.onNodeWithTag("hm_item_detail_overlay_o2").performClick()
        assertThat(events).containsExactly(ItemDetailEvent.OpenOverlay(gas))
    }

    @Test
    @AcceptanceCriterion("AC-C26")
    fun nothingLinkedShowsNoSection() {
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { ItemLinksSection(emptyList()) {} } }
        rule.onNodeWithTag("hm_item_detail_links").assertDoesNotExist()
    }
}
