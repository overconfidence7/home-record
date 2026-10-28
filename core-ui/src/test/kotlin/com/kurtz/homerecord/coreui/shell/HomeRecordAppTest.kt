package com.kurtz.homerecord.coreui.shell

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeRecordAppTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun setApp(requests: TabRequests? = null) = rule.setContent { app(requests) }

    @androidx.compose.runtime.Composable
    private fun app(requests: TabRequests? = null) {
        val tabs = listOf(
            AppTab("a", "Alpha", Icons.Rounded.Home, Icons.Rounded.Home, PlaceholderKey("a")),
            AppTab("b", "Beta", Icons.Rounded.Settings, Icons.Rounded.Settings, PlaceholderKey("b")),
        )
        HomeRecordApp(tabs, entries = { nav ->
            entry<PlaceholderKey> { key ->
                if (key.depth == 0) PlaceholderRoot(key.title, { nav.push(key.copy(depth = 1)) })
                else PlaceholderDetail("Detail ${key.title}", nav::pop, {})
            }
        }, tabRequests = requests?.flow)
    }

    @Test fun tabsShowIconAndLabelAndSwitch() {
        setApp()
        rule.onNodeWithText("Alpha").assertIsDisplayed()
        rule.onNodeWithText("Beta").performClick()
        rule.onNodeWithText("b", substring = false).assertIsDisplayed()
    }

    @Test fun detailHasBackArrowAndBackReturns() {
        setApp()
        rule.onNodeWithText("Open a detail screen").performClick()
        rule.onAllNodesWithText("Detail a").onFirst().assertIsDisplayed()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Open a detail screen").assertIsDisplayed()
    }

    // AC-U12
    @Test fun tabsAndBackArrowCarryTestTags() {
        setApp()
        rule.onNodeWithTag("app_tab_a").assertIsDisplayed()
        rule.onNodeWithTag("app_tab_b").assertIsDisplayed()
        rule.onNodeWithText("Open a detail screen").performClick()
        rule.onNodeWithTag("app_back").assertIsDisplayed()
    }

    @Test fun tabKeepsItsOwnStackAndReselectPopsToRoot() {
        setApp()
        rule.onNodeWithText("Open a detail screen").performClick()
        rule.onNodeWithText("Beta").performClick()
        rule.onNodeWithText("Alpha").performClick()
        rule.onAllNodesWithText("Detail a").onFirst().assertIsDisplayed()
        rule.onNodeWithText("Alpha").performClick()
        rule.onNodeWithText("Open a detail screen").assertIsDisplayed()
    }

    // cr:fl-tab-replay: a widget request opens its tab once; the person's later choice survives rotation.
    @Test fun tabRequestOpensTabOnceAndRotationKeepsTheChosenTab() {
        val requests = TabRequests()
        requests.request("b")
        val restore = StateRestorationTester(rule)
        restore.setContent { app(requests) }
        rule.onNodeWithText("b", substring = false).assertIsDisplayed()
        rule.onNodeWithText("Alpha").performClick()
        rule.onNodeWithText("a", substring = false).assertIsDisplayed()
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("a", substring = false).assertIsDisplayed()
        // A new request still switches tabs.
        requests.request("b")
        rule.onNodeWithText("b", substring = false).assertIsDisplayed()
    }
}
