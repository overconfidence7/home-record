package com.kurtz.homerecord.fieldlog.timesheets

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.model.ShiftFlag
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.clock.SheetDay
import com.kurtz.homerecord.fieldlog.clock.WeekSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

// AC-F14, ui-standards rules 7 and 13 (judge:fl-timesheets-approve-buttons): a collapsed person is one list row with
// no button under it; Approve <name> shows only inside the opened row, and never for an approved week.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TimesheetsApproveRowTest {
    @get:Rule val rule = createComposeRule()

    private val monday = LocalDate.of(2026, 10, 19)
    private val hour = 3_600_000L
    private fun sheet(total: Long) = WeekSheet((0..6).map { SheetDay(monday.plusDays(it.toLong()), if (it == 0) total else 0L, emptySet()) }, total, 0, 0)
    private fun punch(id: String) = PunchRow(id, "IN", 1_760_000_000_000L, "j1", "Garage subpanel", false)

    private val people = listOf(
        PersonWeek("m1", "Dana Whitfield", sheet(8 * hour), emptySet(), approved = true, punches = listOf(punch("p1"))),
        PersonWeek("m3", "Marcus Ortiz", sheet(10 * hour), setOf(ShiftFlag.NoBreak), approved = false, punches = listOf(punch("p3"))),
        PersonWeek("m2", "Sam Rivera", sheet(4 * hour), emptySet(), approved = false, punches = listOf(punch("p5"))),
    )

    private fun screen(open: String?, onEvent: (TimesheetsEvent) -> Unit = {}) = rule.setContent {
        CompositionLocalProvider(LocalTagPrefix provides "fl") {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                TimesheetsScreen(
                    TimesheetsUiState(loading = false, weekStart = monday, people = people, expandedId = open, isCurrentWeek = true),
                    onEvent, onBack = {}, zone = ZoneId.of("UTC"),
                )
            }
        }
    }

    @Test fun collapsedRowsHaveNoApproveButtons() {
        screen(open = null)
        listOf("m1", "m2", "m3").forEach { rule.onNodeWithTag("fl_timesheets_approve_$it").assertDoesNotExist() }
        rule.onNodeWithTag("fl_timesheets_approve_all").assertExists()
    }

    @Test fun openedRowOffersApproveForThatPersonOnly() {
        val events = mutableListOf<TimesheetsEvent>()
        screen(open = "m3") { events += it }
        rule.onNodeWithTag("fl_timesheets_approve_m2").assertDoesNotExist()
        rule.onNodeWithTag("fl_timesheets_approve_m3").performScrollTo().performClick()
        assertThat(events).containsExactly(TimesheetsEvent.Approve("m3"))
    }

    @Test fun approvedWeekShowsNoApproveEvenWhenOpen() {
        screen(open = "m1")
        rule.onNodeWithTag("fl_timesheets_approve_m1").assertDoesNotExist()
    }
}
