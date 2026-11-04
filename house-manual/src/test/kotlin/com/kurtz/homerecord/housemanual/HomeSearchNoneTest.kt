package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.home.HomeEvent
import com.kurtz.homerecord.housemanual.home.HomeScreen
import com.kurtz.homerecord.housemanual.home.HomeUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ui-standards rule 11 ([judge:hm-search-none-no-action]): Home search with no results offers Clear search and Add item.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HomeSearchNoneTest {
    @get:Rule val rule = createComposeRule()

    @Test fun noResultsOffersToClearTheSearchOrAddAnItem() {
        val events = mutableListOf<HomeEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                HomeScreen(HomeUiState(loading = false, homeName = "Maple St", itemCount = 4, query = "zzz"), { events += it })
            }
        }
        rule.onNodeWithText("Nothing matches \"zzz\".").assertIsDisplayed()
        rule.onNodeWithText("Check the spelling, or add it as an item.").assertIsDisplayed()
        rule.onNodeWithTag("hm_home_search_none_clear").performClick()
        rule.onNodeWithTag("hm_home_search_none_add").performClick()
        assertThat(events).containsExactly(HomeEvent.QueryChanged(""), HomeEvent.AddItem).inOrder()
    }
}
