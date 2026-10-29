package com.kurtz.homerecord.core.punch

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.model.GeoFix
import com.kurtz.homerecord.core.model.PunchKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F8 (punch saves offline with a fix), AC-F10 (shift pairs, open shift), AC-F14 (edit needs a reason, audit kept), AC-F52 (queued for sync)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PunchRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private val changes = mutableListOf<Pair<String, String>>()
    private var clock = 1_000_000L
    private lateinit var repo: PunchRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = PunchRepository(db, { clock }, { e, id -> changes += e to id })
    }

    @After fun tearDown() = db.close()

    private fun punch(kind: String, at: Long, fix: GeoFix? = null) =
        NewPunch("c1", "m1", "j1", kind, fix = fix, at = at)

    @Test fun clockInSavesFixAndQueuesForSync() = runBlocking<Unit> {
        val id = repo.record(punch(PunchKind.IN, 1_000, GeoFix(34.05, -118.25, 8f)))
        val row = db.punchDao().get(id)!!
        assertThat(row.lat).isEqualTo(34.05)
        assertThat(row.accuracyM).isEqualTo(8f)
        assertThat(changes).containsExactly("punch" to id)
    }

    @Test fun noLocationPunchStillSaves() = runBlocking<Unit> {
        val id = repo.record(punch(PunchKind.IN, 1_000))
        assertThat(db.punchDao().get(id)!!.lat).isNull()
    }

    @Test fun openShiftThenClockOutClosesIt() = runBlocking<Unit> {
        clock = 10_000_000L
        repo.record(punch(PunchKind.IN, 1_000_000))
        assertThat(repo.openShift("c1", "m1")).isNotNull()
        repo.record(punch(PunchKind.BREAK_START, 3_000_000))
        repo.record(punch(PunchKind.BREAK_END, 3_600_000))
        repo.record(punch(PunchKind.OUT, 8_000_000))
        assertThat(repo.openShift("c1", "m1")).isNull()
        val shift = repo.observeShifts("c1").first().single()
        assertThat(shift.breakMillis()).isEqualTo(600_000L)
        assertThat(shift.endAt).isEqualTo(8_000_000L)
        // Shifts are derived on read; punches write no cached shift rows (cr:shift-cache-dead).
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM shift").use { it.moveToFirst(); assertThat(it.getInt(0)).isEqualTo(0) }
    }

    @Test fun editWithoutReasonIsRefused() = runBlocking<Unit> {
        val id = repo.record(punch(PunchKind.IN, 1_000))
        changes.clear()
        assertThat(repo.edit(id, "o1", "Owner", 2_000, "j1", "  ")).isFalse()
        assertThat(db.punchDao().get(id)!!.at).isEqualTo(1_000L)
        assertThat(changes).isEmpty()
    }

    @Test fun editKeepsAuditAndFlagsShiftEdited() = runBlocking<Unit> {
        val inId = repo.record(punch(PunchKind.IN, 1_000_000))
        repo.record(punch(PunchKind.OUT, 5_000_000))
        clock = 9_000_000L
        assertThat(repo.edit(inId, "o1", "Owner", 1_500_000, "j2", "Forgot to clock in")).isTrue()
        val row = db.punchDao().get(inId)!!
        val audit = repo.auditOf(row).single()
        assertThat(audit.by).isEqualTo("o1")
        assertThat(audit.reason).isEqualTo("Forgot to clock in")
        assertThat(audit.beforeAt).isEqualTo(1_000_000L)
        assertThat(audit.afterAt).isEqualTo(1_500_000L)
        assertThat(audit.afterJobId).isEqualTo("j2")
        assertThat(repo.observeShifts("c1").first().single().edited).isTrue()
        assertThat(changes.last()).isEqualTo("punch" to inId)
    }
}
