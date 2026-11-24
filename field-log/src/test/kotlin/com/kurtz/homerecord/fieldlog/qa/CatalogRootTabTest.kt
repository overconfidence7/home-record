package com.kurtz.homerecord.fieldlog.qa

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
import com.kurtz.homerecord.fieldlog.shellTabs
import com.kurtz.homerecord.fieldlog.jobs.JobTab
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
        assertThat(byId.getValue("today-admin").tab).isEqualTo("today")
        assertThat(byId.getValue("clock-working").tab).isEqualTo("clock")
        assertThat(byId.getValue("plan-lapsed").tab).isNull()
        assertThat(byId.getValue("welcome-create").tab).isNull()
        assertThat(ScreenCatalog.entries.filter { it.nonRoot }.mapNotNull { it.tab }).isEmpty()
    }

    @AcceptanceCriterion("AC-U1")
    @Test fun shellFrameDrawsTheBarWithTheTabSelected() {
        val tabIds = mutableListOf<String>()
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "fl") {
                val tabs = shellTabs()
                tabIds += tabs.map { it.id }
                ShellFrame(tabs, "today") { Text("Screen body") }
            }
        }
        rule.onNodeWithText("Screen body").assertExists()
        rule.onNodeWithTag("fl_tab_today").assertIsSelected()
        assertThat(ScreenCatalog.entries.mapNotNull { it.tab }.toSet()).isNotEmpty()
        assertThat(tabIds).containsAtLeastElementsIn(ScreenCatalog.entries.mapNotNull { it.tab }.toSet())
    }

    @Test fun jobTabEntriesAreDrawnUnderTheJobBar() {
        val byId = ScreenCatalog.entries.associateBy { it.id }
        assertThat(byId.getValue("photos-tab").jobTab).isEqualTo(JobTab.PHOTOS)
        assertThat(byId.getValue("files-tab").jobTab).isEqualTo(JobTab.FILES)
        assertThat(byId.getValue("log-delay-sheet").jobTab).isEqualTo(JobTab.LOG)
        assertThat(byId.getValue("log-weather-form").jobTab).isNull()
        rule.setContent { CompositionLocalProvider(LocalTagPrefix provides "fl") { CatalogJobFrame(JobTab.FILES) { Text("Files body") } } }
        rule.onNodeWithText("Files body").assertExists()
        rule.onNodeWithTag("fl_back").assertExists()
        rule.onNodeWithTag("fl_job_tab_files").assertIsSelected()
        rule.onNodeWithText("Hale kitchen").assertExists()
    }
}
