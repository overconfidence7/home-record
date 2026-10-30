package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.ui.ItemEditScreen
import com.kurtz.homerecord.housemodel.ui.ItemEditUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Edit item keeps Close and Save while the item loads, so the navigation icon does not flip once it arrives (judge:hm-item-edit-loading-nav). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemEditLoadingTest {
    @get:Rule val rule = createComposeRule()

    @AcceptanceCriterion("AC-U6")
    @Test fun loadingShowsCloseAndDisabledSave() {
        var closed = 0
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { ItemEditScreen(ItemEditUiState(loading = true), {}, { closed++ }) } }
        assertThat(rule.onAllNodes(hasTestTag("hm_back")).fetchSemanticsNodes()).isEmpty()
        rule.onNodeWithTag("hm_save").assertIsNotEnabled()
        rule.onNodeWithTag("hm_close").performClick()
        assertThat(closed).isEqualTo(1)
    }
}
