package com.kurtz.homerecord.housemanual

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.sync.SyncPhase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.auth.AccountPhase
import com.kurtz.homerecord.coreui.auth.AccountScreen
import com.kurtz.homerecord.coreui.auth.AccountUiState
import com.kurtz.homerecord.coreui.components.SyncStatusChip
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.housemanual.account.AccountSyncEvent
import com.kurtz.homerecord.housemanual.account.AccountSyncScreen
import com.kurtz.homerecord.housemanual.account.SyncExplainer
import com.kurtz.homerecord.housemanual.more.MoreEvent
import com.kurtz.homerecord.housemanual.more.MoreScreen
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-H15: More → Account and sync opens HM-Account, which says what sync does and offers Sign out.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class AccountSyncScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test
    @AcceptanceCriterion("AC-H15")
    fun moreAccountRowOpensAccount() {
        val events = mutableListOf<MoreEvent>()
        rule.setContent { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { MoreScreen({ events += it }) } }
        rule.onNodeWithTag("hm_more_account").performScrollTo().assertIsDisplayed().performClick()
        assertThat(events).containsExactly(MoreEvent.OpenAccount)
    }

    @Test
    fun moreScrollsSoTheLastRowIsReachableAt200Percent() {
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density = 2.625f, fontScale = 2f),
            ) { HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { MoreScreen({}) } }
        }
        rule.onNodeWithTag("hm_more_account").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("hm_more_import").performScrollTo().assertIsDisplayed()
    }

    @Test
    @AcceptanceCriterion("AC-H15")
    fun signedInAccountExplainsSyncAndSignsOut() {
        val events = mutableListOf<Any>()
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) {
                CompositionLocalProvider(LocalTagPrefix provides "hm") {
                    AccountSyncScreen({ events += it }) { modifier ->
                        AccountScreen(AccountUiState(AccountPhase.SignedIn, email = "dana.ortiz@example.com"), { events += it }, modifier) {
                            SyncStatusChip(SyncPhase.Idle, pending = 0, onClick = {})
                            SyncExplainer()
                        }
                    }
                }
            }
        }
        rule.onNodeWithText("Account and sync").assertIsDisplayed()
        rule.onNodeWithTag("hm_account_email").assertIsDisplayed()
        rule.onNodeWithTag("hm_account_sync_explainer").assertIsDisplayed()
        rule.onNodeWithTag("hm_account_sign_out").performScrollTo().performClick()
        rule.onNodeWithTag("hm_back").performClick()
        assertThat(events).containsExactly(com.kurtz.homerecord.coreui.auth.AccountEvent.SignOut, AccountSyncEvent.Back).inOrder()
    }
}
