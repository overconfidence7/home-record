package com.kurtz.homerecord.coreui.shell

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Serializable internal data object SharedRootKey : NavKey
@Serializable internal data object SharedDetailKey : NavKey

// AC-C22: "View in 3D" on a room page opens the room view with a shared-element transition.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SharedBoundsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Composable
    private fun app() {
        val key = SharedKeys.room3D("kitchen")
        HomeRecordApp(
            listOf(AppTab("a", "Alpha", Icons.Rounded.Home, Icons.Rounded.Home, SharedRootKey)),
            entries = { nav ->
                entry<SharedRootKey> {
                    Box(Modifier.fillMaxSize()) {
                        Button(onClick = { nav.push(SharedDetailKey) }, modifier = Modifier.sharedScreenBounds(key).size(120.dp, 48.dp).testTag("open")) {
                            Text("View in 3D")
                        }
                    }
                }
                entry<SharedDetailKey> {
                    Box(Modifier.fillMaxSize().sharedScreenBounds(key).testTag("scene"))
                }
            },
        )
    }

    @Test fun outsideTheShellTheModifierChangesNothing() {
        var scope: Any? = Unit
        rule.setContent {
            scope = LocalSharedTransitionScope.current
            Box(Modifier.sharedScreenBounds(SharedKeys.room3D("kitchen")).size(120.dp, 48.dp).testTag("open"))
        }
        assertNull(scope)
        rule.onNodeWithTag("open").assertIsDisplayed()
    }

    @Test fun roomViewGrowsOutOfTheButtonThenFillsItsScreen() {
        rule.setContent { app() }
        rule.waitForIdle()
        val full = rule.onRoot().getUnclippedBoundsInRoot()

        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("open").performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(EARLY_MS)
        val mid = rule.onNodeWithTag("scene", useUnmergedTree = true).getUnclippedBoundsInRoot()

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val end = rule.onNodeWithTag("scene").getUnclippedBoundsInRoot()
        // Early on, the scene is between the 120 x 48 dp button and the size it settles at.
        assertTrue("mid width ${mid.width} should exceed the 120 dp button", mid.width > 120.dp)
        assertTrue("mid width ${mid.width} should be under the settled ${end.width}", mid.width < end.width - 8.dp)
        assertTrue("mid height ${mid.height} should be under the settled ${end.height}", mid.height < end.height - 8.dp)
        assertTrue("settled height ${end.height} should be well over the 48 dp button", end.height > full.height / 2)
    }

    @Test fun backShrinksTheRoomViewIntoTheButton() {
        rule.setContent { app() }
        rule.onNodeWithTag("open").performClick()
        rule.onNodeWithTag("scene").assertIsDisplayed()
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("open").assertIsDisplayed()
        rule.onNodeWithTag("scene").assertDoesNotExist()
    }

    private companion object {
        /** Early in the bounds spring (it settles in about 350 ms). */
        const val EARLY_MS = 100L
    }
}
