package com.kurtz.homerecord.coreui.shell

import androidx.activity.ComponentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A tab's screen keeps one ViewModel while the tab is open, so its loaded state never restarts ([judge:hm-house-tab-spinner-flaky]). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TabViewModelLifetimeTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val created = mutableMapOf<String, Int>()
    private val cleared = mutableMapOf<String, Int>()

    private inner class CountingViewModel(val name: String) : ViewModel() {
        init { created[name] = (created[name] ?: 0) + 1 }
        override fun onCleared() { cleared[name] = (cleared[name] ?: 0) + 1 }
    }

    private fun setApp() = rule.setContent { app() }

    @androidx.compose.runtime.Composable
    private fun app() {
        val tabs = listOf(
            AppTab("a", "Alpha", Icons.Rounded.Home, Icons.Rounded.Home, PlaceholderKey("a")),
            AppTab("b", "Beta", Icons.Rounded.Settings, Icons.Rounded.Settings, PlaceholderKey("b")),
        )
        HomeRecordApp(tabs, entries = { nav ->
            entry<PlaceholderKey> { key ->
                val name = if (key.depth == 0) key.title else "${key.title}${key.depth}"
                val vm = viewModel<CountingViewModel>(key = "vm-$name", factory = viewModelFactory { initializer { CountingViewModel(name) } })
                Column {
                    Text("screen ${vm.name}")
                    var taps by rememberSaveable { mutableIntStateOf(0) }
                    Text("taps $name $taps", Modifier.clickable { taps++ })
                    if (key.depth == 0) Text("open ${key.title}", Modifier.clickable { nav.push(key.copy(depth = 1)) })
                    else Text("close ${key.title}", Modifier.clickable { nav.pop() })
                }
            }
        })
    }

    @Test fun switchingToATabCreatesItsViewModelOnce() {
        setApp()
        rule.onNodeWithText("Beta").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        rule.onNodeWithText("screen b").assertExists()
        assertEquals(1, created["b"])
        assertEquals(null, cleared["b"])
    }

    @Test fun returningToATabKeepsItsViewModel() {
        setApp()
        rule.onNodeWithText("Beta").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Alpha").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Beta").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertEquals(mapOf("a" to 1, "b" to 1), created.toMap())
        assertEquals(emptyMap<String, Int>(), cleared.toMap())
    }

    @Test fun aScreenLeavingItsTabsStackIsCleared() {
        setApp()
        rule.onNodeWithText("open a").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Beta").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Alpha").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("screen a1").assertExists()
        assertEquals(1, created["a1"])
        rule.onNodeWithText("close a").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.waitForIdle()
        assertEquals(1, cleared["a1"])
        assertEquals(null, cleared["a"])
    }

    // S5: process death keeps a hidden tab's open screen and the field state it saved (per-tab saveable holders).
    @Test fun hiddenTabsScreenAndSavedStateSurviveProcessDeath() {
        val restore = StateRestorationTester(rule)
        restore.setContent { app() }
        rule.onNodeWithText("Beta").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("open b").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        repeat(3) { rule.onNodeWithText("taps b1", substring = true).performClick() }
        rule.onNodeWithText("taps b1 3").assertExists()
        rule.onNodeWithText("Alpha").performClick()
        rule.waitForIdle()
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Beta").performClick()
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(2_000)
        rule.onNodeWithText("taps b1 3").assertExists()
    }
}
