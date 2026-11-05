package com.kurtz.homerecord.fieldlog.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.photo.FileStorage
import com.kurtz.homerecord.core.units.UnitSystem
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

// T065a: More reaches Settings and About and names the signed-in account (AC-F4); Settings picks units (AC-C4),
// opens notification settings and frees up storage (AC-F20).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class SettingsScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun settings(state: SettingsUiState, events: MutableList<SettingsEvent>) = rule.setContent {
        HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { SettingsScreen(state, { events += it }) }
    }

    // AC-C4
    @Test fun unitsPickerSendsTheChoice() {
        val events = mutableListOf<SettingsEvent>()
        settings(SettingsUiState(storage = FileStorage(1, 0)), events)
        rule.onNodeWithTag("fl_settings_units_metric").performClick()
        assertThat(events).containsExactly(SettingsEvent.SetUnits(UnitSystem.METRIC))
    }

    @Test fun notificationsOffSaysSoAndOpensPhoneSettings() {
        val events = mutableListOf<SettingsEvent>()
        settings(SettingsUiState(notificationsOn = false, storage = FileStorage(1, 0)), events)
        rule.onNodeWithText("Notifications are off").assertIsDisplayed()
        rule.onNodeWithTag("fl_settings_notifications").performScrollTo().performClick()
        assertThat(events).containsExactly(SettingsEvent.OpenNotificationSettings)
    }

    // AC-F20
    @Test fun freeUpIsOffWithNothingCached() {
        settings(SettingsUiState(storage = FileStorage(2_400_000, 0)), mutableListOf())
        rule.onNodeWithTag("fl_more_free_up").assertIsNotEnabled()
    }

    // AC-F20
    @Test fun freeUpSendsTheEvent() {
        val events = mutableListOf<SettingsEvent>()
        settings(SettingsUiState(storage = FileStorage(2_400_000, 318_000)), events)
        rule.onNodeWithTag("fl_more_free_up").performScrollTo().performClick()
        assertThat(events).containsExactly(SettingsEvent.FreeUp)
    }

    @Test fun storageShowsLoadingWhileCounted() {
        settings(SettingsUiState(), mutableListOf())
        rule.onNodeWithTag("fl_settings_storage_loading").assertExists()
    }

    // AC-F4
    @Test fun moreNamesTheAccountAndOpensSettingsAndAbout() {
        val events = mutableListOf<MoreEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                MoreScreen(
                    MoreUiState("Kurtz Electric", CompanyRole.Crew, userEmail = "sam.ortiz@gmail.com", units = UnitSystem.METRIC, versionName = "0.1.0"),
                    { events += it },
                )
            }
        }
        rule.onNodeWithTag("fl_more_account").assertTextContains("Signed in as sam.ortiz@gmail.com")
        rule.onNodeWithTag("fl_more_settings").assertTextContains("Metric", substring = true)
        rule.onNodeWithTag("fl_more_settings").performClick()
        rule.onNodeWithTag("fl_more_about").assertTextContains("Version 0.1.0", substring = true)
        rule.onNodeWithTag("fl_more_about").performClick()
        assertThat(events).containsExactly(MoreEvent.OpenSettings, MoreEvent.OpenAbout).inOrder()
    }

    @Test fun aboutShowsTheVersion() {
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { AboutScreen(AboutUiState("0.1.0", 7), {}) }
        }
        rule.onNodeWithTag("fl_about_version").assertTextContains("0.1.0 (build 7)", substring = true)
    }
}
