package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.SearchKind
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.home.HomeEvent
import com.kurtz.homerecord.housemanual.home.HomeScreen
import com.kurtz.homerecord.housemanual.home.HomeUiState
import com.kurtz.homerecord.housemanual.home.SearchResultRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-H3: a breaker hit reads "Breaker 9 · Main panel · 20 A" (no kind word) and opens its panel item at slot 9.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HomeSearchBreakerTest {
    @get:Rule val rule = createComposeRule()

    @AcceptanceCriterion("AC-H3")
    @Test fun aBreakerHitOpensItsPanelAtTheSlot() {
        val events = mutableListOf<HomeEvent>()
        val row = SearchResultRow(SearchKind.BREAKER, "b9", "Garage outlets", "Breaker 9 · Main panel · 20 A", openId = "panel-item", slot = 9)
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                HomeScreen(HomeUiState(loading = false, homeName = "Maple St", itemCount = 4, query = "garage", results = listOf(row)), { events += it })
            }
        }
        rule.onNodeWithText("Breaker 9 · Main panel · 20 A").assertIsDisplayed()
        rule.onNodeWithTag("hm_home_result_b9").performClick()
        assertThat(events).containsExactly(HomeEvent.OpenResult(SearchKind.BREAKER, "panel-item", 9))
    }
}
