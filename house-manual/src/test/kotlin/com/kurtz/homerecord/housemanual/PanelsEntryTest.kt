package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.more.MoreEvent
import com.kurtz.homerecord.housemanual.more.MoreScreen
import com.kurtz.homerecord.housemanual.panel.PanelsEntry
import com.kurtz.homerecord.housemanual.panel.PanelsEvent
import com.kurtz.homerecord.housemanual.panel.PanelsScreen
import com.kurtz.homerecord.housemanual.panel.PanelsUiState
import com.kurtz.homerecord.housemodel.items.ItemRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C40: More → Breaker panels opens the one panel straight, or lists the home's panels, or offers to add one.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PanelsEntryTest {
    @get:Rule val rule = createComposeRule()

    private fun row(id: String, sub: String, name: String, at: Long) =
        ItemRow(id, name, null, null, ItemCategory.ELECTRICAL, sub, null, "", WarrantyStatus.Unknown, createdAt = at)

    private val furnace = row("f", "furnace", "Furnace", 0)
    private val main = row("m", "breaker_panel", "Main panel", 1)
    private val sub = row("s", "sub_panel", "Garage sub-panel", 2)

    @Test
    @AcceptanceCriterion("AC-C40")
    fun onePanelOpensStraightOtherwiseTheList() {
        assertThat(PanelsEntry.destination(listOf(furnace, main))).isEqualTo(PanelKey("m"))
        assertThat(PanelsEntry.destination(listOf(sub, furnace, main))).isEqualTo(PanelsKey)
        assertThat(PanelsEntry.destination(listOf(furnace))).isEqualTo(PanelsKey)
        assertThat(PanelsEntry.panels(listOf(sub, furnace, main)).map { it.id }).containsExactly("m", "s").inOrder()
    }

    @Test
    fun theListOpensAPanelAndTheEmptyStateAddsOne() {
        val events = mutableListOf<PanelsEvent>()
        var state by androidx.compose.runtime.mutableStateOf(PanelsUiState(loading = false, panels = listOf(main, sub)))
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { PanelsScreen(state) { events += it } } }
        rule.onNodeWithTag("hm_panels_row_1").assertExists()
        rule.onNodeWithTag("hm_panels_row_2").assertExists()
        rule.onNodeWithTag("hm_panels_row_3").assertDoesNotExist()
        rule.onNodeWithText("Garage sub-panel").performClick()
        state = PanelsUiState(loading = false)
        rule.onNodeWithText("No breaker panel yet").assertExists()
        rule.onNodeWithTag("hm_panels_add").performClick()
        assertThat(events).containsExactly(PanelsEvent.Open("s"), PanelsEvent.Add).inOrder()
    }

    @Test
    fun moreHasABreakerPanelsRow() {
        val events = mutableListOf<MoreEvent>()
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { MoreScreen({ events += it }) } }
        rule.onNodeWithText("Breaker panels").assertExists()
        rule.onNodeWithTag("hm_more_panels").performClick()
        assertThat(events).containsExactly(MoreEvent.OpenPanels)
    }
}
