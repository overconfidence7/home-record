package com.kurtz.homerecord.fieldlog.more

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F4: sign-out asks first, and warns when unsent changes would be deleted with the company data.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SignOutDialogTest {
    @get:Rule val rule = createComposeRule()

    private fun more(unsynced: Int, onEvent: (MoreEvent) -> Unit = {}) = rule.setContent {
        HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { MoreScreen(MoreUiState("Kurtz Electric", CompanyRole.Crew, unsynced = unsynced), onEvent) }
    }

    @Test fun confirmSignsOut() {
        val events = mutableListOf<MoreEvent>()
        more(0) { events += it }
        rule.onNodeWithTag("fl_more_sign_out").performClick()
        rule.onNodeWithTag("fl_more_sign_out_body").assertTextContains("Company details saved on this phone are cleared", substring = true)
        rule.onNodeWithTag("fl_more_sign_out_confirm").performClick()
        assertThat(events).containsExactly(MoreEvent.SignOut)
    }

    @Test fun unsentChangesAreNamed() {
        more(3)
        rule.onNodeWithTag("fl_more_sign_out").performClick()
        rule.onNodeWithTag("fl_more_sign_out_body").assertIsDisplayed()
            .assertTextContains("3 changes have not reached the cloud yet", substring = true)
    }

    @Test fun cancelKeepsTheSession() {
        val events = mutableListOf<MoreEvent>()
        more(1) { events += it }
        rule.onNodeWithTag("fl_more_sign_out").performClick()
        rule.onNodeWithTag("fl_more_sign_out_body").assertTextContains("1 change has not reached the cloud yet", substring = true)
        rule.onNodeWithTag("fl_more_sign_out_cancel").performClick()
        rule.onNodeWithTag("fl_more_sign_out_body").assertDoesNotExist()
        assertThat(events).isEmpty()
    }
}
