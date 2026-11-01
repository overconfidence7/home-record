package com.kurtz.homerecord.core.log

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.DailyLogEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.data.sections
import com.kurtz.homerecord.core.model.DailyLogSections
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.core.model.LogDelay
import com.kurtz.homerecord.core.model.LogWeather
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.sync.FakeSyncServer
import com.kurtz.homerecord.core.sync.RoomRowStore
import com.kurtz.homerecord.core.sync.SyncEngine
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R09a: a Lead types Visitors and a delay offline while the office phone has the same day open and its auto-assembly
 * saves later crew hours. Both edits reach both phones, because each section syncs as its own field.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DailyLogSyncTest {
    private val tenant = TenantRef(TenantKind.Company, "c1")
    private val server = FakeSyncServer()
    private val day = LocalDate.of(2026, 10, 30)
    private val zone = ZoneOffset.UTC
    private fun at(h: Int, m: Int = 0) = day.atTime(h, m).toInstant(zone).toEpochMilli()
    private var clock = at(12)

    private inner class Phone(device: String) {
        val db: HomeRecordDatabase = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        val engine = SyncEngine(server, RoomRowStore(db), db.outboxDao(), db.syncCursorDao(), db.syncBaseDao(), device)
        val logs = DailyLogRepository(
            db,
            weatherApi = { _, _, _ -> ApiResult.Ok(LogWeather(highF = 58, lowF = 41, conditions = "rain", fetchedAt = at(12))) },
            now = { clock }, zone = { zone }, onChange = { e, id -> engine.recordChange(tenant, e, id) },
        )

        suspend fun punch(id: String, kind: String, time: Long) {
            db.punchDao().upsert(PunchEntity(id = id, companyId = "c1", memberId = "m1", jobId = "j1", kind = kind, at = time, createdAt = time, updatedAt = time))
            engine.recordChange(tenant, "punch", id)
        }

        suspend fun log(): DailyLogSections = checkNotNull(db.dailyLogDao().get(DailyLogRepository.idFor("j1", day))).sections()
    }

    private lateinit var office: Phone
    private lateinit var lead: Phone

    @Before fun setUp() = runBlocking<Unit> {
        office = Phone("office")
        lead = Phone("lead")
        for (p in listOf(office, lead)) {
            p.db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", lat = 40.06, lng = -75.4, createdAt = 1, updatedAt = 1))
            p.db.memberDao().upsert(MemberEntity(id = "m1", companyId = "c1", name = "Maria Soto", createdAt = 1, updatedAt = 1))
        }
        lead.punch("p1", PunchKind.IN, at(7))
        lead.engine.sync(tenant)
        office.engine.sync(tenant)
        office.logs.assemble("j1", day)
        office.engine.sync(tenant)
        lead.engine.sync(tenant)
    }

    @After fun tearDown() { office.db.close(); lead.db.close() }

    @AcceptanceCriterion("AC-F30", "AC-F53")
    @Test fun typedSectionsOfflineSurviveTheOfficePhonesLaterAssembly() = runBlocking<Unit> {
        // the Lead goes offline and types what happened
        clock = at(14)
        val id = DailyLogRepository.idFor("j1", day)
        lead.logs.update(id) { it.copy(visitors = "Town inspector", delays = listOf(LogDelay(DelayReason.INSPECTION, "Waited an hour"))) }

        // meanwhile the office phone, open on the day, re-assembles later crew hours and syncs first
        clock = at(16)
        office.logs.assemble("j1", day)
        office.engine.sync(tenant)

        // the Lead comes back online
        lead.engine.sync(tenant)
        office.engine.sync(tenant)

        for (phone in listOf(office, lead)) {
            val log = phone.log()
            assertThat(log.visitors).isEqualTo("Town inspector")
            assertThat(log.delays).containsExactly(LogDelay(DelayReason.INSPECTION, "Waited an hour"))
            assertThat(log.crew.value.single().minutes).isEqualTo(9 * 60)
        }
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun openingAnUnchangedDayQueuesNothing() = runBlocking<Unit> {
        val queued = office.db.outboxDao().pending().size
        office.logs.assemble("j1", day)
        assertThat(office.db.outboxDao().pending()).hasSize(queued)
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun aLogSavedBeforeTheSplitReadsWholeAndIsWrittenByColumnOnItsNextSave() = runBlocking<Unit> {
        val old = DailyLogSections(visitors = "Neighbour", safety = "Hard hats on")
        val legacyDay = day.minusDays(1)
        lead.db.dailyLogDao().upsert(
            DailyLogEntity(id = DailyLogRepository.idFor("j1", legacyDay), jobId = "j1", day = legacyDay.toString(), sectionsJson = old.toJson(), createdAt = 1, updatedAt = 1),
        )
        val read = checkNotNull(lead.logs.assemble("j1", legacyDay)).log.sections
        assertThat(read.visitors).isEqualTo("Neighbour")
        assertThat(read.safety).isEqualTo("Hard hats on")
        val row = checkNotNull(lead.db.dailyLogDao().get(DailyLogRepository.idFor("j1", legacyDay)))
        assertThat(row.visitors).isEqualTo("Neighbour")
        assertThat(row.crewJson).isNotNull()
    }
}
