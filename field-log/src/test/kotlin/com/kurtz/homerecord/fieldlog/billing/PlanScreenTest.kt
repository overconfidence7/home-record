package com.kurtz.homerecord.fieldlog.billing

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.billing.CompanyPlanOffer
import com.kurtz.homerecord.core.billing.FieldLogPlan
import com.kurtz.homerecord.core.billing.FieldLogPlanKind
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.clock.ClockScreen
import com.kurtz.homerecord.fieldlog.clock.ClockUiState
import com.kurtz.homerecord.fieldlog.more.MoreEvent
import com.kurtz.homerecord.fieldlog.more.MoreScreen
import com.kurtz.homerecord.fieldlog.more.MoreUiState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F56: the Owner's paywall, the Plan row in More, and the crew's read-only renew screen.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PlanScreenTest {
    @get:Rule val rule = createComposeRule()

    private val offers = listOf(
        CompanyPlanOffer("fieldlog_solo_monthly", FieldLogPlanKind.Solo, "$39.00"),
        CompanyPlanOffer("fieldlog_crew_monthly", FieldLogPlanKind.Crew, "$79.00"),
    )

    @Test fun lapsedOwnerSeesPricesBuysAndCanSignOutButHasNoBack() {
        val events = mutableListOf<PlanEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                PlanScreen(PlanUiState(FieldLogPlan(FieldLogPlanKind.Expired, null, 0), lapsed = true, loading = false, offers = offers)) { events += it }
            }
        }
        rule.onNodeWithTag("fl_plan_status").assertTextContains("has ended", substring = true)
        rule.onNodeWithTag("fl_plan_card_crew", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("\$79.00 a month").assertExists()
        rule.onNodeWithTag("fl_back").assertDoesNotExist()
        rule.onNodeWithTag("fl_plan_crew").performScrollTo().performClick()
        rule.onNodeWithTag("fl_plan_sign_out").performScrollTo().performClick()
        assertThat(events).containsExactly(PlanEvent.Buy("fieldlog_crew_monthly"), PlanEvent.SignOut).inOrder()
    }

    @Test fun trialFromMoreShowsDaysLeftAndBack() {
        val events = mutableListOf<PlanEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                PlanScreen(PlanUiState(FieldLogPlan(FieldLogPlanKind.Trial, 9, 10), loading = false, offers = offers)) { events += it }
            }
        }
        rule.onNodeWithTag("fl_plan_status").assertTextContains("Trial: 9 days left", substring = true)
        rule.onNodeWithTag("fl_back").performClick()
        assertThat(events).containsExactly(PlanEvent.Back)
    }

    @Test fun currentPlanHasNoBuyButton() {
        val state = PlanUiState(FieldLogPlan(FieldLogPlanKind.Crew, null, 10), loading = false, offers = offers)
        rule.setContent { HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { PlanScreen(state) {} } }
        rule.onNodeWithTag("fl_plan_current_crew").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("fl_plan_crew").assertDoesNotExist()
        rule.onNodeWithTag("fl_plan_solo").assertExists()
    }

    @Test fun pricesFailedShowsRetry() {
        val events = mutableListOf<PlanEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                PlanScreen(PlanUiState(FieldLogPlan(FieldLogPlanKind.Expired, null, 0), lapsed = true, loading = false, loadFailed = true)) { events += it }
            }
        }
        rule.onNodeWithTag("fl_plan_error").assertIsDisplayed()
        rule.onNodeWithTag("fl_plan_retry").performClick()
        assertThat(events).containsExactly(PlanEvent.Retry)
    }

    @Test fun planRowIsTheOwnersOnly() {
        val events = mutableListOf<MoreEvent>()
        var role by androidx.compose.runtime.mutableStateOf(CompanyRole.Owner)
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                MoreScreen(MoreUiState("Kurtz Electric", role, plan = FieldLogPlan(FieldLogPlanKind.Trial, 9, 10)), { events += it })
            }
        }
        rule.onNodeWithTag("fl_more_plan").assertTextContains("Trial, 9 days left", substring = true).performClick()
        assertThat(events).containsExactly(MoreEvent.OpenPlan)
        role = CompanyRole.Office
        rule.onNodeWithTag("fl_more_plan").assertDoesNotExist()
    }

    @Test fun crewAfterThePlanEndsSeesRenewAndNoClockIn() {
        var signedOut = false
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                ClockScreen(ClockUiState(loading = false), {}, readOnly = true, onSignOut = { signedOut = true })
            }
        }
        rule.onNodeWithTag("fl_renew_notice").assertIsDisplayed()
        rule.onNodeWithText("plan has ended", substring = true).assertIsDisplayed()
        rule.onNodeWithTag("fl_clock_in").assertDoesNotExist()
        rule.onNodeWithTag("fl_clock_note").assertDoesNotExist()
        rule.onNodeWithTag("fl_renew_sign_out").performClick()
        assertThat(signedOut).isTrue()
    }
}
