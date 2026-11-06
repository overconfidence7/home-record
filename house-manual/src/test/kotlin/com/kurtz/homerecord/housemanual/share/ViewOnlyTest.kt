package com.kurtz.homerecord.housemanual.share

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
}
