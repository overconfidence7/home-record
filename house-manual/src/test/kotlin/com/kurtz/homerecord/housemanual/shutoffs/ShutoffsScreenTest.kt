package com.kurtz.homerecord.housemanual.shutoffs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class ShutoffsScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun shutoff(id: String, kind: ShutoffKind, text: String) =
        Shutoff(id, "h", kind, null, null, text, 0, 0, Source.HOUSE_MANUAL)

    @Test fun `AC-25 empty state explains the three mains`() {
        rule.setContent { HomeRecordTheme { ShutoffsScreen(ShutoffsUiState(loaded = true), onEvent = {}) } }
        rule.onNodeWithTag("shutoffs_empty").assertIsDisplayed()
        rule.onNodeWithText("Add your three mains first", substring = true).assertIsDisplayed()
    }

    @Test fun `rows are grouped by kind with mains first and open on tap`() {
        val events = mutableListOf<ShutoffsEvent>()
        val state = ShutoffsUiState(
            true,
            listOf(shutoff("a", ShutoffKind.SUMP, "Unplug pump"), shutoff("b", ShutoffKind.WATER_MAIN, "Quarter turn")),
        )
        assertEquals(listOf(ShutoffKind.WATER_MAIN, ShutoffKind.SUMP), state.groups.map { it.first })
        rule.setContent { HomeRecordTheme { ShutoffsScreen(state, onEvent = { events += it }) } }
        rule.onNodeWithText("Quarter turn").performClick()
        assertEquals(listOf<ShutoffsEvent>(ShutoffsEvent.Open("b")), events)
    }

    @Test fun `AC-26 save button follows validity`() {
        var state = ShutoffEditUiState(loaded = true, kind = ShutoffKind.WATER_MAIN)
        rule.setContent { HomeRecordTheme { ShutoffEditScreen(state, null, {}) } }
        rule.onNodeWithTag("shutoff_save").assertIsNotEnabled()
    }

    @Test fun `AC-26 save enabled with kind and instructions`() {
        val state = ShutoffEditUiState(loaded = true, kind = ShutoffKind.GAS_MAIN, instructions = "Quarter turn")
        rule.setContent { HomeRecordTheme { ShutoffEditScreen(state, null, {}) } }
        rule.onNodeWithTag("shutoff_save").assertIsEnabled()
    }
}
