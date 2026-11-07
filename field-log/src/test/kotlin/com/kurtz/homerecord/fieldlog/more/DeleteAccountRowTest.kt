package com.kurtz.homerecord.fieldlog.more

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.coreui.account.DeleteAccountViewModel
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.google.common.truth.Truth.assertThat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// T081d: FL-More "Delete account" sits under Sign out, asks first, deletes only on confirm and says why a try failed.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DeleteAccountRowTest {
    @get:Rule val rule = createComposeRule()

    private fun more(vm: DeleteAccountViewModel) = rule.setContent {
        val state by vm.state.collectAsState()
        HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
            MoreScreen(MoreUiState("Kurtz Electric", CompanyRole.Owner), onEvent = {}, deleteAccount = state, onDeleteAccount = vm::onEvent)
        }
    }

    @Test fun cancelDeletesNothing() {
        var calls = 0
        more(DeleteAccountViewModel({ calls++; ApiResult.Ok(Unit) }))
        rule.onNodeWithTag("fl_more_delete_account").performScrollTo().performClick()
        rule.onNodeWithTag("fl_more_delete_account_cancel").performClick()
        rule.onNodeWithTag("fl_more_delete_account_confirm").assertDoesNotExist()
        assertThat(calls).isEqualTo(0)
    }

    @Test fun confirmDeletesTheAccount() {
        var calls = 0
        more(DeleteAccountViewModel({ calls++; ApiResult.Ok(Unit) }))
        rule.onNodeWithTag("fl_more_delete_account").performScrollTo().performClick()
        rule.onNodeWithTag("fl_more_delete_account_confirm").performClick()
        rule.waitForIdle()
        assertThat(calls).isEqualTo(1)
    }

    @Test fun anOfflineTrySaysSo() {
        more(DeleteAccountViewModel({ ApiResult.Err(ApiFailure.Offline) }))
        rule.onNodeWithTag("fl_more_delete_account").performScrollTo().performClick()
        rule.onNodeWithTag("fl_more_delete_account_confirm").performClick()
        rule.onNodeWithTag("fl_more_delete_account_error").performScrollTo().assertIsDisplayed()
    }
}
