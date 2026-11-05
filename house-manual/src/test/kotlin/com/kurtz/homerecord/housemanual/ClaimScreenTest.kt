package com.kurtz.homerecord.housemanual

import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.bundle.HandoffClaimFailure
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.importing.ClaimEvent
import com.kurtz.homerecord.housemanual.importing.ClaimPhase
import com.kurtz.homerecord.housemanual.importing.ClaimScreen
import com.kurtz.homerecord.housemanual.importing.ClaimUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-H17 (claim link): HM-Claim shows the sign-in form when signed out, progress while downloading, and errors with
// Retry only where a retry can work.
@AcceptanceCriterion("AC-H17")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ClaimScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun show(state: ClaimUiState, events: MutableList<ClaimEvent> = mutableListOf()) = rule.setContent {
        HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
            ClaimScreen(state, { events += it }) { m -> Text("sign-in form", m.testTag("form")) }
        }
    }

    @Test fun signedOutShowsTheIntroAndTheSignInForm() { // AC-H17
        show(ClaimUiState(phase = ClaimPhase.SignIn))
        rule.onNodeWithTag("hm_claim_sign_in").assertIsDisplayed()
        rule.onNodeWithTag("form").assertIsDisplayed()
        rule.onNodeWithText("The link works for one account", substring = true).assertIsDisplayed()
    }

    @Test fun downloadingNamesTheContractor() { // AC-H17
        show(ClaimUiState(phase = ClaimPhase.Downloading, companyName = "QA Electric", homeName = "Ortiz house"))
        rule.onNodeWithTag("hm_claim_working").assertIsDisplayed()
        rule.onNodeWithText("From QA Electric").assertIsDisplayed()
        rule.onNodeWithText("Ortiz house").assertIsDisplayed()
    }

    @Test fun offlineOffersRetryAndClose() { // AC-H17
        val events = mutableListOf<ClaimEvent>()
        show(ClaimUiState(phase = ClaimPhase.Failed, failure = HandoffClaimFailure.Offline), events)
        rule.onNodeWithTag("hm_claim_retry").performClick()
        rule.onNodeWithTag("hm_claim_close").performClick()
        assertThat(events).containsExactly(ClaimEvent.Retry, ClaimEvent.Close).inOrder()
    }

    @Test fun anExpiredLinkOnlyCloses() { // AC-H17, AC-B40
        val events = mutableListOf<ClaimEvent>()
        show(ClaimUiState(phase = ClaimPhase.Failed, failure = HandoffClaimFailure.Expired), events)
        rule.onNodeWithText("This link has expired").assertIsDisplayed()
        rule.onNodeWithTag("hm_claim_retry").assertDoesNotExist()
        rule.onNodeWithTag("hm_claim_close").performClick()
        assertThat(events).containsExactly(ClaimEvent.Close)
    }
}
