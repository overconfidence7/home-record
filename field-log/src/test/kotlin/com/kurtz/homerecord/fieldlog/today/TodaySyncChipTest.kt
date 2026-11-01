package com.kurtz.homerecord.fieldlog.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.sync.RefusedChange
import com.kurtz.homerecord.core.sync.SyncPhase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** T046b: Today's sync chip shows the pending count and opens FL-Sync, whatever it says (AC-F52). */
@AcceptanceCriterion("AC-F52")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TodaySyncChipTest {
    @get:Rule val rule = createComposeRule()

    private fun show(s: TodayUiState): MutableList<TodayEvent> {
        val events = mutableListOf<TodayEvent>()
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(com.kurtz.homerecord.coreui.shell.LocalTagPrefix provides "fl") {
                HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TodayScreen(s, { events += it }) }
            }
        }
        return events
    }

    @Test fun offlineTheChipCountsWhatIsWaitingAndOpensSync() {
        val events = show(TodayUiState("Kurtz Electric", CompanyRole.Crew, SyncPhase.Offline, pendingChanges = 3))
        rule.onNodeWithTag("fl_today_sync").assertTextContains("Offline · 3 changes waiting").performClick()
        assertThat(events).containsExactly(TodayEvent.OpenSync)
    }

    @Test fun refusedChangesAlsoOpenSyncInsteadOfADialog() {
        val refused = listOf(RefusedChange("punch", "p1", 1_793_194_800_000L))
        val events = show(TodayUiState("Kurtz Electric", CompanyRole.Owner, SyncPhase.Idle, 0, refused))
        rule.onNodeWithText("1 change didn't sync. Tap to see it").assertIsDisplayed().performClick()
        assertThat(events).containsExactly(TodayEvent.OpenSync)
        rule.onNodeWithText("Changes that didn't sync").assertDoesNotExist()
    }
}
