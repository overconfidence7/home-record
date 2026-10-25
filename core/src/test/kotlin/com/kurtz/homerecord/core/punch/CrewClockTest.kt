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
import java.time.ZoneId
import java.time.ZonedDateTime

private class CrewLocation(var fix: GeoFix? = GeoFix(34.0522, -118.2437, 5f)) : LocationProvider {
    override fun hasPermission() = true
    override suspend fun currentFix(timeoutMs: Long): GeoFix? = fix
}

// AC-F11 (crew clock-in with the lead's fix and clockedInBy), AC-F14 (approve locks the week, edits refused)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class CrewClockTest {
    private lateinit var db: HomeRecordDatabase
    private val zone = ZoneId.of("UTC")
    private var clock = 10_000_000L
    private val location = CrewLocation()
    private val ctx = ClockContext("c1", "lead", CompanySettings())
    private val job = ClockJob("j1", 34.0522, -118.2437)

    private fun repo() = PunchRepository(db, { clock }, zone = { zone })
    private fun crew(r: PunchRepository) = CrewClock(r, ClockController(r, location), location)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun oneTapPunchesEveryoneWithLeadFixAndClockedInBy() = runBlocking<Unit> {
        val r = repo()
        val res = crew(r).clockInCrew(ctx, "lead", setOf("a", "b", "lead"), job)
        assertThat(res).isInstanceOf(CrewResult.Done::class.java)
        val rows = db.punchDao().listFor("c1")
        assertThat(rows.map { it.memberId }).containsExactly("a", "b", "lead")
        assertThat(rows.map { it.clockedInBy }.toSet()).containsExactly("lead")
        assertThat(rows.map { it.lat }.toSet()).containsExactly(34.0522)
        assertThat(rows.map { it.kind }.toSet()).containsExactly(PunchKind.IN)
    }

    @Test fun alreadyClockedInMembersAreSkipped() = runBlocking<Unit> {
        val r = repo()
        val c = crew(r)
        c.clockInCrew(ctx, "lead", setOf("a"), job)
        val res = c.clockInCrew(ctx, "lead", setOf("a", "b"), job) as CrewResult.Done
        assertThat(res.punched).containsExactly("b")
        assertThat(res.skipped).containsExactly("a")
        assertThat(db.punchDao().listFor("c1")).hasSize(2)
    }

    @Test fun farFromJobAsksOnceForOneReasonForAll() = runBlocking<Unit> {
        val r = repo()
        val far = ClockJob("j2", 34.2, -118.2437)
        val c = crew(r)
        assertThat(c.clockInCrew(ctx, "lead", setOf("a", "b"), far)).isInstanceOf(CrewResult.NeedsReason::class.java)
        assertThat(db.punchDao().listFor("c1")).isEmpty()
        c.clockInCrew(ctx, "lead", setOf("a", "b"), far, "Supplier stop")
        assertThat(db.punchDao().listFor("c1").map { it.reason }).containsExactly("Supplier stop", "Supplier stop")
    }

    @Test fun emptySelectionAndRequiredGps() = runBlocking<Unit> {
        val c = crew(repo())
        assertThat(c.clockInCrew(ctx, "lead", emptySet(), job)).isEqualTo(CrewResult.NothingSelected)
        location.fix = null
        val strict = ctx.copy(settings = CompanySettings(requireGps = true))
        assertThat(c.clockInCrew(strict, "lead", setOf("a"), job)).isEqualTo(CrewResult.GpsRequired(permissionMissing = false))
        assertThat(db.punchDao().listFor("c1")).isEmpty()
    }

    @Test fun approveLocksTheWeekAndRefusesEdits() = runBlocking<Unit> {
        val r = repo()
        val at = ZonedDateTime.of(2026, 10, 20, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val id = r.record(NewPunch("c1", "a", "j1", PunchKind.IN, at = at))
        val approvals = TimesheetApprovals(db, { zone }, { clock })
        val week = WeekKey.of(at, zone)
        assertThat(week).isEqualTo("2026-10-19")
        assertThat(approvals.approve("a", week, "lead")).isTrue()
        assertThat(approvals.approve("a", week, "lead")).isFalse()
        assertThat(approvals.isLocked("a", at)).isTrue()
        assertThat(r.edit(id, "lead", "Lee", at + 60_000, "j1", "Forgot")).isFalse()
        assertThat(approvals.isLocked("b", at)).isFalse()
    }

    @Test fun approveAllCountsOnlyNewApprovals() = runBlocking<Unit> {
        val approvals = TimesheetApprovals(db, { zone }, { clock })
        approvals.approve("a", "2026-10-19", "lead")
        assertThat(approvals.approveAll(listOf("a", "b", "c"), "2026-10-19", "lead")).isEqualTo(2)
    }
}
