package com.kurtz.homerecord.housemanual.rules

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.kurtz.homerecord.coreui.components.*
import com.kurtz.homerecord.housemanual.house.*
import com.kurtz.homerecord.housemodel.ui.*
import com.kurtz.homerecord.housemodel.FloorSummary
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListAndFormRulesTest {
    @get:Rule val rule = createComposeRule()

    // AC-U3
    @Test fun recordListItemShowsLeadingHeadlineSupportingAndChevron() {
        var clicked = false
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                RecordListItem(
                    headline = "Water heater",
                    supporting = "Basement utility room",
                    leading = { Icon(Icons.Rounded.Bolt, contentDescription = "Appliance icon") },
                    onClick = { clicked = true },
                )
            }
        }
        rule.onNodeWithText("Water heater").assertIsDisplayed()
        rule.onNodeWithText("Basement utility room").assertIsDisplayed()
        rule.onNodeWithContentDescription("Appliance icon").assertIsDisplayed()
        rule.onNodeWithText("Water heater").performClick()
        assertThat(clicked).isTrue()
    }

    // AC-U6
    @Test fun formHasCloseAndSaveAndCleanCloseSkipsDiscardDialog() {
        var closed = false
        var saved = false
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                FormScaffold("New item", dirty = false, saveEnabled = true, onClose = { closed = true }, onSave = { saved = true }) {}
            }
        }
        rule.onNodeWithText("Save").performClick()
        assertThat(saved).isTrue()
        rule.onNodeWithContentDescription("Close").performClick()
        assertThat(closed).isTrue()
    }

    // AC-U6
    @Test fun dirtyFormConfirmsDiscardInAlertDialog() {
        var closed = false
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                FormScaffold("New item", dirty = true, saveEnabled = true, onClose = { closed = true }, onSave = {}) {}
            }
        }
        rule.onNodeWithContentDescription("Close").performClick()
        rule.onNodeWithText("Discard changes?").assertIsDisplayed()
        assertThat(closed).isFalse()
    }

    // AC-U7
    @Test fun linkChipsNavigateOnTap() {
        var opened: LinkChip? = null
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                LinkChipRow(listOf(LinkChip("r1", "Kitchen"), LinkChip("b4", "Breaker 4")), onOpen = { opened = it })
            }
        }
        rule.onNodeWithText("Kitchen").assertIsDisplayed()
        rule.onNodeWithText("Breaker 4").performClick()
        assertThat(opened?.id).isEqualTo("b4")
    }

    // AC-U5
    @Test fun floorsScreenHasOneFabThatFiresAddClicked() {
        val events = mutableListOf<FloorsEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                FloorsScreen(
                    FloorsUiState(
                        loading = false,
                        floors = listOf(
                            FloorSummary("f0", "Basement", 96, false, 3),
                            FloorSummary("f1", "Main", 108, false, 7),
                        ),
                    ),
                    onEvent = { events += it },
                    onBack = {},
                )
            }
        }
        assertThat(rule.onAllNodesWithTag("hm_floors_fab").fetchSemanticsNodes()).hasSize(1)
        rule.onNodeWithTag("hm_floors_fab").assertIsDisplayed().performClick()
        assertThat(events).contains(FloorsEvent.AddClicked)
    }
}
