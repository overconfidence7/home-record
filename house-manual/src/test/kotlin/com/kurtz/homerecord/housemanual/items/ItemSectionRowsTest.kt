package com.kurtz.homerecord.housemanual.items

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ConsumableEntity
import com.kurtz.homerecord.core.data.DocumentEntity
import com.kurtz.homerecord.core.data.ServiceRecordEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The item page's sections are headers over list rows, not cards (cr:card-lists): the row opens, trailing buttons act. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemSectionRowsTest {
    @get:Rule val rule = createComposeRule()

    private val filter = ConsumableEntity("c1", "i1", "FILTER", "16x25x1 MERV 8", 2, "https://example.com/filters", 1, 1)
    private val battery = ConsumableEntity("c2", "i1", "BATTERY", "9V thermostat battery", 1, null, 1, 1)

    @AcceptanceCriterion("AC-U3", "AC-C35")
    @Test fun consumableRowEditsOnTapAndReordersFromItsButton() {
        val events = mutableListOf<ItemDetailEvent>()
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { ConsumablesSection(listOf(filter, battery)) { events += it } } }
        val rows = rule.onAllNodesWithTag("hm_item_detail_consumable_edit")
        assertThat(rows.fetchSemanticsNodes()).hasSize(2)
        rows[1].performClick()
        rule.onNodeWithContentDescription("Reorder 16x25x1 MERV 8").performClick()
        assertThat(events).containsExactly(
            ItemDetailEvent.OpenConsumable("c2"),
            ItemDetailEvent.OpenLink("https://example.com/filters"),
        ).inOrder()
        // Only the filter has a reorder link
        assertThat(rule.onAllNodesWithTag("hm_item_detail_consumable_reorder").fetchSemanticsNodes()).hasSize(1)
        // The section title is a heading for TalkBack (rule 9)
        rule.onAllNodes(hasText("Consumables").and(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))).assertCountEquals(1)
    }

    @AcceptanceCriterion("AC-U3", "AC-C36")
    @Test fun serviceRowOpensTheRecord() {
        val events = mutableListOf<ItemDetailEvent>()
        val tuneUp = ServiceRecordEntity("s1", "i1", "2026-03-14", null, "Annual tune-up and igniter check", 14_900, 1, 1)
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { ServiceSection(listOf(tuneUp), emptyList()) { events += it } } }
        rule.onAllNodesWithTag("hm_item_detail_service_edit")[0].performClick()
        assertThat(events).containsExactly(ItemDetailEvent.OpenService("s1"))
    }

    @AcceptanceCriterion("AC-U3", "AC-C37")
    @Test fun documentRowOpensTheViewer() {
        val events = mutableListOf<ItemDetailEvent>()
        val manual = DocumentEntity(id = "d1", homeId = "h1", folder = "Appliances", name = "Furnace manual.pdf", path = "p1", mime = "application/pdf", size = 2_411_520, createdAt = 1, updatedAt = 1)
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { DocumentsSection(ItemDetailUiState(loading = false, documents = listOf(manual))) { events += it } }
        }
        rule.onAllNodesWithTag("hm_item_detail_document_row")[0].performClick()
        assertThat(events).containsExactly(ItemDetailEvent.OpenDocument("d1"))
    }
}
