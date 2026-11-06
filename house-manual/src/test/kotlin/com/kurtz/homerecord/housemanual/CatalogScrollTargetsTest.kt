package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.housemanual.qa.QaScreen
import com.kurtz.homerecord.housemanual.qa.ScreenCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// [judge:qa-crawler-no-scroll]: every catalog entry that names a scroll target really shows it, so the crawler's
// shot of that entry is a picture of what the entry exists for, not the top of the item page again.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CatalogScrollTargetsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyScrollTargetIsOnItsScreen() {
        val targets = ScreenCatalog.entries.filter { it.scrollTo != null }
        assertThat(targets.map { it.id }).containsAtLeast("item-detail-recalls", "item-detail-upkeep", "item-detail-lookup-failed", "item-detail-review-badge")
        val current = mutableStateOf<QaScreen?>(null)
        rule.setContent { current.value?.content?.invoke() }
        for (entry in targets) {
            current.value = entry
            rule.waitForIdle()
            rule.onAllNodesWithTag(requireNotNull(entry.scrollTo), useUnmergedTree = true)[0].performScrollTo().assertIsDisplayed()
        }
    }
}
