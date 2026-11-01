package com.kurtz.homerecord.fieldlog.sync

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.filter
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.sync.ConflictEntry
import com.kurtz.homerecord.core.sync.ConflictOutcome
import com.kurtz.homerecord.core.sync.SyncPhase
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.more.MoreEvent
import com.kurtz.homerecord.fieldlog.more.MoreScreen
import com.kurtz.homerecord.fieldlog.more.MoreUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F52 (offline: changes saved on this phone, pending count, Sync now), AC-F53 (the conflict log): FL-Sync.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SyncLogScreenTest {
    @get:Rule val rule = createComposeRule()

    private val conflicts = listOf(
        ConflictEntry("task", "t1", "Set the kitchen outlets", ConflictOutcome.NewerKept, listOf("due date", "title"), 1_793_200_440_000L),
        ConflictEntry("punch", "p1", null, ConflictOutcome.OfficeKept, listOf("time"), 1_793_197_000_000L),
        ConflictEntry("photo", "ph1", null, ConflictOutcome.Refused, emptyList(), 1_793_190_000_000L),
    )

    private fun show(state: SyncLogUiState, events: MutableList<SyncLogEvent> = mutableListOf()) = rule.setContent {
        HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { SyncLogScreen(state, { events += it }) }
    }

    @Test fun offlineSaysChangesAreSavedOnThisPhone() {
        val events = mutableListOf<SyncLogEvent>()
        show(SyncLogUiState(SyncPhase.Offline, 5, emptyList(), loading = false), events)
        rule.onNodeWithTag("fl_sync_status").assertTextContains("5 changes saved on this phone", substring = true)
            .assertTextContains("sent when you're back online", substring = true)
        rule.onNodeWithTag("fl_sync_none").assertTextContains("No conflicts", substring = true)
        rule.onNodeWithTag("fl_sync_clear").assertDoesNotExist()
        rule.onNodeWithTag("fl_sync_now").performClick()
        assertThat(events).containsExactly(SyncLogEvent.SyncNow)
    }

    @Test fun syncingDisablesSyncNow() {
        show(SyncLogUiState(SyncPhase.Syncing, 2, emptyList(), loading = false))
        rule.onNodeWithTag("fl_sync_now").assertIsNotEnabled()
    }

    @Test fun upToDateSaysSo() {
        show(SyncLogUiState(SyncPhase.Idle, 0, emptyList(), loading = false))
        rule.onNodeWithTag("fl_sync_status").assertTextContains("Everything is synced", substring = true)
    }

    @Test fun conflictsReadInPlainWords() {
        show(SyncLogUiState(SyncPhase.Idle, 0, conflicts, loading = false))
        val rows = rule.onAllNodesWithTag("fl_sync_entry")
        rows[0].assertTextContains("Set the kitchen outlets", substring = true)
            .assertTextContains("Kept the newer change from another phone (due date, title).", substring = true)
        rows[1].assertTextContains("Clock in or out", substring = true)
            .assertTextContains("Kept the office's edit to this time.", substring = true)
        rule.onNodeWithTag("fl_sync_list").performScrollToIndex(4)
        rule.onAllNodesWithTag("fl_sync_entry").filter(hasText("The server turned this change down", substring = true)).assertCountEquals(1)
    }

    @Test fun clearAsksFirst() {
        val events = mutableListOf<SyncLogEvent>()
        show(SyncLogUiState(SyncPhase.Idle, 0, conflicts, loading = false), events)
        rule.onNodeWithTag("fl_sync_clear").performClick()
        rule.onNodeWithTag("fl_sync_clear_cancel").performClick()
        assertThat(events).isEmpty()
        rule.onNodeWithTag("fl_sync_clear").performClick()
        rule.onNodeWithTag("fl_sync_clear_confirm").performClick()
        assertThat(events).containsExactly(SyncLogEvent.Clear)
    }

    @Test fun moreShowsTheSyncRowForEveryRole() {
        val events = mutableListOf<MoreEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { MoreScreen(MoreUiState("Kurtz Electric", CompanyRole.Crew, unsynced = 3), { events += it }) }
        }
        rule.onNodeWithTag("fl_more_sync").assertTextContains("3 changes waiting to send", substring = true).performClick()
        assertThat(events).containsExactly(MoreEvent.OpenSync)
    }
}
