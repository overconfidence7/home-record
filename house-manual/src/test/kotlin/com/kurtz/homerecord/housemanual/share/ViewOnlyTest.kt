package com.kurtz.homerecord.housemanual.share

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.housemanual.home.HomeScreen
import com.kurtz.homerecord.housemanual.home.HomeUiState
import com.kurtz.homerecord.housemanual.items.ItemsScreen
import com.kurtz.homerecord.housemanual.items.ItemsUiState
import com.kurtz.homerecord.housemodel.plan.Pt
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.ui.HouseScreen
import com.kurtz.homerecord.housemodel.ui.HouseUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// T076c: AC-H16 "a viewer cannot edit (edit controls hidden, not just disabled)" on Home, Items and the House tab.
@AcceptanceCriterion("AC-H16")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ViewOnlyTest {
    @get:Rule val rule = createComposeRule()

    @Test fun homeHidesTheAddActionsAndSaysViewOnly() {
        rule.setContent { HomeScreen(HomeUiState(loading = false, homeName = "Maple St", viewOnly = true), {}) }
        rule.onNodeWithTag("hm_home_view_only").assertIsDisplayed()
        rule.onNodeWithTag("hm_home_add_item").assertDoesNotExist()
        rule.onNodeWithTag("hm_home_scan_plate").assertDoesNotExist()
        rule.onNodeWithTag("hm_home_emergency_add").assertDoesNotExist()
    }

    @Test fun homeSearchWithNoMatchOffersNoAdd() {
        rule.setContent { HomeScreen(HomeUiState(loading = false, homeName = "Maple St", itemCount = 4, query = "zzz", viewOnly = true), {}) }
        rule.onNodeWithTag("hm_home_search_none_clear").assertIsDisplayed()
        rule.onNodeWithTag("hm_home_search_none_add").assertDoesNotExist()
    }

    @Test fun anOwnerStillSeesTheAddActions() {
        rule.setContent { HomeScreen(HomeUiState(loading = false, homeName = "Maple St"), {}) }
        rule.onNodeWithTag("hm_home_view_only").assertDoesNotExist()
        rule.onNodeWithTag("hm_home_add_item").assertIsDisplayed()
        rule.onNodeWithTag("hm_home_emergency_add").assertExists()
    }

    @Test fun itemsHaveNoAddButton() {
        rule.setContent { ItemsScreen(ItemsUiState(loading = false, viewOnly = true), {}) }
        rule.onNodeWithTag("hm_items_fab").assertDoesNotExist()
        rule.onNodeWithTag("hm_items_add").assertDoesNotExist()
    }

    @Test fun theHouseTabHasNoEditPlanOrFloors() {
        val kitchen = PlanRoom("r1", "Kitchen", points = listOf(Pt(0, 0), Pt(144, 0), Pt(144, 120), Pt(0, 120)))
        val state = HouseUiState(loading = false, homeName = "Maple St", floorId = "f1", rooms = listOf(kitchen))
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                HouseScreen(state, {}, {}, {}, {}, {}, on3D = {}, canEdit = false)
            }
        }
        rule.onNodeWithTag("hm_house_3d").assertExists()
        rule.onNodeWithTag("hm_house_edit_plan").assertDoesNotExist()
        rule.onNodeWithTag("hm_house_floors").assertDoesNotExist()
    }

    @Test fun emergencyAndPanelsOfferNoAdd() {
        rule.setContent {
            androidx.compose.foundation.layout.Column {
                com.kurtz.homerecord.housemanual.emergency.EmergencyScreen(com.kurtz.homerecord.housemanual.emergency.EmergencyUiState(loading = false, viewOnly = true), {})
            }
        }
        rule.onNodeWithTag("hm_emergency_add").assertDoesNotExist()
        rule.onNodeWithTag("hm_emergency_manage_add").assertDoesNotExist()
    }

    @Test fun panelsOfferNoAdd() {
        rule.setContent { com.kurtz.homerecord.housemanual.panel.PanelsScreen(com.kurtz.homerecord.housemanual.panel.PanelsUiState(loading = false, viewOnly = true), {}) }
        rule.onNodeWithTag("hm_panels_add").assertDoesNotExist()
    }

    @Test fun contactsAndDocumentsOfferNoAddOrEdit() {
        rule.setContent {
            androidx.compose.foundation.layout.Column {
                com.kurtz.homerecord.housemanual.contacts.ContactsScreen(com.kurtz.homerecord.housemanual.contacts.ContactsUiState(loading = false, viewOnly = true), {})
            }
        }
        rule.onNodeWithTag("hm_contacts_fab").assertDoesNotExist()
        rule.onNodeWithTag("hm_contacts_add").assertDoesNotExist()
    }

    @Test fun documentsOfferNoAdd() {
        rule.setContent { com.kurtz.homerecord.housemanual.documents.DocumentsScreen(com.kurtz.homerecord.housemanual.documents.DocumentsUiState(loading = false, viewOnly = true), {}) }
        rule.onNodeWithTag("hm_documents_fab").assertDoesNotExist()
        rule.onNodeWithTag("hm_documents_add").assertDoesNotExist()
    }

    @Test fun itemPageHidesEditOverflowPlaceAndAdds() {
        val item = com.kurtz.homerecord.core.data.ItemEntity(
            id = "i1", homeId = "h1", roomId = "r1", category = com.kurtz.homerecord.core.model.ItemCategory.HEATING_COOLING.name, subtype = "furnace",
            name = "Furnace", createdAt = 1, updatedAt = 1,
        )
        val detail = com.kurtz.homerecord.housemodel.items.ItemDetail(
            item, com.kurtz.homerecord.core.model.ItemCategory.HEATING_COOLING, "Furnace", "Basement", null, com.kurtz.homerecord.core.model.WarrantyStatus.Unknown, emptyList(),
        )
        val consumable = com.kurtz.homerecord.core.data.ConsumableEntity(id = "c1", itemId = "i1", kind = "FILTER", spec = "16x25x1 MERV 11", createdAt = 1, updatedAt = 1)
        rule.setContent {
            com.kurtz.homerecord.housemanual.items.ItemDetailScreen(
                com.kurtz.homerecord.housemanual.items.ItemDetailUiState(
                    loading = false, viewOnly = true, detail = detail, consumables = listOf(consumable),
                    shutoff = com.kurtz.homerecord.housemanual.items.ShutoffChoice("valve", "Furnace gas valve", "Basement"),
                ),
                {},
            )
        }
        rule.onNodeWithTag("hm_item_detail_share").assertIsDisplayed()
        listOf(
            "hm_item_detail_edit", "hm_item_detail_more", "hm_item_detail_lookup", "hm_item_detail_consumable_add", "hm_item_detail_service_add",
            "hm_item_detail_reminder_add", "hm_item_detail_document_add", "hm_item_detail_document_scan", "hm_item_detail_document_attach",
        ).forEach { rule.onNodeWithTag(it).assertDoesNotExist() }
        rule.onNodeWithTag("hm_item_detail_place").assertHasNoClickAction()
        rule.onNodeWithTag("hm_item_detail_consumable_edit").assertHasNoClickAction()
        rule.onNodeWithTag("hm_item_detail_shutoff_chip").assertExists()
        rule.onNodeWithTag("hm_item_detail_shutoff_change").assertDoesNotExist()
    }

    @Test fun remindersTabHasNoAddZoneOrRowActions() {
        val row = com.kurtz.homerecord.housemodel.reminders.ReminderRow(
            id = "r1", title = "Change furnace filter", itemId = "i1", itemName = "Furnace",
            bucket = com.kurtz.homerecord.housemodel.reminders.ReminderBucket.DUE, dueOn = java.time.LocalDate.of(2026, 11, 1),
            intervalMonths = 3, lastDoneOn = null, snoozedUntil = null, seasonal = false,
        )
        rule.setContent {
            com.kurtz.homerecord.housemanual.reminders.RemindersScreen(
                com.kurtz.homerecord.housemanual.reminders.RemindersUiState(loading = false, rows = listOf(row), today = java.time.LocalDate.of(2026, 11, 6), viewOnly = true),
                {},
            )
        }
        rule.onNodeWithTag("hm_reminders_row_due_0").assertExists()
        listOf("hm_reminders_fab", "hm_reminders_done_due_0", "hm_reminders_snooze_due_0", "hm_reminders_zone_cold").forEach { rule.onNodeWithTag(it).assertDoesNotExist() }
    }

    @Test fun backupKeepsMakeButHidesRestore() {
        rule.setContent { com.kurtz.homerecord.housemanual.backup.BackupScreen(com.kurtz.homerecord.housemanual.backup.BackupUiState(homeName = "Maple St", viewOnly = true), {}) }
        rule.onNodeWithTag("hm_backup_make").assertExists()
        rule.onNodeWithTag("hm_backup_restore").assertDoesNotExist()
    }

    @Test fun theRoomPageTakesNoWallPhotoAndKeepsThePlanEditorShut() {
        val walls = listOf(
            com.kurtz.homerecord.housemodel.walls.RoomWall("w1", com.kurtz.homerecord.housemodel.walls.WallSide.NORTH, null, 168, 96, mapOf(com.kurtz.homerecord.housemodel.walls.WallStage.FINISHED to "/media/sink.jpg")),
            com.kurtz.homerecord.housemodel.walls.RoomWall("w2", com.kurtz.homerecord.housemodel.walls.WallSide.EAST, null, 144, 96),
        )
        val onWall = overlay("o1", wallId = "w1")
        val onPlan = overlay("o2", wallId = null)
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                com.kurtz.homerecord.housemodel.ui.RoomScreen(
                    com.kurtz.homerecord.housemodel.ui.RoomUiState(
                        loading = false, room = PlanRoom("r1", "Kitchen", points = listOf(Pt(0, 0), Pt(168, 0), Pt(168, 144), Pt(0, 144))),
                        walls = walls, canPhotograph = true, canOpenWalls = true, viewOnly = true, overlays = listOf(onWall, onPlan),
                    ),
                ) {}
            }
        }
        rule.onNodeWithTag("hm_room_wall_w1").assertExists()
        rule.onNodeWithTag("hm_room_wall_photograph_w1").assertDoesNotExist()
        rule.onNodeWithTag("hm_room_wall_photograph_w2").assertDoesNotExist()
        rule.onNodeWithTag("hm_room_overlay_o1").assertHasClickAction()
        rule.onNodeWithTag("hm_room_overlay_o2").assertHasNoClickAction()
    }

    @Test fun theWallPageDrawsNothingAndOffersNoEditOrDelete() {
        val wall = com.kurtz.homerecord.housemodel.walls.RoomWall("w1", com.kurtz.homerecord.housemodel.walls.WallSide.NORTH, null, 168, 96)
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                com.kurtz.homerecord.housemodel.ui.WallOverlaysScreen(
                    com.kurtz.homerecord.housemodel.ui.WallOverlaysUiState(loading = false, wall = wall, roomName = "Kitchen", overlays = listOf(overlay("o1", "w1")), selectedId = "o1", canEdit = false),
                    {},
                )
            }
        }
        rule.onNodeWithTag("hm_wall_selection").assertIsDisplayed()
        rule.onNodeWithTag("hm_wall_overlay_done").assertExists()
        listOf("hm_wall_add_overlay", "hm_wall_overlay_edit", "hm_wall_overlay_delete").forEach { rule.onNodeWithTag(it).assertDoesNotExist() }
    }

    @Test fun theWallPageListHasNoAddOverlay() {
        val wall = com.kurtz.homerecord.housemodel.walls.RoomWall("w1", com.kurtz.homerecord.housemodel.walls.WallSide.NORTH, null, 168, 96)
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                com.kurtz.homerecord.housemodel.ui.WallOverlaysScreen(
                    com.kurtz.homerecord.housemodel.ui.WallOverlaysUiState(loading = false, wall = wall, roomName = "Kitchen", canEdit = false),
                    {},
                )
            }
        }
        rule.onNodeWithTag("hm_wall_overlays_empty").assertExists()
        rule.onNodeWithTag("hm_wall_add_overlay").assertDoesNotExist()
    }

    @Test fun anEmpty3DFloorOffersNoEditPlan() {
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "hm") {
                com.kurtz.homerecord.housemodel.ui.House3DScreen(
                    com.kurtz.homerecord.housemodel.ui.House3DUiState(
                        loading = false, homeName = "Maple St", floorId = "f1",
                        scene = com.kurtz.homerecord.housemodel.scene.FloorScene.build(emptyList(), 96, emptyMap()),
                    ),
                    {}, {}, null,
                )
            }
        }
        rule.onNodeWithText("The owner hasn't drawn this floor's rooms yet.").assertExists()
        rule.onNodeWithTag("hm_3d_edit_plan").assertDoesNotExist()
    }

    private fun overlay(id: String, wallId: String?) = com.kurtz.homerecord.housemodel.overlays.Overlay(
        id, "h1", "r1", wallId, com.kurtz.homerecord.housemodel.overlays.OverlayKind.WATER_COLD, "Sink supply", null,
        com.kurtz.homerecord.housemodel.overlays.OverlayDepth.BEHIND_DRYWALL,
        listOf(com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.2f, 0.5f), com.kurtz.homerecord.housemodel.overlays.OverlayPoint(0.6f, 0.5f)),
        com.kurtz.homerecord.housemodel.walls.WallStage.ROUGH_IN, null, null, 0L,
    )
}
