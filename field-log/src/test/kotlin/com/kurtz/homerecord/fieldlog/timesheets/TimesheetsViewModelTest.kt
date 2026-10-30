package com.kurtz.homerecord.fieldlog.timesheets

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.punch.NewPunch
import com.kurtz.homerecord.core.punch.PunchRepository
import com.kurtz.homerecord.core.punch.TimesheetApprovals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// AC-F14: a week per person, edit needs a reason and keeps an audit entry, Approve person / all locks the week
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class TimesheetsViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private val created = mutableListOf<TimesheetsViewModel>()
    private val zone = ZoneId.of("UTC")
    private val monday = LocalDate.of(2026, 10, 19)
    private val now = monday.plusDays(3).atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
    private fun at(day: Int, h: Int) = monday.plusDays(day.toLong()).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking {
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Garage subpanel", createdAt = 1, updatedAt = 1))
            db.jobDao().upsert(JobEntity(id = "j2", companyId = "c1", name = "Kitchen", createdAt = 2, updatedAt = 2))
            listOf("m1" to "Dana Whitfield", "m2" to "Sam Rivera", "o1" to "Nate Kurtz").forEach { (id, name) ->
                db.memberDao().upsert(MemberEntity(id = id, companyId = "c1", name = name, createdAt = 1, updatedAt = 1))
            }
        }
    }

    @After fun tearDown() {
        runBlocking { created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private val settings = kotlinx.coroutines.flow.MutableStateFlow(CompanySettings())

    private val repo get() = PunchRepository(db, { now }, zone = { zone })

    private suspend fun shift(who: String, day: Int, from: Int, to: Int) {
        repo.record(NewPunch("c1", who, "j1", PunchKind.IN, at = at(day, from)))
        repo.record(NewPunch("c1", who, "j1", PunchKind.OUT, at = at(day, to)))
    }

    private suspend fun vm(saved: SavedStateHandle = SavedStateHandle()): TimesheetsViewModel {
        val deps = TimesheetsDeps(
            "o1", { "Nate Kurtz" }, repo, TimesheetApprovals(db, { zone }, { now }),
            db.memberDao().observeFor("c1"), db.punchDao().observeFor("c1"), db.jobDao().observeFor("c1"),
            db.timesheetApprovalDao().observeAll(), settings, zone, { now },
        )
        return TimesheetsViewModel(deps, saved).also { created += it; await(it) { s -> !s.loading } }
    }

    private suspend fun await(vm: TimesheetsViewModel, test: (TimesheetsUiState) -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first(test) } }

    @Test fun showsThisWeekPerPerson() = runBlocking<Unit> {
        shift("m1", 0, 8, 16); shift("m2", 1, 9, 13)
        val s = vm().state.value
        assertThat(s.weekStart).isEqualTo(monday)
        assertThat(s.people.map { it.name }).containsExactly("Dana Whitfield", "Sam Rivera").inOrder()
        assertThat(s.approvable).isEqualTo(2)
    }

    // AC-F12 (cr:fl-settings-source): an overtime rule changed after the screen opened recomputes the week
    @Test fun overtimeRuleChangeRecomputesTheWeek() = runBlocking<Unit> {
        shift("m1", 0, 8, 18)
        val vm = vm()
        assertThat(vm.state.value.people.single().sheet.overtimeMillis).isEqualTo(0)
        settings.value = CompanySettings(com.kurtz.homerecord.core.company.OvertimeRule.Daily8)
        val s = await(vm) { it.people.single().sheet.overtimeMillis > 0 }
        assertThat(s.people.single().sheet.overtimeMillis).isEqualTo(2 * 3_600_000L)
    }

    @Test fun previousWeekIsEmptyAndNextIsBlockedAtThisWeek() = runBlocking<Unit> {
        shift("m1", 0, 8, 16)
        val vm = vm()
        vm.onEvent(TimesheetsEvent.PreviousWeek)
        val s = await(vm) { it.weekStart == monday.minusWeeks(1) }
        assertThat(s.people).isEmpty()
        vm.onEvent(TimesheetsEvent.NextWeek)
        assertThat(await(vm) { it.weekStart == monday }.isCurrentWeek).isTrue()
        vm.onEvent(TimesheetsEvent.NextWeek)
        assertThat(vm.state.value.weekStart).isEqualTo(monday)
    }

    @Test fun editNeedsAReasonAndKeepsAnAuditEntry() = runBlocking<Unit> {
        shift("m1", 0, 8, 16)
        val vm = vm()
        val inId = vm.state.value.people.single().punches.first { it.kind == PunchKind.IN }.id
        vm.onEvent(TimesheetsEvent.EditPunch(inId))
        assertThat(vm.state.value.edit?.time).isEqualTo(LocalTime.of(8, 0))
        vm.onEvent(TimesheetsEvent.EditTime(LocalTime.of(7, 30)))
        vm.onEvent(TimesheetsEvent.SaveEdit)
        assertThat(vm.state.value.edit?.error).isEqualTo(EditError.ReasonRequired)
        vm.onEvent(TimesheetsEvent.EditReason("Forgot to clock in"))
        vm.onEvent(TimesheetsEvent.SaveEdit)
        val s = await(vm) { it.edit == null && it.people.singleOrNull()?.punches?.any { p -> p.edited } == true }
        val row = s.people.single().punches.first { it.id == inId }
        assertThat(row.at).isEqualTo(monday.atTime(7, 30).atZone(zone).toInstant().toEpochMilli())
        assertThat(row.edits.single().by).isEqualTo("o1")
        assertThat(row.edits.single().byName).isEqualTo("Nate Kurtz")
        assertThat(row.edits.single().reason).isEqualTo("Forgot to clock in")
    }

    // AC-F14, ui-standards rules 3 and 15: Close asks first only once something changed
    @Test fun editFormIsDirtyOnlyAfterAChange() = runBlocking<Unit> {
        shift("m1", 0, 8, 16)
        val vm = vm()
        vm.onEvent(TimesheetsEvent.EditPunch(vm.state.value.people.single().punches.first().id))
        assertThat(vm.state.value.edit?.dirty).isFalse()
        vm.onEvent(TimesheetsEvent.EditJob("j2"))
        assertThat(vm.state.value.edit?.dirty).isTrue()
        vm.onEvent(TimesheetsEvent.EditJob("j1"))
        assertThat(vm.state.value.edit?.dirty).isFalse()
        vm.onEvent(TimesheetsEvent.EditTime(LocalTime.of(7, 45)))
        assertThat(vm.state.value.edit?.dirty).isTrue()
        vm.onEvent(TimesheetsEvent.CancelEdit)
        assertThat(vm.state.value.edit).isNull()
    }

    // AC-F14: the open Edit punch form, with the picked time, job and typed reason, survives process death
    @Test fun editFormSurvivesProcessDeath() = runBlocking<Unit> {
        shift("m1", 0, 8, 16)
        val saved = SavedStateHandle()
        val first = vm(saved)
        val inId = first.state.value.people.single().punches.first { it.kind == PunchKind.IN }.id
        first.onEvent(TimesheetsEvent.EditPunch(inId))
        first.onEvent(TimesheetsEvent.EditTime(LocalTime.of(7, 15)))
        first.onEvent(TimesheetsEvent.EditJob("j2"))
        first.onEvent(TimesheetsEvent.EditReason("Started at the supply house"))
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val again = vm(restored)
        val e = await(again) { it.edit != null }.edit
        assertThat(e?.punchId).isEqualTo(inId)
        assertThat(e?.time).isEqualTo(LocalTime.of(7, 15))
        assertThat(e?.jobId).isEqualTo("j2")
        assertThat(e?.reason).isEqualTo("Started at the supply house")
        assertThat(e?.originalTime).isEqualTo(LocalTime.of(8, 0))
        again.onEvent(TimesheetsEvent.CancelEdit)
        val closed = SavedStateHandle(restored.keys().associateWith { restored.get<Any?>(it) })
        assertThat(vm(closed).state.value.edit).isNull()
    }

    @Test fun approveLocksThePersonsWeekAndEditsAreRefused() = runBlocking<Unit> {
        shift("m1", 0, 8, 16); shift("m2", 0, 8, 12)
        val vm = vm()
        vm.onEvent(TimesheetsEvent.Approve("m1"))
        val s = await(vm) { st -> st.people.firstOrNull { it.memberId == "m1" }?.approved == true }
        assertThat(s.people.first { it.memberId == "m2" }.approved).isFalse()
        vm.onEvent(TimesheetsEvent.EditPunch(s.people.first { it.memberId == "m1" }.punches.first().id))
        assertThat(vm.state.value.edit).isNull()
        assertThat(db.punchDao().listFor("c1").all { it.editsJson == "[]" }).isTrue()
    }

    @Test fun approveAllAsksFirstThenApprovesEveryoneWithPunches() = runBlocking<Unit> {
        shift("m1", 0, 8, 16); shift("m2", 1, 9, 13)
        val vm = vm()
        vm.onEvent(TimesheetsEvent.AskApproveAll)
        assertThat(vm.state.value.confirmApproveAll).isTrue()
        vm.onEvent(TimesheetsEvent.ApproveAll)
        val s = await(vm) { st -> st.people.size == 2 && st.people.all { it.approved } }
        assertThat(s.confirmApproveAll).isFalse()
        assertThat(s.approvable).isEqualTo(0)
    }

    @Test fun expandedPersonSurvivesProcessDeath() = runBlocking<Unit> {
        shift("m1", 0, 8, 16)
        val handle = SavedStateHandle()
        val first = TimesheetsViewModel(depsFor(), handle).also { created += it }
        first.onEvent(TimesheetsEvent.Toggle("m1"))
        val second = TimesheetsViewModel(depsFor(), handle).also { created += it }
        assertThat(second.state.value.expandedId).isEqualTo("m1")
    }

    private fun depsFor() = TimesheetsDeps(
        "o1", { "Nate Kurtz" }, repo, TimesheetApprovals(db, { zone }, { now }),
        db.memberDao().observeFor("c1"), db.punchDao().observeFor("c1"), db.jobDao().observeFor("c1"),
        db.timesheetApprovalDao().observeAll(), settings, zone, { now },
    )
}
