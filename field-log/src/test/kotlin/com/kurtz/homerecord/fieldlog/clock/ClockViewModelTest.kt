package com.kurtz.homerecord.fieldlog.clock

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.punch.CrewClock
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.location.LocationProvider
import com.kurtz.homerecord.core.model.GeoFix
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.punch.ClockController
import com.kurtz.homerecord.core.punch.ClockStatus
import com.kurtz.homerecord.core.punch.PunchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emptyFlow
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

private class FakeLocation(var permitted: Boolean = true, var fix: GeoFix? = GeoFix(34.0, -118.0, 5f)) : LocationProvider {
    override fun hasPermission() = permitted
    override suspend fun currentFix(timeoutMs: Long): GeoFix? = if (permitted) fix else null
}

// AC-F11 (lead crew clock-in), AC-F8 (clock in with job), AC-F9 (far reason), AC-F10 (break, switch, out), AC-F18 (explanation once, no-location and require-GPS paths)
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ClockViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private val created = mutableListOf<ClockViewModel>()
    private val location = FakeLocation()
    private var asked = false
    private val settings = kotlinx.coroutines.flow.MutableStateFlow(CompanySettings())
    private var clock = 5_000_000L

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking {
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Garage subpanel", lat = 34.0, lng = -118.0, createdAt = 1, updatedAt = 1))
            db.jobDao().upsert(JobEntity(id = "j2", companyId = "c1", name = "Kitchen", lat = 34.0, lng = -118.0, createdAt = 2, updatedAt = 2))
            db.jobDao().upsert(JobEntity(id = "j3", companyId = "c1", name = "Done job", status = "DONE", createdAt = 3, updatedAt = 3))
        }
    }

    @After fun tearDown() {
        runBlocking { created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun vm(role: CompanyRole = CompanyRole.Crew, approvals: kotlinx.coroutines.flow.Flow<List<com.kurtz.homerecord.core.data.TimesheetApprovalEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())): ClockViewModel {
        val punches = PunchRepository(db, { clock })
        val deps = ClockDeps(
            "c1", "m1", ClockController(punches, location), punches, db.jobDao(), location, settings,
            { asked }, { asked = true }, now = { clock }, ticks = emptyFlow(),
            role = role, crewClock = CrewClock(punches, ClockController(punches, location), location), members = db.memberDao().observeFor("c1"), approvals = approvals,
        )
        return ClockViewModel(deps, SavedStateHandle()).also { created += it; await(it) { s -> !s.loading } }
    }

    private suspend fun await(vm: ClockViewModel, test: (ClockUiState) -> Boolean): ClockUiState =
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first(test) } }

    private suspend fun kinds() = db.punchDao().listFor("c1").sortedBy { it.at }.map { it.kind }

    // AC-F14: crew sees Approved once the week is approved
    @Test fun showsApprovedWhenThisWeekIsApproved() = runBlocking<Unit> {
        assertThat(vm().state.value.weekApproved).isFalse()
        val week = com.kurtz.homerecord.core.punch.WeekKey.of(clock, java.time.ZoneId.systemDefault())
        val row = com.kurtz.homerecord.core.data.TimesheetApprovalEntity("a1", "m1", week, "boss", 1, 1, 1)
        assertThat(vm(approvals = kotlinx.coroutines.flow.flowOf(listOf(row))).state.value.weekApproved).isTrue()
    }

    @Test fun listsActiveJobsAndClockInNeedsOne() = runBlocking<Unit> {
        val vm = vm()
        assertThat(vm.state.value.jobs.map { it.id }).containsExactly("j1", "j2")
        vm.onEvent(ClockEvent.ClockIn)
        assertThat(kinds()).isEmpty()
        vm.onEvent(ClockEvent.SelectJob("j1"))
        vm.onEvent(ClockEvent.ClockIn)
        await(vm) { it.status is ClockStatus.Working }
        assertThat(kinds()).containsExactly(PunchKind.IN)
    }

    // AC-F17: the widget opens FL-Clock with the last job preselected
    @Test fun preselectsTheLastJobWorked() = runBlocking<Unit> {
        val first = vm()
        first.onEvent(ClockEvent.SelectJob("j2")); first.onEvent(ClockEvent.ClockIn)
        await(first) { it.status is ClockStatus.Working }
        clock += 1000; first.onEvent(ClockEvent.ClockOut)
        await(first) { it.status is ClockStatus.Out }
        assertThat(vm().state.value.selectedJobId).isEqualTo("j2")
    }

    @Test fun breakSwitchAndClockOut() = runBlocking<Unit> {
        val vm = vm()
        vm.onEvent(ClockEvent.SelectJob("j1")); vm.onEvent(ClockEvent.ClockIn)
        await(vm) { it.status is ClockStatus.Working }
        clock += 1000; vm.onEvent(ClockEvent.StartBreak)
        await(vm) { it.status is ClockStatus.OnBreak }
        clock += 1000; vm.onEvent(ClockEvent.EndBreak)
        await(vm) { it.status is ClockStatus.Working }
        clock += 1000; vm.onEvent(ClockEvent.SelectJob("j2")); vm.onEvent(ClockEvent.SwitchJob)
        await(vm) { (it.status as? ClockStatus.Working)?.jobId == "j2" }
        clock += 1000; vm.onEvent(ClockEvent.ClockOut)
        await(vm) { it.status is ClockStatus.Out }
        assertThat(kinds()).containsExactly(PunchKind.IN, PunchKind.BREAK_START, PunchKind.BREAK_END, PunchKind.SWITCH, PunchKind.OUT).inOrder()
    }

    @Test fun explainsLocationOnFirstClockInOnly() = runBlocking<Unit> {
        location.permitted = false
        val vm = vm()
        vm.onEvent(ClockEvent.SelectJob("j1")); vm.onEvent(ClockEvent.ClockIn)
        assertThat(vm.state.value.explainLocation).isTrue()
        assertThat(kinds()).isEmpty()
        vm.onEvent(ClockEvent.ExplainContinue)
        assertThat(vm.state.value.requestPermission).isTrue()
        vm.onEvent(ClockEvent.PermissionResult(granted = false))
        val s = await(vm) { it.status is ClockStatus.Working && it.savedWithoutLocation }
        assertThat(s.savedWithoutLocation).isTrue()
        assertThat(db.punchDao().listFor("c1").single().note).contains("No location")
        vm.onEvent(ClockEvent.ClockOut)
        await(vm) { it.status is ClockStatus.Out }
        vm.onEvent(ClockEvent.ClockIn)
        assertThat(vm.state.value.explainLocation).isFalse()
    }

    @Test fun requireGpsWithoutPermissionOffersSettings() = runBlocking<Unit> {
        settings.value = CompanySettings(requireGps = true)
        location.permitted = false; asked = true
        val vm = vm()
        vm.onEvent(ClockEvent.SelectJob("j1")); vm.onEvent(ClockEvent.ClockIn)
        assertThat(await(vm) { it.gpsRequired != null }.gpsRequired).isTrue()
        assertThat(kinds()).isEmpty()
        vm.onEvent(ClockEvent.DismissGps)
        assertThat(vm.state.value.gpsRequired).isNull()
    }

    // AC-F18 (cr:fl-settings-source): Require GPS turned on by the office while Clock is open applies to the next punch
    @Test fun requireGpsSetAfterOpeningApplies() = runBlocking<Unit> {
        location.permitted = false; asked = true
        val vm = vm()
        settings.value = CompanySettings(requireGps = true)
        vm.onEvent(ClockEvent.SelectJob("j1")); vm.onEvent(ClockEvent.ClockIn)
        assertThat(await(vm) { it.gpsRequired != null }.gpsRequired).isTrue()
        assertThat(kinds()).isEmpty()
    }

    // AC-F18: allowing location in Settings from the require-GPS dialog finishes the clock-in on return
    @Test fun requireGpsRetriesTheClockInAfterSettings() = runBlocking<Unit> {
        settings.value = CompanySettings(requireGps = true)
        location.permitted = false; asked = true
        val vm = vm()
        vm.onEvent(ClockEvent.SelectJob("j1")); vm.onEvent(ClockEvent.ClockIn)
        await(vm) { it.gpsRequired == true }
        vm.onEvent(ClockEvent.OpenSettings)
        assertThat(vm.state.value.gpsRequired).isNull()
        location.permitted = true
        vm.onEvent(ClockEvent.Resumed)
        await(vm) { it.status is ClockStatus.Working }
        assertThat(kinds()).containsExactly(PunchKind.IN)
        vm.onEvent(ClockEvent.ClockOut)
        await(vm) { it.status is ClockStatus.Out }
        vm.onEvent(ClockEvent.Resumed) // a later return from anywhere punches nothing
        assertThat(kinds()).containsExactly(PunchKind.IN, PunchKind.OUT).inOrder()
    }

    // AC-F18: back from Settings still without location, nothing is punched and nothing is retried later
    @Test fun requireGpsStillDeniedAfterSettingsPunchesNothing() = runBlocking<Unit> {
        settings.value = CompanySettings(requireGps = true)
        location.permitted = false; asked = true
        val vm = vm()
        vm.onEvent(ClockEvent.SelectJob("j1")); vm.onEvent(ClockEvent.ClockIn)
        await(vm) { it.gpsRequired == true }
        vm.onEvent(ClockEvent.OpenSettings)
        vm.onEvent(ClockEvent.Resumed)
        location.permitted = true
        vm.onEvent(ClockEvent.Resumed)
        assertThat(kinds()).isEmpty()
        assertThat(vm.state.value.status).isEqualTo(ClockStatus.Out)
    }

    @Test fun farFromJobAsksForAReasonThenSaves() = runBlocking<Unit> {
        location.fix = GeoFix(34.1, -118.0, 5f) // about 7 miles away
        val vm = vm()
        vm.onEvent(ClockEvent.SelectJob("j1")); vm.onEvent(ClockEvent.ClockIn)
        val sheet = await(vm) { it.farReason != null }.farReason
        assertThat(sheet?.canSave).isFalse()
        vm.onEvent(ClockEvent.PickReason("Supply house"))
        vm.onEvent(ClockEvent.SaveReason)
        await(vm) { it.status is ClockStatus.Working }
        val p = db.punchDao().listFor("c1").single()
        assertThat(p.farFromJob).isTrue()
        assertThat(p.reason).isEqualTo("Supply house")
    }

    @Test fun otherReasonNeedsText() {
        assertThat(FarReasonSheet(900, FarReasonSheet.OTHER, "  ").canSave).isFalse()
        assertThat(FarReasonSheet(900, FarReasonSheet.OTHER, " Dropping off a ladder ").reason).isEqualTo("Dropping off a ladder")
    }

    private suspend fun addMember(id: String, name: String) =
        db.memberDao().upsert(MemberEntity(id = id, companyId = "c1", userId = id, name = name, createdAt = 1, updatedAt = 1))

    // AC-F11: a lead ticks people, one tap punches each with clockedInBy; people already in are shown but not pickable
    @Test fun leadClocksInSelectedCrewOnTheSelectedJob() = runBlocking<Unit> {
        addMember("m1", "Lead"); addMember("m2", "Maria"); addMember("m3", "Dev")
        val vm = vm(CompanyRole.Lead)
        val s0 = await(vm) { it.crew.size == 2 }
        assertThat(s0.canLeadCrew).isTrue()
        assertThat(s0.crew.map { it.id }).containsExactly("m2", "m3")
        vm.onEvent(ClockEvent.ToggleCrew("m2")); vm.onEvent(ClockEvent.ToggleCrew("m3"))
        vm.onEvent(ClockEvent.SelectJob("j1"))
        vm.onEvent(ClockEvent.ClockInCrew)
        val s1 = await(vm) { it.crewNotice != null }
        assertThat(s1.crewNotice?.punched).isEqualTo(2)
        assertThat(s1.crewSelected).isEmpty()
        val rows = db.punchDao().listFor("c1")
        assertThat(rows.map { it.memberId }).containsExactly("m2", "m3")
        assertThat(rows.map { it.clockedInBy }.toSet()).containsExactly("m1")
        assertThat(rows.map { it.jobId }.toSet()).containsExactly("j1")
        await(vm) { s -> s.crew.all { it.onClock } }
        vm.onEvent(ClockEvent.ToggleCrew("m2"))
        assertThat(vm.state.value.crewSelected).isEmpty()
    }

    @Test fun crewMembersGetNoCrewCard() = runBlocking<Unit> {
        addMember("m2", "Maria")
        assertThat(vm(CompanyRole.Crew).state.value.canLeadCrew).isFalse()
    }

    @Test fun crewFarFromJobAsksOnceThenPunchesAll() = runBlocking<Unit> {
        addMember("m2", "Maria"); addMember("m3", "Dev")
        location.fix = GeoFix(34.5, -118.0, 5f)
        val vm = vm(CompanyRole.Owner)
        await(vm) { it.crew.size == 2 }
        vm.onEvent(ClockEvent.ToggleCrew("m2")); vm.onEvent(ClockEvent.ToggleCrew("m3"))
        vm.onEvent(ClockEvent.SelectJob("j1")); vm.onEvent(ClockEvent.ClockInCrew)
        await(vm) { it.farReason != null }
        assertThat(db.punchDao().listFor("c1")).isEmpty()
        vm.onEvent(ClockEvent.PickReason("Supply house")); vm.onEvent(ClockEvent.SaveReason)
        await(vm) { it.crewNotice != null }
        assertThat(db.punchDao().listFor("c1").map { it.reason }).containsExactly("Supply house", "Supply house")
    }
}
