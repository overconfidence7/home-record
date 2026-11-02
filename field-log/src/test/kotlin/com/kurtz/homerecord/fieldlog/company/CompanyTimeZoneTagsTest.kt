package com.kurtz.homerecord.fieldlog.company

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyProfile
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F42 (R09f1), CLAUDE.md QA rule 3: the time zone row and its sheet rows have stable ids
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CompanyTimeZoneTagsTest {
    @get:Rule val rule = createComposeRule()

    private val profile = CompanyProfile("c1", "Kurtz Electric", null, null, null, null, CompanySettings())

    @Test fun rowShowsEasternUntilSetAndOpensThePicker() {
        val events = mutableListOf<CompanyEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                CompanyScreen(CompanyUiState(status = TeamStatus.Ready, loaded = profile, name = profile.name), { events += it }, {})
            }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("fl_company_timezone"))
        rule.onNodeWithText("Eastern").assertIsDisplayed()
        rule.onNodeWithTag("fl_company_timezone").performClick()
        assertThat(events).contains(CompanyEvent.OpenTimeZones)
    }

    @Test fun sheetRowsPickTheirZone() {
        val events = mutableListOf<CompanyEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                CompositionLocalProvider(LocalTagPrefix provides "fl") { CompanyTimeZoneList(null) { events += it } }
            }
        }
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("fl_sheet_pacific_honolulu"))
        rule.onNodeWithTag("fl_sheet_pacific_honolulu").performClick()
        rule.onNodeWithTag("fl_sheet_america_chicago").performClick()
        assertThat(events).containsExactly(CompanyEvent.TimeZone("Pacific/Honolulu"), CompanyEvent.TimeZone("America/Chicago")).inOrder()
    }
}
