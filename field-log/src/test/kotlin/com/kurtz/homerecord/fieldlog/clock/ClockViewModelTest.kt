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
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    // One thread plays the main thread, as on a device: the ViewModel's coroutines, its SavedStateHandle and its
    // plain fields are only touched there. An Unconfined Main let them resume on Room's threads while the test
    // thread sent the next event, and crewFarFromJobAsksOnceThenPunchesAll timed out once in a full build.
    private val mainThread = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "test-main") }
    private val main = mainThread.asCoroutineDispatcher()

    @Before fun setUp() {
        Dispatchers.setMain(main)
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
        main.close()
    }

    private suspend fun vm(role: CompanyRole = CompanyRole.Crew, leadJobs: Set<String> = emptySet(), approvals: kotlinx.coroutines.flow.Flow<List<com.kurtz.homerecord.core.data.TimesheetApprovalEntity>> = kotlinx.coroutines.flow.flowOf(emptyList()), saved: SavedStateHandle = SavedStateHandle()): ClockViewModel {
        val punches = PunchRepository(db, { clock })
        val deps = ClockDeps(
            "c1", "m1", ClockController(punches, location), punches, db.jobDao(), location, settings,
            { asked }, { asked = true }, now = { clock }, ticks = emptyFlow(),
            role = role, crewClock = CrewClock(punches, ClockController(punches, location), location), members = db.memberDao().observeFor("c1"), approvals = approvals,
            gates = kotlinx.coroutines.flow.flowOf(com.kurtz.homerecord.core.company.RoleGates(role, leadJobs)),
        )
        return withContext(Dispatchers.Main) { ClockViewModel(deps, saved) }.also { created += it; await(it) { s -> !s.loading } }
    }

    // Room runs on real threads here; under a full parallel build (2 Gradle workers, Robolectric) one step took
    // longer than 5 s and breakSwitchAndClockOut timed out. The wait is generous; the checks are unchanged.
    private val AWAIT_MS = 20_000L

    private suspend fun await(vm: ClockViewModel, test: (ClockUiState) -> Boolean): ClockUiState =
        withContext(Dispatchers.Default) { withTimeout(AWAIT_MS) { vm.state.first(test) } }

    /** Sends [event] on the main thread, then lets the main queue run what it started up to its first suspension. */
    private suspend fun ClockViewModel.send(event: ClockEvent) {
        withContext(Dispatchers.Main) { onEvent(event) }
        withContext(Dispatchers.Main) { }
    }

    private suspend fun kinds() = db.punchDao().listFor("c1").sortedBy { it.at }.map { it.kind }

    // AC-F14: crew sees Approved once the week is approved
    @Test fun showsApprovedWhenThisWeekIsApproved() = runBlocking<Unit> {
        assertThat(vm().state.value.weekApproved).isFalse()
        val week = com.kurtz.homerecord.core.punch.WeekKey.of(clock, java.time.ZoneId.systemDefault())
        val row = com.kurtz.homerecord.core.data.TimesheetApprovalEntity("a1", "m1", week, "boss", 1, 1, 1)
        assertThat(vm(approvals = kotlinx.coroutines.flow.flowOf(listOf(row))).state.value.weekApproved).isTrue()
    }

    // AC-F14: an owner's edit to my punch shows on My timesheet with who, when, before and after, and why
    @Test fun ownerEditOfMyPunchShowsWhoWhenAndWhy() = runBlocking<Unit> {
        val vm = vm()
        vm.send(ClockEvent.SelectJob("j1")); vm.send(ClockEvent.ClockIn)
        await(vm) { it.status is ClockStatus.Working }
        val punch = db.punchDao().listFor("c1").single()
        clock += 60_000
        assertThat(PunchRepository(db, { clock }).edit(punch.id, "boss", "Dana Ruiz", punch.at - 1_800_000, "j2", "Started at the supply house")).isTrue()
        val change = await(vm) { it.changes.isNotEmpty() }.changes.single()
        assertThat(change.punchId).isEqualTo(punch.id)
        assertThat(change.kind).isEqualTo(PunchKind.IN)
        assertThat(change.byName).isEqualTo("Dana Ruiz")
        assertThat(change.reason).isEqualTo("Started at the supply house")
        assertThat(change.timeChanged).isTrue()
        assertThat(change.jobChanged).isTrue()
        assertThat(change.beforeJob).isEqualTo("Garage subpanel")
        assertThat(change.afterJob).isEqualTo("Kitchen")
    }

    // AC-F8: a job saved as Upcoming is in the picker once its start date comes; a future one is not
    @Test fun upcomingJobJoinsThePickerOnItsStartDate() = runBlocking<Unit> {
        val today = java.time.Instant.ofEpochMilli(clock).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        db.jobDao().upsert(JobEntity(id = "j4", companyId = "c1", name = "Basement finish", status = "UPCOMING", startDate = today.toString(), createdAt = 4, updatedAt = 4))
        db.jobDao().upsert(JobEntity(id = "j5", companyId = "c1", name = "Porch", status = "UPCOMING", startDate = today.plusDays(1).toString(), createdAt = 5, updatedAt = 5))
        assertThat(vm().state.value.jobs.map { it.id }).containsExactly("j1", "j2", "j4")
    }

    @Test fun listsActiveJobsAndClockInNeedsOne() = runBlocking<Unit> {
        val vm = vm()
        assertThat(vm.state.value.jobs.map { it.id }).containsExactly("j1", "j2")
        vm.send(ClockEvent.ClockIn)
        assertThat(kinds()).isEmpty()
        vm.send(ClockEvent.SelectJob("j1"))
        vm.send(ClockEvent.ClockIn)
        await(vm) { it.status is ClockStatus.Working }
        assertThat(kinds()).containsExactly(PunchKind.IN)
    }

    // AC-F17: the widget opens FL-Clock with the last job preselected
    @Test fun preselectsTheLastJobWorked() = runBlocking<Unit> {
        val first = vm()
        first.send(ClockEvent.SelectJob("j2")); first.send(ClockEvent.ClockIn)
        await(first) { it.status is ClockStatus.Working }
        clock += 1000; first.send(ClockEvent.ClockOut)
        await(first) { it.status is ClockStatus.Out }
        assertThat(vm().state.value.selectedJobId).isEqualTo("j2")
    }

    @Test fun breakSwitchAndClockOut() = runBlocking<Unit> {
        val vm = vm()
        vm.send(ClockEvent.SelectJob("j1")); vm.send(ClockEvent.ClockIn)
        await(vm) { it.status is ClockStatus.Working }
        clock += 1000; vm.send(ClockEvent.StartBreak)
        await(vm) { it.status is ClockStatus.OnBreak }
        clock += 1000; vm.send(ClockEvent.EndBreak)
        await(vm) { it.status is ClockStatus.Working }
        clock += 1000; vm.send(ClockEvent.SelectJob("j2")); vm.send(ClockEvent.SwitchJob)
        await(vm) { (it.status as? ClockStatus.Working)?.jobId == "j2" }
        clock += 1000; vm.send(ClockEvent.ClockOut)
        await(vm) { it.status is ClockStatus.Out }
        assertThat(kinds()).containsExactly(PunchKind.IN, PunchKind.BREAK_START, PunchKind.BREAK_END, PunchKind.SWITCH, PunchKind.OUT).inOrder()
    }

    @Test fun explainsLocationOnFirstClockInOnly() = runBlocking<Unit> {
        location.permitted = false
        val vm = vm()
        vm.send(ClockEvent.SelectJob("j1")); vm.send(ClockEvent.ClockIn)
        assertThat(vm.state.value.explainLocation).isTrue()
        assertThat(kinds()).isEmpty()
        vm.send(ClockEvent.ExplainContinue)
        assertThat(vm.state.value.requestPermission).isTrue()
        vm.send(ClockEvent.PermissionResult(granted = false))
        val s = await(vm) { it.status is ClockStatus.Working && it.savedWithoutLocation }
        assertThat(s.savedWithoutLocation).isTrue()
        assertThat(db.punchDao().listFor("c1").single().note).contains("No location")
        vm.send(ClockEvent.ClockOut)
        await(vm) { it.status is ClockStatus.Out }
        vm.send(ClockEvent.ClockIn)
        assertThat(vm.state.value.explainLocation).isFalse()
    }

    @Test fun requireGpsWithoutPermissionOffersSettings() = runBlocking<Unit> {
        settings.value = CompanySettings(requireGps = true)
        location.permitted = false; asked = true
        val vm = vm()
        vm.send(ClockEvent.SelectJob("j1")); vm.send(ClockEvent.ClockIn)
        assertThat(await(vm) { it.gpsRequired != null }.gpsRequired).isTrue()
        assertThat(kinds()).isEmpty()
        vm.send(ClockEvent.DismissGps)
        assertThat(vm.state.value.gpsRequired).isNull()
    }

    // AC-F18 (cr:fl-settings-source): Require GPS turned on by the office while Clock is open applies to the next punch
    @Test fun requireGpsSetAfterOpeningApplies() = runBlocking<Unit> {
        location.permitted = false; asked = true
        val vm = vm()
        settings.value = CompanySettings(requireGps = true)
        vm.send(ClockEvent.SelectJob("j1")); vm.send(ClockEvent.ClockIn)
        assertThat(await(vm) { it.gpsRequired != null }.gpsRequired).isTrue()
        assertThat(kinds()).isEmpty()
    }

    // AC-F18: allowing location in Settings from the require-GPS dialog finishes the clock-in on return
    @Test fun requireGpsRetriesTheClockInAfterSettings() = runBlocking<Unit> {
        settings.value = CompanySettings(requireGps = true)
        location.permitted = false; asked = true
        val vm = vm()
        vm.send(ClockEvent.SelectJob("j1")); vm.send(ClockEvent.ClockIn)
        await(vm) { it.gpsRequired == true }
        vm.send(ClockEvent.OpenSettings)
        assertThat(vm.state.value.gpsRequired).isNull()
        location.permitted = true
        vm.send(ClockEvent.Resumed)
        await(vm) { it.status is ClockStatus.Working }
        assertThat(kinds()).containsExactly(PunchKind.IN)
        vm.send(ClockEvent.ClockOut)
        await(vm) { it.status is ClockStatus.Out }
        vm.send(ClockEvent.Resumed) // a later return from anywhere punches nothing
        assertThat(kinds()).containsExactly(PunchKind.IN, PunchKind.OUT).inOrder()
    }

    // AC-F18: back from Settings still without location, nothing is punched and nothing is retried later
    @Test fun requireGpsStillDeniedAfterSettingsPunchesNothing() = runBlocking<Unit> {
        settings.value = CompanySettings(requireGps = true)
        location.permitted = false; asked = true
        val vm = vm()
        vm.send(ClockEvent.SelectJob("j1")); vm.send(ClockEvent.ClockIn)
        await(vm) { it.gpsRequired == true }
        vm.send(ClockEvent.OpenSettings)
        vm.send(ClockEvent.Resumed)
        location.permitted = true
        vm.send(ClockEvent.Resumed)
        assertThat(kinds()).isEmpty()
        assertThat(vm.state.value.status).isEqualTo(ClockStatus.Out)
    }

    @Test fun farFromJobAsksForAReasonThenSaves() = runBlocking<Unit> {
        location.fix = GeoFix(34.1, -118.0, 5f) // about 7 miles away
        val vm = vm()
        vm.send(ClockEvent.SelectJob("j1")); vm.send(ClockEvent.ClockIn)
        val sheet = await(vm) { it.farReason != null }.farReason
        assertThat(sheet?.canSave).isFalse()
        vm.send(ClockEvent.PickReason(FarReason.SupplyHouse))
        vm.send(ClockEvent.SaveReason)
        await(vm) { it.status is ClockStatus.Working }
        val p = db.punchDao().listFor("c1").single()
        assertThat(p.farFromJob).isTrue()
        assertThat(p.reason).isEqualTo("supply_house")
    }

    // S5c2c (rule 33), AC-F9: the far-from-job reason sheet, with the reason picked, comes back after process death and still saves the punch
    @Test fun farReasonSheetSurvivesProcessDeath() = runBlocking<Unit> {
        location.fix = GeoFix(34.1, -118.0, 5f)
        val saved = SavedStateHandle()
        val first = vm(saved = saved)
        first.send(ClockEvent.SelectJob("j1")); first.send(ClockEvent.ClockIn)
        val asked = await(first) { it.farReason != null }.farReason
        first.send(ClockEvent.PickReason(FarReason.Other)); first.send(ClockEvent.OtherReason("Dropping off a ladder"))
        val again = vm(saved = SavedStateHandle(saved.keys().associateWith { saved.get<Any>(it) }))
        val sheet = again.state.value.farReason
        assertThat(sheet).isEqualTo(FarReasonSheet(checkNotNull(asked).distanceFt, FarReason.Other, "Dropping off a ladder"))
        again.send(ClockEvent.SaveReason)
        await(again) { it.status is ClockStatus.Working }
        assertThat(db.punchDao().listFor("c1").single().reason).isEqualTo("Dropping off a ladder")
    }

    // N002, AC-F9: a crew member's far-from-job check uses the company's distance from /me, not the 500 ft default
    @Test fun crewFarFromJobUsesTheCompanyDistance() = runBlocking<Unit> {
        location.fix = GeoFix(34.0011, -118.0, 5f) // about 400 ft north of the job
        val near = vm(CompanyRole.Crew)
        near.send(ClockEvent.SelectJob("j1")); near.send(ClockEvent.ClockIn)
        assertThat(await(near) { it.status is ClockStatus.Working }.farReason).isNull()
        clock += 1000; near.send(ClockEvent.ClockOut)
        await(near) { it.status is ClockStatus.Out }

        settings.value = CompanySettings(punchDistanceFt = 300)
        val far = vm(CompanyRole.Crew)
        far.send(ClockEvent.SelectJob("j1")); far.send(ClockEvent.ClockIn)
        assertThat(await(far) { it.farReason != null }.farReason).isNotNull()
    }

    @Test fun otherReasonNeedsText() {
        assertThat(FarReasonSheet(900, FarReason.Other, "  ").canSave).isFalse()
        assertThat(FarReasonSheet(900, FarReason.Other, " Dropping off a ladder ").reason).isEqualTo("Dropping off a ladder")
    }

    // AC-F9: presets are stored as codes, so a punch reads the same whatever the phone's language
    @Test fun presetReasonsStoreCodes() {
        assertThat(FarReason.entries.filter { it != FarReason.Other }.map { FarReasonSheet(900, it).reason })
            .containsExactly("materials", "supply_house", "shop").inOrder()
        assertThat(FarReason.fromStored("shop")).isEqualTo(FarReason.Shop)
        assertThat(FarReason.fromStored("other")).isNull()
        assertThat(FarReason.fromStored("Dropping off a ladder")).isNull()
    }

    private suspend fun addMember(id: String, name: String) =
        db.memberDao().upsert(MemberEntity(id = id, companyId = "c1", userId = id, name = name, createdAt = 1, updatedAt = 1))

    // AC-F11: a lead ticks people, one tap punches each with clockedInBy; people already in are shown but not pickable
    @Test fun leadClocksInSelectedCrewOnTheSelectedJob() = runBlocking<Unit> {
        addMember("m1", "Lead"); addMember("m2", "Maria"); addMember("m3", "Dev")
        val vm = vm(CompanyRole.Lead, leadJobs = setOf("j1"))
        vm.send(ClockEvent.SelectJob("j1"))
        val s0 = await(vm) { it.crew.size == 2 }
        assertThat(s0.canLeadCrew).isTrue()
        assertThat(s0.crew.map { it.id }).containsExactly("m2", "m3")
        vm.send(ClockEvent.ToggleCrew("m2")); vm.send(ClockEvent.ToggleCrew("m3"))
        vm.send(ClockEvent.ClockInCrew)
        val s1 = await(vm) { it.crewNotice != null }
        assertThat(s1.crewNotice?.punched).isEqualTo(2)
        assertThat(s1.crewSelected).isEmpty()
        val rows = db.punchDao().listFor("c1")
        assertThat(rows.map { it.memberId }).containsExactly("m2", "m3")
        assertThat(rows.map { it.clockedInBy }.toSet()).containsExactly("m1")
        assertThat(rows.map { it.jobId }.toSet()).containsExactly("j1")
        await(vm) { s -> s.crew.all { it.onClock } }
        vm.send(ClockEvent.ToggleCrew("m2"))
        assertThat(vm.state.value.crewSelected).isEmpty()
    }

    // AC-F3: a Lead clocks in the crew only on jobs they are on; on another job the card is gone and a stray tap punches no one
    @Test fun leadGetsNoCrewCardOnAJobTheyAreNotOn() = runBlocking<Unit> {
        addMember("m1", "Lead"); addMember("m2", "Maria")
        val vm = vm(CompanyRole.Lead, leadJobs = setOf("j1"))
        await(vm) { it.crew.size == 1 }
        vm.send(ClockEvent.SelectJob("j2"))
        assertThat(vm.state.value.canLeadCrew).isFalse()
        vm.send(ClockEvent.ToggleCrew("m2"))
        vm.send(ClockEvent.ClockInCrew)
        assertThat(db.punchDao().listFor("c1")).isEmpty()
        vm.send(ClockEvent.SelectJob("j1"))
        assertThat(vm.state.value.canLeadCrew).isTrue()
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
        vm.send(ClockEvent.ToggleCrew("m2")); vm.send(ClockEvent.ToggleCrew("m3"))
        vm.send(ClockEvent.SelectJob("j1")); vm.send(ClockEvent.ClockInCrew)
        await(vm) { it.farReason != null }
        assertThat(db.punchDao().listFor("c1")).isEmpty()
        vm.send(ClockEvent.PickReason(FarReason.SupplyHouse)); vm.send(ClockEvent.SaveReason)
        await(vm) { it.crewNotice != null }
        assertThat(db.punchDao().listFor("c1").map { it.reason }).containsExactly("supply_house", "supply_house")
    }
}
