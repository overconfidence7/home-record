package com.kurtz.homerecord.fieldlog.house

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemodel.FloorSummary
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.ui.HouseScreen
import com.kurtz.homerecord.housemodel.ui.HouseUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FL-House at 200% text keeps its plan ([judge:fl-house-plan-font200], rules 2 and 32): no second
 * heading under the job's top bar, and the actions under the plan are one row, Behind the wall
 * plus a More actions menu, instead of five stacked buttons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class JobHouseLargeTextTest {
    @get:Rule val rule = createComposeRule()

    private val state = HouseUiState(
        loading = false, homeName = "Hale kitchen", summary = "418 Linden Ave, Ithaca",
        floors = listOf(FloorSummary("f0", "Basement", 96, false, 1), FloorSummary("f1", "Main floor", 108, false, 3)),
        floorId = "f1",
        rooms = listOf(
            PlanRoom.rectangle("r1", "Kitchen", "KITCHEN", 0, 0, 168, 144),
            PlanRoom.rectangle("r2", "Mudroom", "MUDROOM", 168, 0, 96, 144),
        ),
    )

    private fun show(fontScale: Float, onFloors: () -> Unit = {}, onHandoff: (() -> Unit)? = null) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                    HouseScreen(
                        state = state, onEvent = {}, onOpenRoom = {}, onEditPlan = {}, onEditShape = {}, onOpenFloors = onFloors,
                        on3D = {}, onBehindWall = {}, showHeader = false, onHandoff = onHandoff,
                    )
                }
            }
        }
    }

    @Test fun atDoubleTextThePlanKeepsMostOfTheScreenAndTheRestOfTheActionsSitInAMenu() {
        var floors = false
        show(2f, onFloors = { floors = true })
        rule.onNodeWithText("418 Linden Ave, Ithaca").assertDoesNotExist()
        rule.onNodeWithTag("fl_house_behind_wall").assertExists()
        rule.onNodeWithTag("fl_house_floors").assertDoesNotExist()
        val plan = rule.onNodeWithTag("fl_house_plan").getBoundsInRoot()
        val root = rule.onRoot().getBoundsInRoot()
        assertThat((plan.bottom - plan.top).value).isAtLeast((root.bottom - root.top).value * 0.4f)
        rule.onNodeWithTag("fl_house_more").performClick()
        rule.onNodeWithTag("fl_house_floors").performClick()
        assertThat(floors).isTrue()
    }

    @Test fun atNormalTextEveryActionIsAButtonAndTheHeaderStaysOff() {
        show(1f)
        rule.onNodeWithText("Hale kitchen").assertDoesNotExist()
        listOf("behind_wall", "edit_plan", "3d", "floors", "export").forEach { rule.onNodeWithTag("fl_house_$it").assertExists() }
        rule.onNodeWithTag("fl_house_more").assertDoesNotExist()
    }

    @Test fun handOffIsAnActionOnlyWhenTheJobOffersIt() { // AC-F51
        var opened = false
        show(1f, onHandoff = { opened = true })
        rule.onNodeWithTag("fl_house_handoff").performClick()
        assertThat(opened).isTrue()
    }

    @Test fun withoutTheHandoffThereIsNoHandOffButton() { // AC-F51
        show(1f)
        rule.onNodeWithTag("fl_house_handoff").assertDoesNotExist()
    }
}
