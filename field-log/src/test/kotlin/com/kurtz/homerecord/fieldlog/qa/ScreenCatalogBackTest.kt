package com.kurtz.homerecord.fieldlog.qa

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The JVM half of the crawler's first check (ScreenCrawlerTest): every non-root catalog entry shows
 * a back arrow or a close, so a catalog screen drawn without its ScreenScaffold fails here before
 * it fails on the emulator (AC-U12, ui-standards rule 1).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ScreenCatalogBackTest {
    @get:Rule val rule = createComposeRule()

    @Test fun everyNonRootCatalogScreenHasABackOrClose() {
        val nonRoot = ScreenCatalog.entries.filter { it.nonRoot }
        var index by mutableIntStateOf(0)
        rule.setContent { key(index) { nonRoot[index].content() } }
        val missing = mutableListOf<String>()
        for (i in nonRoot.indices) {
            index = i
            rule.waitForIdle()
            val found = listOf("fl_back", "fl_close").any { tag ->
                rule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            if (!found) missing += nonRoot[i].id
        }
        assertThat(missing).isEmpty()
    }
}
