package com.kurtz.homerecord.housemanual.equipment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.ui.theme.HomeRecordTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class EquipmentScreenTest {
    @get:Rule val rule = createComposeRule()

    private val today = LocalDate.of(2026, 10, 16)

    private fun item(id: String, installed: String?, warranty: String?) = Equipment(
        id, "h", null, EquipmentCategory.WATER_HEATER, "Rheem", "XE50", installedOn = installed, warrantyEnds = warranty,
        createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL,
    ).toItem(today)

    @Test fun `AC-42 empty state suggests the first three items`() {
        rule.setContent { HomeRecordTheme { EquipmentScreen(EquipmentUiState(loaded = true), onEvent = {}) } }
        rule.onNodeWithTag("equipment_empty").assertIsDisplayed()
        rule.onNodeWithText("water heater", substring = true).assertIsDisplayed()
        rule.onNodeWithText("furnace or air handler", substring = true).assertIsDisplayed()
        rule.onNodeWithText("AC or heat pump", substring = true).assertIsDisplayed()
    }

    @Test fun `AC-44 row shows age and warranty and opens on tap`() {
        val events = mutableListOf<EquipmentEvent>()
        val state = EquipmentUiState(true, listOf(item("a", "2020-01-01", "2027-02-03")))
        rule.setContent { HomeRecordTheme { EquipmentScreen(state, onEvent = { events += it }) } }
        rule.onNodeWithText("6 yrs").assertIsDisplayed()
        rule.onNodeWithText("Warranty until 2027-02-03").assertIsDisplayed()
        rule.onNodeWithText("Rheem XE50").performClick()
        assertEquals(listOf<EquipmentEvent>(EquipmentEvent.Open("a")), events)
    }

    @Test fun `AC-44 expired warranty`() {
        val state = EquipmentUiState(true, listOf(item("a", null, "2020-01-01")))
        rule.setContent { HomeRecordTheme { EquipmentScreen(state, onEvent = {}) } }
        rule.onNodeWithText("Warranty expired").assertIsDisplayed()
    }

    @Test fun `AC-43 save button follows category`() {
        var state by mutableStateOf(EquipmentEditUiState(loaded = true))
        rule.setContent { HomeRecordTheme { EquipmentEditScreen(state, null) {} } }
        rule.onNodeWithTag("equipment_save").assertIsNotEnabled()
        state = state.copy(category = EquipmentCategory.DRYER)
        rule.onNodeWithTag("equipment_save").assertIsEnabled()
    }
}
