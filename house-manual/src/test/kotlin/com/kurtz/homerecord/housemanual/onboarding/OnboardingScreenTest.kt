package com.kurtz.homerecord.housemanual.onboarding

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OnboardingScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `continue enables only after a non-space name is typed`() {
        rule.setContent {
            var state by remember { mutableStateOf(OnboardingUiState()) }
            HomeRecordTheme {
                OnboardingScreen(state) { e ->
                    if (e is OnboardingEvent.HomeNameChanged) state = state.copy(homeName = e.value)
                }
            }
        }
        rule.onNodeWithText("Continue").assertIsNotEnabled()
        rule.onNodeWithText("Home name").performTextInput("   ")
        rule.onNodeWithText("Continue").assertIsNotEnabled()
        rule.onNodeWithText("Home name").performTextInput("Maple")
        rule.onNodeWithText("Continue").assertIsEnabled()
    }
}
