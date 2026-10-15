package com.kurtz.homerecord.housemanual.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `AC-28 emergency card is one tap from the hub and cards show live counts`() {
        val events = mutableListOf<HomeEvent>()
        rule.setContent {
            HomeRecordTheme {
                HomeScreen(
                    HomeUiState(homeName = "Maple", hasPanel = true, panelSlotCount = 30, percentLabeled = 40, shutoffCount = 3, equipmentCount = 1, roomCount = 8),
                    onEvent = { events += it },
                )
            }
        }
        rule.onNodeWithText("30 slots · 40% labeled").assertIsDisplayed()
        rule.onNodeWithText("3 shutoffs").assertIsDisplayed()
        rule.onNodeWithText("1 item").assertIsDisplayed()
        rule.onNodeWithText("8 rooms").assertIsDisplayed()
        rule.onNodeWithTag("card_emergency").performClick()
        assertEquals(listOf<HomeEvent>(HomeEvent.OpenEmergency), events)
    }
}
