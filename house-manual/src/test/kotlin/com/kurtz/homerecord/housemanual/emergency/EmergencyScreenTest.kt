package com.kurtz.homerecord.housemanual.emergency

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.logic.EmergencyGrouper
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.ShutoffKind
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EmergencyScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun shutoff(id: String, kind: ShutoffKind, text: String) =
        Shutoff(id, "h", kind, null, null, text, 0, 0, Source.HOUSE_MANUAL)

    @Test fun `empty state shows guidance`() {
        rule.setContent { HomeRecordTheme { EmergencyScreen(EmergencyUiState(loaded = true), onEvent = {}) } }
        rule.onNodeWithTag("emergency_empty").assertIsDisplayed()
    }

    @Test fun `AC-29 renders groups and items`() {
        val state = EmergencyUiState(
            true,
            EmergencyGrouper.group(listOf(shutoff("a", ShutoffKind.GAS_MAIN, "Quarter turn"), shutoff("b", ShutoffKind.WATER_MAIN, "Turn clockwise"))),
        )
        rule.setContent { HomeRecordTheme { EmergencyScreen(state, onEvent = {}) } }
        rule.onAllNodesWithTag("emergency_group").assertCountEquals(2)
        rule.onAllNodesWithTag("emergency_item").assertCountEquals(2)
    }

    @Test fun `AC-30 instructions use at least 24sp`() {
        assertEquals(24f, EmergencyInstructionSize.value, 0f)
    }

    @Test fun `manage button raises event`() {
        val events = mutableListOf<EmergencyEvent>()
        rule.setContent { HomeRecordTheme { EmergencyScreen(EmergencyUiState(loaded = true), onEvent = { events += it }) } }
        rule.onNodeWithTag("emergency_manage").performClick()
        assertEquals(listOf<EmergencyEvent>(EmergencyEvent.ManageShutoffs), events)
    }
}
