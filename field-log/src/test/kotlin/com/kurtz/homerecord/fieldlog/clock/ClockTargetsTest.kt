package com.kurtz.homerecord.fieldlog.clock

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.ClockBreakPreview
import com.kurtz.homerecord.fieldlog.ClockExplainPreview
import com.kurtz.homerecord.fieldlog.ClockGpsRequiredPreview
import com.kurtz.homerecord.fieldlog.ClockOutPreview
import com.kurtz.homerecord.fieldlog.ClockWorkingPreview
import com.kurtz.homerecord.fieldlog.TodayCrewOutPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-U8 and ui-standards rule 30: Clock in and Clock out are at least 64 dp tall, every other clock target at least 56 dp.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class ClockTargetsTest {
    @get:Rule val rule = createComposeRule()

    private fun atLeast(content: @Composable () -> Unit, min: Int, vararg tags: String) {
        rule.setContent(content)
        tags.forEach { rule.onNodeWithTag(it).assertHeightIsAtLeast(min.dp) }
    }

    @Test fun clockInIs64() = atLeast({ ClockOutPreview() }, 64, "fl_clock_in")

    @Test fun workingButtonsAre64() = atLeast({ ClockWorkingPreview() }, 64, "fl_clock_break", "fl_clock_switch", "fl_clock_out")

    @Test fun breakButtonsAre64() = atLeast({ ClockBreakPreview() }, 64, "fl_clock_break_end", "fl_clock_out")

    @Test fun todayClockActionIs64() = atLeast({ TodayCrewOutPreview() }, 64, "fl_today_clock_action")

    @Test fun explainDialogButtonsAre56() = atLeast({ ClockExplainPreview() }, 56, "fl_clock_explain_ok", "fl_clock_explain_skip")

    // A picked reason rather than Other: the Other text field never idles under Robolectric.
    @Test fun farReasonSaveIs56() = atLeast(
        {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                ClockScreen(ClockUiState(loading = false, farReason = FarReasonSheet(1840, FarReason.Shop)), {})
            }
        },
        56, "fl_clock_far_save",
    )

    @Test fun gpsSettingsIs56() = atLeast({ ClockGpsRequiredPreview() }, 56, "fl_clock_gps_settings")
}
