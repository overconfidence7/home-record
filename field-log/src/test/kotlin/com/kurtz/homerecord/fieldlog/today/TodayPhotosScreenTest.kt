package com.kurtz.homerecord.fieldlog.today

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.punch.ClockStatus
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The camera button and Recent photos on FL-Today (SPEC FL-Today, AC-F24). */
@AcceptanceCriterion("AC-F24")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TodayPhotosScreenTest {
    @get:Rule val rule = createComposeRule()

    private val jobs = listOf(TodayJob("j1", "Kitchen Remodel", "412 Maple Ave"), TodayJob("j2", "Deck Rebuild", null))
    private val state = TodayUiState(
        "Kurtz Electric", CompanyRole.Crew, hasJobs = true, jobs = jobs,
        clock = TodayClock(ClockStatus.Working("j1", 0), "Kitchen Remodel", 60_000),
        photos = listOf(TodayPhoto("ph1", "j1", "/none/ph1.jpg", "Kitchen Remodel"), TodayPhoto("ph2", "j2", "", "Deck Rebuild")),
        capture = TodayCaptureChoice(target = "j1"),
    )

    private fun show(s: TodayUiState): MutableList<TodayEvent> {
        val events = mutableListOf<TodayEvent>()
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(com.kurtz.homerecord.coreui.shell.LocalTagPrefix provides "fl") {
                HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { TodayScreen(s, { events += it }) }
            }
        }
        return events
    }

    @Test fun theCameraButtonAsksToCapture() {
        val events = show(state)
        rule.onNodeWithTag("fl_today_capture").assertIsDisplayed().performClick()
        assertThat(events).containsExactly(TodayEvent.Capture)
    }

    @Test fun recentPhotosOpenOnTapAndSayWhichJob() {
        val events = show(state)
        rule.onNodeWithTag("fl_today_list").performScrollToNode(hasTestTag("fl_today_photos"))
        rule.onNodeWithText("Recent photos").assertExists()
        rule.onNodeWithContentDescription("Photo from Deck Rebuild").assertExists()
        rule.onNodeWithTag("fl_today_photo_ph1").performClick()
        assertThat(events).containsExactly(TodayEvent.OpenPhoto("ph1"))
    }

    @Test fun noPhotosNoRowAndNoLiveJobNoButton() {
        show(state.copy(photos = emptyList(), capture = TodayCaptureChoice()))
        rule.onNodeWithText("Recent photos").assertDoesNotExist()
        rule.onNodeWithTag("fl_today_capture").assertDoesNotExist()
    }

    @Test fun pickingAJobInTheSheetCapturesForIt() {
        val events = show(state.copy(capture = TodayCaptureChoice(options = jobs), pickingCaptureJob = true))
        rule.onNodeWithText("Photo for which job?").assertExists()
        rule.onNodeWithTag("fl_sheet_j2").performClick()
        assertThat(events).contains(TodayEvent.CaptureFor("j2"))
    }

    @Test fun theCameraButtonIsAFullTouchTarget() {
        show(state)
        val bounds = rule.onNodeWithTag("fl_today_capture").fetchSemanticsNode().boundsInRoot
        val density = rule.density.density
        assertThat(bounds.height / density).isAtLeast(56f)
    }
}
