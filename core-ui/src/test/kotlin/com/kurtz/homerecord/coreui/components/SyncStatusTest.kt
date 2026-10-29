package com.kurtz.homerecord.coreui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.sync.RefusedChange
import com.kurtz.homerecord.core.sync.SyncPhase
import com.kurtz.homerecord.coreui.R
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** cr:sync-poison-op "show it" (CR2a2): a change the server refused is never silent on the sync status (AC-F52). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncStatusTest {
    @get:Rule val rule = createComposeRule()

    private val refused = listOf(RefusedChange("opening", "o1", 1_793_200_440_000L), RefusedChange("punch", "p1", 1_793_194_800_000L))

    @Test fun refusedChangesShowOnTheChipAndTapListsThemInsteadOfSyncing() {
        var synced = 0
        var cleared = 0
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                var list by remember { mutableStateOf(refused) }
                SyncStatus(SyncPhase.Idle, 0, list, onSyncNow = { synced++ }, onClearRefused = { cleared++; list = emptyList() })
            }
        }
        rule.onNodeWithText("2 changes didn't sync. Tap to see them").assertIsDisplayed().performClick()
        assertThat(synced).isEqualTo(0)
        rule.onNodeWithText("Changes that didn't sync").assertIsDisplayed()
        rule.onAllNodesWithTag("fl_sync_refused_row").assertCountEquals(2)
        rule.onNodeWithText("Door or window").assertIsDisplayed()
        rule.onNodeWithText("Clock in or out").assertIsDisplayed()
        rule.onNodeWithTag("fl_sync_refused_clear").performClick()
        assertThat(cleared).isEqualTo(1)
        rule.onNodeWithText("Changes that didn't sync").assertDoesNotExist()
        rule.onNodeWithText("Everything is synced").assertIsDisplayed().performClick()
        assertThat(synced).isEqualTo(1)
    }

    @Test fun closeKeepsTheListAndOfflineStillWins() {
        var phase by mutableStateOf(SyncPhase.Idle)
        rule.setContent {
            HomeRecordTheme(Brand.HouseManual, dynamicColor = false) { SyncStatus(phase, 1, refused.take(1), onSyncNow = {}, onClearRefused = {}) }
        }
        rule.onNodeWithText("1 change didn't sync. Tap to see it").performClick()
        rule.onNodeWithTag("hm_sync_refused_close").performClick()
        rule.onNodeWithText("Changes that didn't sync").assertDoesNotExist()
        rule.onNodeWithText("1 change didn't sync. Tap to see it").assertIsDisplayed()
        phase = SyncPhase.Offline
        rule.onNodeWithText("Offline · 1 change waiting").assertIsDisplayed()
    }

    @Test fun everySyncedTableHasALabelAndUnknownOnesFallBack() {
        listOf("home", "floor", "room", "wall", "opening", "stair", "item", "consumable", "contact", "document", "panel", "breaker",
            "job", "client", "punch", "shift", "photo", "note", "task", "job_file", "daily_log", "change_order", "material")
            .forEach { assertThat(refusedEntityLabel(it)).isNotEqualTo(R.string.sync_entity_other) }
        assertThat(refusedEntityLabel("something_new")).isEqualTo(R.string.sync_entity_other)
    }
}
