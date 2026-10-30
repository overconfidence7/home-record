package com.kurtz.homerecord.housemanual

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.components.OptionsSheetContent
import com.kurtz.homerecord.coreui.components.SheetOption
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.ui.FloorsDialog
import com.kurtz.homerecord.housemodel.ui.FloorsEvent
import com.kurtz.homerecord.housemodel.ui.FloorsScreen
import com.kurtz.homerecord.housemodel.ui.FloorsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C3, AC-C14, CLAUDE.md QA rule 3 (cr:qa-text-selectors): H03 drives the Add floor dialog and the room menu by id,
// so the ids it uses must exist and do what the flow expects.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FlowTagsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun addFloorDialogFieldsAndSaveHaveIds() {
        val events = mutableListOf<FloorsEvent>()
        val state = FloorsUiState(
            loading = false,
            floors = listOf(FloorSummary("f1", "Main floor", 96, halfLevel = false, roomCount = 2)),
            dialog = FloorsDialog.Add,
        )
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { FloorsScreen(state, { events += it }, onBack = {}) }
            }
        }
        rule.onNodeWithTag("hm_floors_ceiling").assertIsDisplayed()
        rule.onNodeWithTag("hm_floors_cancel").assertIsDisplayed()
        rule.onNodeWithTag("hm_floors_name").performTextInput("Attic")
        rule.onNodeWithTag("hm_floors_save").performClick()
        assertThat(events).contains(FloorsEvent.ConfirmAdd("Attic"))
    }

    @Test fun sheetOptionsHaveIdsFromTheirOptionId() {
        var picked: SheetOption? = null
        val options = listOf(SheetOption("rename", "Rename"), SheetOption("shape", "Edit shape"), SheetOption("delete", "Delete"))
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { OptionsSheetContent("Kitchen", options) { picked = it } }
            }
        }
        rule.onNodeWithTag("hm_sheet_rename").assertIsDisplayed()
        rule.onNodeWithTag("hm_sheet_delete").performClick()
        assertThat(picked?.id).isEqualTo("delete")
    }
}
