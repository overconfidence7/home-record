package com.kurtz.homerecord.housemanual.acceptance

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.house.FloorsEvent
import com.kurtz.homerecord.housemanual.house.FloorsScreen
import com.kurtz.homerecord.housemanual.house.FloorsUiState
import com.kurtz.homerecord.housemanual.onboarding.OnboardingEvent
import com.kurtz.homerecord.housemanual.onboarding.OnboardingScreen
import com.kurtz.homerecord.housemanual.onboarding.OnboardingUiState
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.FloorSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingAndFloorsAcceptanceTest {
    @get:Rule val rule = createComposeRule()

    @AcceptanceCriterion("AC-C1")
    @Test fun onboardingOffersFoundationsGarageAndDrawPlan() {
        val events = mutableListOf<OnboardingEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                OnboardingScreen(OnboardingUiState(name = "Maple Street"), onEvent = { events += it })
            }
        }
        rule.onNodeWithTag("hm_onboarding_name").assertIsDisplayed()
        rule.onNodeWithTag("hm_onboarding_type").assertIsDisplayed()
        Foundation.entries.forEach { rule.onNodeWithTag("hm_onboarding_foundation_${it.name.lowercase()}").assertIsDisplayed() }
        rule.onNodeWithTag("hm_onboarding_foundation_slab").performClick()
        rule.onNodeWithTag("hm_onboarding_garage").performClick()
        assertThat(events).contains(OnboardingEvent.FoundationChanged(Foundation.SLAB))
        assertThat(events).contains(OnboardingEvent.GarageChanged(true))
    }

    @AcceptanceCriterion("AC-C3")
    @Test fun floorsListShowsRowsTopFirstAndAddButton() {
        val events = mutableListOf<FloorsEvent>()
        val basement = FloorSummary("f-basement", "Basement", 96, false, 3)
        val main = FloorSummary("f-main", "Main", 108, false, 6)
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                FloorsScreen(FloorsUiState(loading = false, floors = listOf(basement, main)), onEvent = { events += it }, onBack = {})
            }
        }
        rule.onNodeWithTag("row_f-main").assertIsDisplayed()
        rule.onNodeWithTag("row_f-basement").assertIsDisplayed()
        rule.onNodeWithTag("hm_floors_fab").performClick()
        assertThat(events).contains(FloorsEvent.AddClicked)
    }
}
