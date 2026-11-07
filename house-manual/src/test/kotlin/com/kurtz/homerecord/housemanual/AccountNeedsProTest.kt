package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.auth.AuthState
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.account.AccountSyncEvent
import com.kurtz.homerecord.housemanual.account.AccountSyncScreen
import com.kurtz.homerecord.housemanual.account.accountSignInNeedsPro
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-H18: on the free tier, Sign in on HM-Account opens HM-Paywall; Pro shows the sign-in form.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AccountNeedsProTest {
    @get:Rule val rule = createComposeRule()

    @Test
    @AcceptanceCriterion("AC-H18")
    fun onlyASignedOutFreePhoneNeedsPro() {
        assertThat(accountSignInNeedsPro(pro = false, auth = AuthState.SignedOut)).isTrue()
        assertThat(accountSignInNeedsPro(pro = true, auth = AuthState.SignedOut)).isFalse()
        // A free account already signed in (claim link, second phone) keeps its summary and Sign out
        assertThat(accountSignInNeedsPro(pro = false, auth = AuthState.SignedIn("t"))).isFalse()
        assertThat(accountSignInNeedsPro(pro = false, auth = AuthState.Restoring)).isFalse()
    }

    @Test
    @AcceptanceCriterion("AC-H18")
    fun signInOnTheFreeTierOpensThePaywall() {
        val events = mutableListOf<AccountSyncEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                AccountSyncScreen({ events += it }, needsPro = true) { Box(it.testTag("form")) }
            }
        }
        rule.onNodeWithTag("form").assertDoesNotExist()
        rule.onNodeWithTag("hm_account_pro_body").assertIsDisplayed()
        rule.onNodeWithTag("hm_account_sign_in_pro").assertIsDisplayed().performClick()
        assertThat(events).containsExactly(AccountSyncEvent.OpenPro)
    }

    @Test
    @AcceptanceCriterion("AC-H18")
    fun proShowsTheSignInForm() {
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                AccountSyncScreen({}, needsPro = false) { Box(it.testTag("form")) }
            }
        }
        rule.onNodeWithTag("form").assertExists()
        rule.onNodeWithTag("hm_account_sign_in_pro").assertDoesNotExist()
    }

    @Test
    @AcceptanceCriterion("AC-H18")
    fun aSignedInFreePhoneSyncsOnlyAProHome() {
        assertThat(com.kurtz.homerecord.housemanual.account.accountSyncNeedsPro(phonePro = false, homePro = false)).isTrue()
        assertThat(com.kurtz.homerecord.housemanual.account.accountSyncNeedsPro(phonePro = false, homePro = true)).isFalse()
        assertThat(com.kurtz.homerecord.housemanual.account.accountSyncNeedsPro(phonePro = true, homePro = false)).isFalse()
    }

    @Test
    @AcceptanceCriterion("AC-H18")
    fun syncOffNoteOpensPro() {
        var opened = 0
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                com.kurtz.homerecord.housemanual.account.SyncNeedsPro { opened++ }
            }
        }
        rule.onNodeWithTag("hm_account_sync_pro_body").assertIsDisplayed()
        rule.onNodeWithTag("hm_account_sync_pro").assertIsDisplayed().performClick()
        assertThat(opened).isEqualTo(1)
    }
}
