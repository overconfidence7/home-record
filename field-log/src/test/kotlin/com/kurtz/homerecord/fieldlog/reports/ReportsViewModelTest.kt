package com.kurtz.homerecord.fieldlog.reports

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.punch.TimeExportFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

// AC-F46: hours by person and by job over a week range; AC-F15: exports go to the share sheet
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {
    private val zone = ZoneId.of("UTC")
    private val monday = LocalDate.of(2026, 10, 19)
    private val now = monday.plusDays(3).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
    private fun at(week: Long, day: Long, h: Int) = monday.minusWeeks(week).plusDays(day).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private val shared = mutableListOf<TimeExportFile>()
    private var shareFails = false

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun punch(id: String, who: String, kind: String, at: Long) =
        PunchEntity(id = id, companyId = "c1", memberId = who, jobId = "j1", kind = kind, at = at, createdAt = at, updatedAt = at)

    private fun vm(saved: SavedStateHandle = SavedStateHandle(), empty: Boolean = false): ReportsViewModel {
        val punches = if (empty) emptyList() else listOf(
            punch("a", "m1", PunchKind.IN, at(0, 0, 8)), punch("b", "m1", PunchKind.OUT, at(0, 0, 16)),
            punch("c", "m1", PunchKind.IN, at(1, 1, 9)), punch("d", "m1", PunchKind.OUT, at(1, 1, 13)),
        )
        val deps = ReportsDeps(
            members = flowOf(listOf(MemberEntity(id = "m1", companyId = "c1", name = "Dana Whitfield", createdAt = 1, updatedAt = 1))),
            punches = flowOf(punches),
            jobs = flowOf(listOf(JobEntity(id = "j1", companyId = "c1", name = "Garage subpanel", createdAt = 1, updatedAt = 1))),
            clients = flowOf(emptyList()),
            settings = { CompanySettings() },
            share = { if (shareFails) throw IOException("disk") else shared += it },
            zone = zone, now = { now },
        )
        return ReportsViewModel(deps, saved)
    }

    @Test fun thisWeekTotalsByPersonAndJob() {
        val s = vm().state.value
        assertThat(s.loading).isFalse()
        assertThat(s.from).isEqualTo(monday)
        assertThat(s.byPerson.map { it.name to it.workedMillis }).containsExactly("Dana Whitfield" to 8 * 3_600_000L)
        assertThat(s.byJob.map { it.name }).containsExactly("Garage subpanel")
        assertThat(s.totalMillis).isEqualTo(8 * 3_600_000L)
    }

    @Test fun lastFourWeeksAddsEarlierHoursAndIsRemembered() {
        val saved = SavedStateHandle()
        val vm = vm(saved)
        vm.onEvent(ReportsEvent.SelectRange(ReportRange.LastFourWeeks))
        assertThat(vm.state.value.totalMillis).isEqualTo(4 * 3_600_000L) // last 4 weeks ends last week
        assertThat(vm.state.value.from).isEqualTo(monday.minusWeeks(4))
        assertThat(vm(saved).state.value.range).isEqualTo(ReportRange.LastFourWeeks)
    }

    @Test fun exportsShareAFileAndFailureShowsANotice() {
        val vm = vm()
        vm.onEvent(ReportsEvent.ExportShifts)
        vm.onEvent(ReportsEvent.ExportQuickBooks)
        assertThat(shared.map { it.name }).containsExactly(
            "shifts-$monday-to-${monday.plusDays(6)}.csv", "quickbooks-timesheet-$monday-to-${monday.plusDays(6)}.csv",
        ).inOrder()
        assertThat(vm.state.value.notice).isEqualTo(ExportNotice.Shared)
        shareFails = true
        vm.onEvent(ReportsEvent.ExportShifts)
        assertThat(vm.state.value.notice).isEqualTo(ExportNotice.Failed)
        assertThat(vm.state.value.exporting).isFalse()
    }

    @Test fun emptyRangeRefusesToExport() {
        val vm = vm(empty = true)
        assertThat(vm.state.value.isEmpty).isTrue()
        vm.onEvent(ReportsEvent.ExportShifts)
        assertThat(shared).isEmpty()
        assertThat(vm.state.value.notice).isEqualTo(ExportNotice.Empty)
        vm.onEvent(ReportsEvent.DismissNotice)
        assertThat(vm.state.value.notice).isNull()
    }
}
