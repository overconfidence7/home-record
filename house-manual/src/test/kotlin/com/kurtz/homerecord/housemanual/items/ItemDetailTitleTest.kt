package com.kurtz.homerecord.housemanual.items

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Rule 2: the item page's bar stays blank while loading and says "Item not found" only once loading is over (judge:hm-item-loading-title-not-found). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemDetailTitleTest {
    @get:Rule val rule = createComposeRule()

    @AcceptanceCriterion("AC-U2", "AC-H7")
    @Test fun loadingShowsNoNotFoundTitle() {
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { ItemDetailScreen(ItemDetailUiState(loading = true), {}) } }
        rule.onAllNodesWithText("Item not found").assertCountEquals(0)
    }

    @AcceptanceCriterion("AC-U2", "AC-H7")
    @Test fun missingItemSaysNotFoundOnceLoaded() {
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { ItemDetailScreen(ItemDetailUiState(loading = false), {}) } }
        // Top bar and the empty-state headline
        rule.onAllNodesWithText("Item not found").assertCountEquals(2)
    }
}
