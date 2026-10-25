package com.kurtz.homerecord.fieldlog.clock

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanySettings
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

// AC-F8 (clock in with job), AC-F9 (far reason), AC-F10 (break, switch, out), AC-F18 (explanation once, no-location and require-GPS paths)
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ClockViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private val created = mutableListOf<ClockViewModel>()
    private val location = FakeLocation()
    private var asked = false
    private var settings = CompanySettings()
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

    private suspend fun vm(): ClockViewModel {
        val punches = PunchRepository(db, { clock })
        val deps = ClockDeps(
            "c1", "m1", ClockController(punches, location), punches, db.jobDao(), location, { settings },
            { asked }, { asked = true }, now = { clock }, ticks = emptyFlow(),
        )
        return ClockViewModel(deps, SavedStateHandle()).also { created += it; await(it) { s -> !s.loading } }
    }

    private suspend fun await(vm: ClockViewModel, test: (ClockUiState) -> Boolean): ClockUiState =
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first(test) } }

    private suspend fun kinds() = db.punchDao().listFor("c1").sortedBy { it.at }.map { it.kind }

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
        settings = CompanySettings(requireGps = true)
        location.permitted = false; asked = true
        val vm = vm()
        vm.onEvent(ClockEvent.SelectJob("j1")); vm.onEvent(ClockEvent.ClockIn)
        assertThat(await(vm) { it.gpsRequired != null }.gpsRequired).isTrue()
        assertThat(kinds()).isEmpty()
        vm.onEvent(ClockEvent.DismissGps)
        assertThat(vm.state.value.gpsRequired).isNull()
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
}
