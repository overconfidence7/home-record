package com.kurtz.homerecord.core.punch

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.location.LocationProvider
import com.kurtz.homerecord.core.model.GeoFix
import com.kurtz.homerecord.core.model.PunchKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeLocation(var permitted: Boolean = true, var fix: GeoFix? = null) : LocationProvider {
    var lastTimeout = 0L
    override fun hasPermission() = permitted
    override suspend fun currentFix(timeoutMs: Long): GeoFix? { lastTimeout = timeoutMs; return fix }
}

// AC-F8 (fix with accuracy, 5 s cap, offline), AC-F10 (break, switch, out pairs), AC-F18 (permission denial, require GPS), AC-F9 (far reason)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ClockControllerTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: PunchRepository
    private val location = FakeLocation(fix = GeoFix(34.0522, -118.2437, 6f))
    private var clock = 10_000_000L
    private val job = ClockJob("j1", 34.0522, -118.2437)
    private val open = ClockContext("c1", "m1", CompanySettings())

    private fun controller() = ClockController(PunchRepository(db, { clock }).also { repo = it }, location)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    private suspend fun kinds() = db.punchDao().listFor("c1").sortedBy { it.at }.map { it.kind }

    @Test fun clockInSavesFixWithAccuracyUnderFiveSecondCap() = runBlocking<Unit> {
        val c = controller()
        val r = c.clockIn(open, job)
        assertThat(r).isInstanceOf(ClockResult.Done::class.java)
        val p = db.punchDao().listFor("c1").single()
        assertThat(p.accuracyM).isEqualTo(6f)
        assertThat(p.lat).isEqualTo(34.0522)
        assertThat(location.lastTimeout).isEqualTo(5_000L)
        assertThat(c.status("c1", "m1")).isEqualTo(ClockStatus.Working("j1", clock))
    }

    @Test fun breakSwitchAndOutMakeTheRightPunches() = runBlocking<Unit> {
        val c = controller()
        c.clockIn(open, job); clock += 1000
        c.startBreak(open); clock += 1000
        assertThat(c.status("c1", "m1")).isInstanceOf(ClockStatus.OnBreak::class.java)
        c.endBreak(open); clock += 1000
        c.switchJob(open, ClockJob("j2", 34.0522, -118.2437)); clock += 1000
        assertThat((c.status("c1", "m1") as ClockStatus.Working).jobId).isEqualTo("j2")
        c.clockOut(open)
        assertThat(kinds()).containsExactly(
            PunchKind.IN, PunchKind.BREAK_START, PunchKind.BREAK_END, PunchKind.SWITCH, PunchKind.OUT,
        ).inOrder()
        assertThat(c.status("c1", "m1")).isEqualTo(ClockStatus.Out)
    }

    @Test fun clockOutOnBreakEndsTheBreakFirst() = runBlocking<Unit> {
        val c = controller()
        c.clockIn(open, job); clock += 1000
        c.startBreak(open); clock += 1000
        c.clockOut(open)
        assertThat(kinds().takeLast(2)).containsExactly(PunchKind.BREAK_END, PunchKind.OUT).inOrder()
        assertThat(repo.openShift("c1", "m1")).isNull()
    }

    @Test fun actionsThatDoNotFitTheStateAreRefused() = runBlocking<Unit> {
        val c = controller()
        assertThat(c.clockOut(open)).isEqualTo(ClockResult.NotAllowed)
        assertThat(c.startBreak(open)).isEqualTo(ClockResult.NotAllowed)
        c.clockIn(open, job)
        assertThat(c.clockIn(open, job)).isEqualTo(ClockResult.NotAllowed)
        assertThat(c.endBreak(open)).isEqualTo(ClockResult.NotAllowed)
    }

    @Test fun deniedPermissionStillClocksInMarkedNoLocation() = runBlocking<Unit> {
        location.permitted = false
        val r = controller().clockIn(open, job, note = "Back door")
        assertThat((r as ClockResult.Done).noLocation).isTrue()
        val p = db.punchDao().listFor("c1").single()
        assertThat(p.lat).isNull()
        assertThat(p.note).isEqualTo("Back door · No location")
    }

    @Test fun noFixWithinTheCapSavesNoLocation() = runBlocking<Unit> {
        location.fix = null
        val r = controller().clockIn(open, job)
        assertThat((r as ClockResult.Done).noLocation).isTrue()
        assertThat(db.punchDao().listFor("c1").single().note).isEqualTo("No location")
    }

    @Test fun requireGpsWithoutPermissionExplainsAndSavesNothing() = runBlocking<Unit> {
        location.permitted = false
        val strict = open.copy(settings = CompanySettings(requireGps = true))
        assertThat(controller().clockIn(strict, job)).isEqualTo(ClockResult.GpsRequired(permissionMissing = true))
        assertThat(db.punchDao().listFor("c1")).isEmpty()
    }

    @Test fun requireGpsWithPermissionButNoFixIsNotPermissionMissing() = runBlocking<Unit> {
        location.fix = null
        val strict = open.copy(settings = CompanySettings(requireGps = true))
        assertThat(controller().clockIn(strict, job)).isEqualTo(ClockResult.GpsRequired(permissionMissing = false))
    }

    @Test fun farFromJobAsksForReasonThenSavesFlagged() = runBlocking<Unit> {
        location.fix = GeoFix(34.1522, -118.2437, 10f) // about 7 miles north
        val c = controller()
        val first = c.clockIn(open, job)
        assertThat(first).isInstanceOf(ClockResult.NeedsReason::class.java)
        assertThat(db.punchDao().listFor("c1")).isEmpty()
        c.clockIn(open, job, reason = "Supply house")
        val p = db.punchDao().listFor("c1").single()
        assertThat(p.farFromJob).isTrue()
        assertThat(p.reason).isEqualTo("Supply house")
    }
}
