package com.kurtz.homerecord.housemanual.qa

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.shell.ShellFrame
import com.kurtz.homerecord.housemanual.shellTabs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Root catalog entries are shown inside the app's bar with their tab selected ([judge:qa-crawler-no-root-bar], AC-U1). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CatalogRootTabTest {
    @get:Rule val rule = createComposeRule()

    @Test fun rootEntriesNameTheirTabAndOthersNone() {
        val byId = ScreenCatalog.entries.associateBy { it.id }
        assertThat(byId.getValue("home-full").tab).isEqualTo("home")
        assertThat(byId.getValue("items-loading").tab).isEqualTo("items")
        assertThat(byId.getValue("item-move-room").tab).isNull()
        assertThat(byId.getValue("onboarding-empty").tab).isNull()
        assertThat(ScreenCatalog.entries.filter { it.nonRoot }.mapNotNull { it.tab }).isEmpty()
    }

    @AcceptanceCriterion("AC-U1")
    @Test fun shellFrameDrawsTheBarWithTheTabSelected() {
        val tabIds = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                val tabs = shellTabs()
                tabIds += tabs.map { it.id }
                ShellFrame(tabs, "home") { Text("Screen body") }
            }
        }
        rule.onNodeWithText("Screen body").assertExists()
        rule.onNodeWithTag("hm_tab_home").assertIsSelected()
        assertThat(ScreenCatalog.entries.mapNotNull { it.tab }.toSet()).isNotEmpty()
        assertThat(tabIds).containsAtLeastElementsIn(ScreenCatalog.entries.mapNotNull { it.tab }.toSet())
    }
}
