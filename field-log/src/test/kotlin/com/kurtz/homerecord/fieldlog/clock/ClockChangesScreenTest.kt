package com.kurtz.homerecord.fieldlog.clock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.kurtz.homerecord.fieldlog.ClockChangesPreview
import com.kurtz.homerecord.fieldlog.ClockOutPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F14: the audit entry (who, when, before/after, why) is visible on the crew's own punch in My timesheet.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class ClockChangesScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun showsWhoChangedMyPunchWhenAndWhy() {
        rule.setContent { ClockChangesPreview() }
        rule.onNodeWithTag("fl_clock_change_p1").assertIsDisplayed()
        rule.onNodeWithText("Clock in, Tue, Oct 20: 7:30 AM to 6:45 AM").assertIsDisplayed()
        rule.onNodeWithText("Changed by Dana Ruiz on Wed, Oct 21 at 4:10 PM").assertIsDisplayed()
        rule.onNodeWithText("Reason: Forgot to clock in at the supply house").assertIsDisplayed()
        rule.onNodeWithText("Job: Garage subpanel to Ortiz kitchen remodel").assertIsDisplayed()
    }

    @Test fun noChangesNoSection() {
        rule.setContent { ClockOutPreview() }
        rule.onNodeWithTag("fl_clock_changes").assertDoesNotExist()
    }
}
