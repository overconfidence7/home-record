package com.kurtz.homerecord.fieldlog.log

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.log.WeatherStatus
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.core.model.LogWeather
import com.kurtz.homerecord.core.model.LogLineKind
import com.kurtz.homerecord.core.model.LogDelay
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SPEC FL-Log: every section has its edit control for the lead, none for Crew; the delay picker lists the five reasons. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LogScreenTest {
    @get:Rule val rule = createComposeRule()
    private val day = LocalDate.of(2026, 10, 30)

    private val ready = LogUiState(
        day = day, today = day, loading = false, canEdit = true, exists = true,
        crew = listOf(LogCrewRow("m1", "Marcus Ortiz", 465, onSiteNow = false)),
        workDone = listOf("Set the base cabinets"),
        delays = listOf(LogDelay(DelayReason.WEATHER, "Rain held up the roof tear-off")),
        weatherStatus = WeatherStatus.Offline,
    )

    private fun show(state: LogUiState, onEvent: (LogEvent) -> Unit = {}) = rule.setContent {
        HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { LogScreen(state, onEvent) }
    }

    @AcceptanceCriterion("AC-F30", "AC-F31", "AC-F32")
    @Test fun leadGetsAnEditControlOnEverySectionAndOfflineWeatherIsSaid() {
        val events = mutableListOf<LogEvent>()
        show(ready) { events += it }
        rule.onNodeWithTag("fl_log_crew_m1").performClick()
        rule.onNodeWithTag("fl_log_work_edit").performClick()
        rule.onNodeWithTag("fl_log_list").performScrollToNode(hasTestTag("fl_log_delay_remove_0"))
        rule.onNodeWithTag("fl_log_delay_remove_0").performClick()
        listOf("fl_log_delay_add", "fl_log_visitors_edit", "fl_log_safety_edit", "fl_log_client_edit").forEach {
            rule.onNodeWithTag("fl_log_list").performScrollToNode(hasTestTag(it))
            rule.onNodeWithTag(it).performClick()
            if (it == "fl_log_delay_add") {
                rule.onNodeWithTag("fl_log_list").performScrollToNode(hasTestTag("fl_log_weather_none"))
                rule.onNodeWithText("No weather (offline)").assertExists()
            }
        }
        assertThat(events).containsExactly(
            LogEvent.EditHours("m1"), LogEvent.EditText(LogTextSection.WORK_DONE), LogEvent.AskRemoveDelay(0), LogEvent.AddDelay,
            LogEvent.EditText(LogTextSection.VISITORS), LogEvent.EditText(LogTextSection.SAFETY), LogEvent.EditText(LogTextSection.CLIENT_NOTES),
        ).inOrder()
        rule.onNodeWithTag("fl_log_next").assertIsNotEnabled()
    }

    /** QA auto c1240620: the section pencils measured 40 dp when scrolled out of view; they are 48 dp buttons now. */
    @AcceptanceCriterion("AC-F30")
    @Test fun sectionEditButtonsAreFullTouchTargets() {
        show(ready)
        listOf("fl_log_work_edit", "fl_log_visitors_edit", "fl_log_safety_edit", "fl_log_client_edit").forEach {
            rule.onNodeWithTag("fl_log_list").performScrollToNode(hasTestTag(it))
            rule.onNodeWithTag(it).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun crewSeeTheLogWithNoEditControls() {
        show(ready.copy(canEdit = false, day = day.minusDays(1)))
        listOf("fl_log_work_edit", "fl_log_delay_remove_0", "fl_log_delay_add").forEach { rule.onNodeWithTag(it).assertDoesNotExist() }
        rule.onNodeWithTag("fl_log_next").assertIsEnabled()
    }

    @AcceptanceCriterion("AC-F32")
    @Test fun delaySheetOffersTheFiveReasonsAndSavesOnlyWithOne() {
        val events = mutableListOf<LogEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                LogDelaySheetContent(LogEditor.Delay(), { events += it })
            }
        }
        listOf("weather", "materials", "inspection", "client", "other").forEach { rule.onNodeWithTag("fl_log_delay_reason_$it").assertExists() }
        rule.onNodeWithTag("fl_log_delay_save").assertIsNotEnabled()
        rule.onNodeWithTag("fl_log_delay_reason_inspection").performClick()
        assertThat(events).containsExactly(LogEvent.EditorReason(DelayReason.INSPECTION))
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun leadCanLeaveRecordLinesOutPutThemBackAndCorrectTheWeather() {
        val events = mutableListOf<LogEvent>()
        show(
            ready.copy(
                photos = listOf(LogPhotoThumb("ph1", "")),
                tasksDone = listOf(LogLine("t1", "Level the sink base")),
                materials = listOf(LogLine("ma1", "Home Depot, Electrical", "$212.48")),
                dropped = mapOf(LogLineKind.TASK to 2),
                weather = LogWeather(highF = 61, lowF = 44, conditions = "showers"),
                weatherEdited = true,
                weatherStatus = WeatherStatus.Shown,
            ),
        ) { events += it }
        listOf("fl_log_photos_drop_ph1", "fl_log_tasks_drop_t1", "fl_log_tasks_put_back", "fl_log_materials_drop_ma1", "fl_log_weather_edit").forEach {
            rule.onNodeWithTag("fl_log_list").performScrollToNode(hasTestTag(it))
            rule.onNodeWithTag(it).performClick()
            if (it == "fl_log_tasks_put_back") rule.onNodeWithText("Put back 2 left out").assertExists()
        }
        rule.onNodeWithText("Typed in by hand", substring = true).assertExists()
        assertThat(events).containsExactly(
            LogEvent.AskDrop(LogLineKind.PHOTO, "ph1"), LogEvent.AskDrop(LogLineKind.TASK, "t1"), LogEvent.PutBack(LogLineKind.TASK),
            LogEvent.AskDrop(LogLineKind.MATERIAL, "ma1"), LogEvent.EditWeather,
        ).inOrder()
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun crewGetNoDropPutBackOrWeatherControls() {
        show(ready.copy(canEdit = false, tasksDone = listOf(LogLine("t1", "Level the sink base")), dropped = mapOf(LogLineKind.TASK to 1)))
        listOf("fl_log_tasks_drop_t1", "fl_log_tasks_put_back", "fl_log_weather_edit").forEach { rule.onNodeWithTag(it).assertDoesNotExist() }
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun weatherFormListsTheConditionsAndSaysWhatIsWrong() {
        val events = mutableListOf<LogWeatherEvent>()
        rule.setContent {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                LogWeatherScreen(LogWeatherUiState(day = day, loading = false, showErrors = true, draft = WeatherDraft(high = "48", low = "52")), { events += it })
            }
        }
        rule.onNodeWithTag("fl_log_weather_conditions_error").assertExists()
        rule.onNodeWithText("The low can\u2019t be above the high.").assertExists()
        rule.onNodeWithTag("fl_save").assertIsNotEnabled()
        rule.onNodeWithTag("fl_log_weather_lookup").assertDoesNotExist()
        rule.onNodeWithTag("fl_log_weather_cond_thunderstorm").performScrollTo().performClick()
        // typed changes: Close asks before discarding (rule 3), Discard closes
        rule.onNodeWithTag("fl_close").performClick()
        rule.onNodeWithTag("fl_discard").performClick()
        assertThat(events).containsExactly(LogWeatherEvent.Conditions("thunderstorm"), LogWeatherEvent.Close).inOrder()
    }
}
