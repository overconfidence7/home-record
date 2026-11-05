package com.kurtz.homerecord.fieldlog.house

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.core.model.WarrantyStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.items.ItemDetail
import com.kurtz.homerecord.housemodel.ui.RoomItem
import com.kurtz.homerecord.housemodel.ui.RoomEvent
import com.kurtz.homerecord.housemodel.ui.RoomScreen
import com.kurtz.homerecord.housemodel.ui.RoomUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class JobItemScreenTest {
    @get:Rule val rule = createComposeRule()

    private val heater = ItemDetail(
        item = ItemEntity(
            id = "it1", homeId = "h1", roomId = "r1", category = "WATER", subtype = "water_heater", name = "Water heater",
            make = "Rheem", model = "XE50M06ST45U1", serial = "Q051826153", inServiceOn = "2026-10-21", createdAt = 1, updatedAt = 1,
        ),
        category = ItemCategory.WATER, subtypeLabel = "Water heater", roomName = "Utility room", warranty = null,
        status = WarrantyStatus.Unknown, photos = emptyList(),
    )

    private fun show(state: JobItemUiState): MutableList<JobItemEvent> {
        val events = mutableListOf<JobItemEvent>()
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { JobItemScreen(state, { events += it }) } }
        return events
    }

    @AcceptanceCriterion("AC-F50")
    @Test fun aLeadSeesMakeModelSerialAndCanLookUpEditPlaceAndDelete() {
        val events = show(JobItemUiState(loading = false, detail = heater, canEdit = true))
        rule.onNodeWithText("Q051826153").assertExists()
        rule.onNodeWithText("XE50M06ST45U1").assertExists()
        rule.onNodeWithTag("fl_jobitem_edit").performClick()
        rule.onNodeWithTag("fl_jobitem_place").performClick()
        rule.onNode(hasTestTag("fl_jobitem_warranty"), true).assertExists()
        rule.onNodeWithTag("fl_jobitem_lookup", useUnmergedTree = true).performClick()
        rule.onNodeWithTag("fl_jobitem_delete").performClick()
        assertThat(events).containsExactly(JobItemEvent.Edit, JobItemEvent.Place, JobItemEvent.LookUp(again = false), JobItemEvent.AskDelete).inOrder()
    }

    @Test fun crewReadTheItemWithoutEditLookupOrDelete() {
        show(JobItemUiState(loading = false, detail = heater, canEdit = false))
        rule.onNodeWithText("Rheem").assertIsDisplayed()
        rule.onNodeWithTag("fl_jobitem_edit").assertDoesNotExist()
        rule.onNodeWithTag("fl_jobitem_delete").assertDoesNotExist()
        rule.onNodeWithTag("fl_jobitem_lookup", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("Not on the plan yet.").assertExists()
    }

    @Test fun deleteAsksFirst() {
        val events = show(JobItemUiState(loading = false, detail = heater, canEdit = true, confirmDelete = true))
        rule.onNodeWithText("Delete Water heater?").assertExists()
        rule.onNodeWithTag("fl_jobitem_delete_cancel").performClick()
        assertThat(events).containsExactly(JobItemEvent.CancelDelete)
    }

    @AcceptanceCriterion("AC-F50")
    @Test fun theJobsRoomListsItsItemsAndOffersAddOnlyWhenAllowed() {
        val events = mutableListOf<RoomEvent>()
        var canAdd by androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                CompositionLocalProvider(LocalTagPrefix provides "fl") {
                    RoomScreen(
                        RoomUiState(
                            loading = false, canAddItem = canAdd,
                            room = com.kurtz.homerecord.housemodel.plan.PlanRoom.rectangle("r1", "Utility room", "UTILITY", 0, 0, 96, 120),
                            items = listOf(RoomItem("it1", "Water heater", "Rheem XE50M06ST45U1", "WaterDrop")),
                        ),
                    ) { events += it }
                }
            }
        }
        rule.onNodeWithText("Rheem XE50M06ST45U1").performClick()
        rule.onNodeWithTag("fl_room_add_item").performClick()
        assertThat(events).containsExactly(RoomEvent.OpenItem("it1"), RoomEvent.AddItem).inOrder()
        canAdd = false
        rule.waitForIdle()
        rule.onNodeWithTag("fl_room_add_item").assertDoesNotExist()
    }
}
