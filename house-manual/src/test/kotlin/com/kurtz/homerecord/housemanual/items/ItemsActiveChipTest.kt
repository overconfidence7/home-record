package com.kurtz.homerecord.housemanual.items

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.housemanual.ItemsFilteredPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [judge:hm-items-active-chip-offscreen]: the selected warranty chip is scrolled fully into view on a narrow phone (rule 11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemsActiveChipTest {
    @get:Rule val rule = createComposeRule()

    @Test fun theExpiredChipIsFullyOnScreenWhenItFilters() {
        // Robolectric measures text narrower than a phone, so a 250 dp row stands in for the device row that clipped Expired
        rule.setContent { Box(Modifier.width(250.dp)) { ItemsFilteredPreview() } }
        rule.waitForIdle()
        val chip = rule.onNodeWithTag("hm_items_filter_warranty_expired").fetchSemanticsNode()
        // boundsInRoot is clipped by the row; a chip past the edge shows narrower than it is
        assertThat(chip.boundsInRoot.width).isAtLeast(chip.size.width - 1f)
    }
}
