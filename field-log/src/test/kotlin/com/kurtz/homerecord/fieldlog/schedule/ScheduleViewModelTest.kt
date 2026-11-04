package com.kurtz.homerecord.fieldlog.schedule

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.model.ScheduleEntry
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

// AC-F41 (week view, assign by picker, move, crew see their own week), AC-F42 (a schedule push opens its day), AC-F43 (conflict badge)
@AcceptanceCriterion("AC-F41", "AC-F42", "AC-F43")
@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {
    private val wed = LocalDate.of(2026, 11, 4)
    private val noon = wed.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val members = MutableStateFlow(
        listOf(
            MemberEntity("m1", "c1", name = "Dana Ortiz", role = "OWNER", createdAt = 1, updatedAt = 1),
            MemberEntity("m2", "c1", name = "Luis Romero", createdAt = 2, updatedAt = 2),
        ),
    )
    private val jobs = MutableStateFlow(
        listOf(
            JobEntity(id = "j1", companyId = "c1", name = "Smith deck", createdAt = 1, updatedAt = 1),
            JobEntity(id = "j2", companyId = "c1", name = "Hale kitchen", createdAt = 1, updatedAt = 1),
        ),
    )
    private val entries = MutableStateFlow(listOf(ScheduleEntry("a1", "j1", "m2", wed)))
    private val gates = MutableStateFlow(RoleGates(CompanyRole.Owner, selfId = "m1"))
    private val openDay = MutableStateFlow<LocalDate?>(null)
    private val calls = mutableListOf<String>()

    private var clock = noon
    private val ticks = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun vm(saved: SavedStateHandle = SavedStateHandle(), me: String = "m1", open: MutableStateFlow<LocalDate?> = MutableStateFlow(null)) = ScheduleViewModel(
        ScheduleDeps(
            me, members, jobs, entries, gates,
            assign = { j, m, d -> calls += "assign $j $m $d"; "new" },
            move = { id, d -> calls += "move $id $d"; true },
            remove = { id -> calls += "remove $id"; true },
            openDay = open, consumeOpenDay = { open.value = null },
            zone = ZoneOffset.UTC, now = { clock }, ticks = ticks,
        ),
        saved,
    )

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun ownerSeesEveryoneAndAssignsByPicker() {
        val vm = vm()
        val s = vm.state.value
        assertThat(s.weekStart).isEqualTo(LocalDate.of(2026, 11, 2))
        assertThat(s.selected).isEqualTo(wed)
        assertThat(s.rows.map { it.name }).containsExactly("Dana Ortiz", "Luis Romero").inOrder()
        assertThat(s.jobs.map { it.name }).containsExactly("Hale kitchen", "Smith deck").inOrder()
        vm.onEvent(ScheduleEvent.SelectDay(wed.plusDays(1)))
        vm.onEvent(ScheduleEvent.Assign("m2"))
        assertThat(vm.state.value.sheet).isEqualTo(ScheduleSheet.PickJob("m2", "Luis Romero", wed.plusDays(1)))
        vm.onEvent(ScheduleEvent.PickJob("j2"))
        assertThat(calls).containsExactly("assign j2 m2 2026-11-05")
        assertThat(vm.state.value.sheet).isNull()
    }

    @Test fun aScheduledDayMovesOrComesOff() {
        val vm = vm()
        vm.onEvent(ScheduleEvent.OpenDay("m2", "a1"))
        assertThat((vm.state.value.sheet as ScheduleSheet.Day).job.jobName).isEqualTo("Smith deck")
        vm.onEvent(ScheduleEvent.MoveTo(wed.plusDays(2)))
        vm.onEvent(ScheduleEvent.OpenDay("m2", "a1"))
        vm.onEvent(ScheduleEvent.Remove)
        assertThat(calls).containsExactly("move a1 2026-11-06", "remove a1").inOrder()
    }

    @Test fun aDayOnAJobNowDoneCanStillComeOff() {
        // R11: the job finished early; it is no longer offered for new days but its scheduled day can be taken off.
        jobs.value = jobs.value.map { if (it.id == "j1") it.copy(status = "DONE") else it }
        val vm = vm()
        assertThat(vm.state.value.jobs.map { it.id }).containsExactly("j2")
        assertThat(vm.state.value.canChange("j1")).isTrue()
        vm.onEvent(ScheduleEvent.OpenDay("m2", "a1"))
        vm.onEvent(ScheduleEvent.Remove)
        assertThat(calls).containsExactly("remove a1")
    }

    @Test fun leftOpenPastMidnightTheScreenMovesToTheNewDay() {
        // R11: Sunday 23:59 → Monday 00:01: today, the selected day and This week follow the clock.
        val sun = LocalDate.of(2026, 11, 8)
        clock = sun.atTime(23, 59).toInstant(ZoneOffset.UTC).toEpochMilli()
        val vm = vm()
        assertThat(vm.state.value.selected).isEqualTo(sun)
        clock = sun.plusDays(1).atTime(0, 1).toInstant(ZoneOffset.UTC).toEpochMilli()
        ticks.tryEmit(Unit)
        val s = vm.state.value
        assertThat(s.today).isEqualTo(sun.plusDays(1))
        assertThat(s.selected).isEqualTo(sun.plusDays(1))
        assertThat(s.weekStart).isEqualTo(sun.plusDays(1))
        assertThat(s.isThisWeek).isTrue()
    }

    @Test fun aPickedDayStaysPickedWhenTheDayChanges() {
        val vm = vm()
        vm.onEvent(ScheduleEvent.SelectDay(wed.plusDays(2)))
        clock = wed.plusDays(1).atTime(0, 1).toInstant(ZoneOffset.UTC).toEpochMilli()
        ticks.tryEmit(Unit)
        assertThat(vm.state.value.today).isEqualTo(wed.plusDays(1))
        assertThat(vm.state.value.selected).isEqualTo(wed.plusDays(2))
    }

    @Test fun twoJobsTheSameDayShowAConflict() {
        entries.value = entries.value + ScheduleEntry("a2", "j2", "m2", wed)
        val vm = vm()
        assertThat(vm.state.value.conflict(wed)).isTrue()
        assertThat(vm.state.value.rows[1].conflicts).isEqualTo(1)
    }

    @Test fun crewSeeTheirOwnWeekReadOnly() {
        gates.value = RoleGates(CompanyRole.Crew, selfId = "m2")
        val vm = vm(me = "m2")
        val s = vm.state.value
        assertThat(s.ownOnly).isTrue()
        assertThat(s.rows.map { it.memberId }).containsExactly("m2")
        assertThat(s.canAssign).isFalse()
        vm.onEvent(ScheduleEvent.OpenDay("m2", "a1"))
        vm.onEvent(ScheduleEvent.Assign("m2"))
        assertThat(s.sheet).isNull()
        assertThat(vm.state.value.sheet).isNull()
    }

    @Test fun leadSchedulesOnlyTheJobsTheyRun() {
        gates.value = RoleGates(CompanyRole.Lead, leadJobs = setOf("j1"), selfId = "m2")
        val vm = vm(me = "m2")
        assertThat(vm.state.value.jobs.map { it.id }).containsExactly("j1")
        assertThat(vm.state.value.canChange("j2")).isFalse()
    }

    @Test fun weekAndDaySurviveProcessDeathAndAPushOpensItsDay() {
        val saved = SavedStateHandle()
        val first = vm(saved)
        first.onEvent(ScheduleEvent.NextWeek)
        assertThat(first.state.value.weekStart).isEqualTo(LocalDate.of(2026, 11, 9))
        assertThat(first.state.value.selected).isEqualTo(LocalDate.of(2026, 11, 11))
        assertThat(first.state.value.isThisWeek).isFalse()
        val again = vm(saved, open = openDay)
        assertThat(again.state.value.selected).isEqualTo(LocalDate.of(2026, 11, 11))
        openDay.value = wed.plusDays(1)
        assertThat(again.state.value.weekStart).isEqualTo(LocalDate.of(2026, 11, 2))
        assertThat(again.state.value.selected).isEqualTo(wed.plusDays(1))
        assertThat(openDay.value).isNull()
        again.onEvent(ScheduleEvent.ThisWeek)
        assertThat(again.state.value.selected).isEqualTo(wed)
    }
}
