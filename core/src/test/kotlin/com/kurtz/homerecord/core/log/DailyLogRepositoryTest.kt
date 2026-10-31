package com.kurtz.homerecord.core.log

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.NoteEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.data.TaskEntity
import com.kurtz.homerecord.core.model.LogSection
import com.kurtz.homerecord.core.model.LogWeather
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DailyLogRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: DailyLogRepository
    private val day = LocalDate.of(2026, 10, 30)
    private val zone = ZoneOffset.UTC
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m).toInstant(zone).toEpochMilli()
    private var clock = at(17)
    private val changes = mutableListOf<Pair<String, String>>()
    private val weatherCalls = mutableListOf<String>()
    private var weather: ApiResult<LogWeather> = ApiResult.Ok(LogWeather(highF = 58, lowF = 41, precipIn = 0.12, conditions = "rain"))

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DailyLogRepository(
            db,
            weatherApi = { lat, lng, date ->
                weatherCalls += "$lat,$lng,$date"
                when (val w = weather) { is ApiResult.Ok -> ApiResult.Ok(w.value.copy(fetchedAt = clock)); is ApiResult.Err -> w }
            },
            now = { clock }, zone = { zone }, onChange = { e, id -> changes += e to id },
        )
        runBlocking {
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", lat = 40.06, lng = -75.4, createdAt = 1, updatedAt = 1))
            db.memberDao().upsert(MemberEntity(id = "m1", companyId = "c1", name = "Maria Soto", createdAt = 1, updatedAt = 1))
            punch("p1", PunchKind.IN, at(7))
            punch("p2", PunchKind.OUT, at(15, 30))
            db.noteDao().upsert(NoteEntity(id = "n1", jobId = "j1", day = day.toString(), text = "Set the base cabinets", createdAt = at(10), updatedAt = at(10)))
            db.photoDao().upsert(PhotoEntity(id = "ph1", homeId = "h1", jobId = "j1", path = "/p.jpg", takenAt = at(11), createdAt = at(11), updatedAt = at(11)))
            db.taskDao().upsert(TaskEntity(id = "t1", jobId = "j1", title = "Level the sink base", done = true, createdAt = at(8, d = day.minusDays(2)), updatedAt = at(14)))
            db.taskDao().upsert(TaskEntity(id = "t2", jobId = "j1", title = "Hang uppers", createdAt = at(8), updatedAt = at(8)))
        }
    }

    @After fun tearDown() = db.close()

    private suspend fun punch(id: String, kind: String, at: Long) =
        db.punchDao().upsert(PunchEntity(id = id, companyId = "c1", memberId = "m1", jobId = "j1", kind = kind, at = at, createdAt = at, updatedAt = at))

    @AcceptanceCriterion("AC-F30", "AC-F31")
    @Test fun assemblesTheDayFromRoomAndSavesTheDraft() = runBlocking<Unit> {
        val result = checkNotNull(repo.assemble("j1", day))
        val s = result.log.sections
        assertThat(s.crew.value.map { it.name to it.minutes }).containsExactly("Maria Soto" to 510)
        assertThat(s.workDone.value).containsExactly("Set the base cabinets")
        assertThat(s.photoIds.value).containsExactly("ph1")
        assertThat(s.tasksDone.value.map { it.title }).containsExactly("Level the sink base")
        assertThat(s.weather.value?.conditions).isEqualTo("rain")
        assertThat(result.weather).isEqualTo(WeatherStatus.Shown)
        assertThat(weatherCalls).containsExactly("40.06,-75.4,2026-10-30")
        assertThat(result.log.id).isEqualTo(DailyLogRepository.idFor("j1", day))
        assertThat(changes).containsExactly("daily_log" to result.log.id)
        assertThat(repo.observe("j1", day).first()?.sections).isEqualTo(s)
        assertThat(checkNotNull(db.dailyLogDao().get(result.log.id)).weatherJson).contains("\"rain\"")
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun reassemblingKeepsHandEditsAndPicksUpNewRecords() = runBlocking<Unit> {
        val id = checkNotNull(repo.assemble("j1", day)).log.id
        repo.update(id) { it.copy(workDone = LogSection(listOf("Base cabinets set and shimmed"), edited = true), safety = "Toolbox talk: ladders") }
        db.photoDao().upsert(PhotoEntity(id = "ph2", homeId = "h1", jobId = "j1", path = "/q.jpg", takenAt = at(16), createdAt = at(16), updatedAt = at(16)))
        db.noteDao().upsert(NoteEntity(id = "n2", jobId = "j1", day = day.toString(), text = "Countertop template Monday", createdAt = at(16), updatedAt = at(16)))
        clock = at(17, 30)
        val s = checkNotNull(repo.assemble("j1", day)).log.sections
        assertThat(s.workDone.value).containsExactly("Base cabinets set and shimmed")
        assertThat(s.safety).isEqualTo("Toolbox talk: ladders")
        assertThat(s.photoIds.value).containsExactly("ph1", "ph2").inOrder()
        assertThat(db.dailyLogDao().listFor("j1")).hasSize(1)
    }

    @AcceptanceCriterion("AC-F31")
    @Test fun offlineWithNoSnapshotSaysNoWeather() = runBlocking<Unit> {
        weather = ApiResult.Err(ApiFailure.Offline)
        val result = checkNotNull(repo.assemble("j1", day))
        assertThat(result.weather).isEqualTo(WeatherStatus.Offline)
        assertThat(result.log.sections.weather.value).isNull()
        // back online: the next open fills it in
        weather = ApiResult.Ok(LogWeather(highF = 60, lowF = 45, conditions = "clear"))
        val again = checkNotNull(repo.assemble("j1", day))
        assertThat(again.weather).isEqualTo(WeatherStatus.Shown)
        assertThat(again.log.sections.weather.value?.highF).isEqualTo(60)
    }

    @AcceptanceCriterion("AC-F31")
    @Test fun snapshotIsCachedForSixHoursAndKeptWhenARefreshFails() = runBlocking<Unit> {
        clock = at(9)
        repo.assemble("j1", day)
        clock = at(14)
        repo.assemble("j1", day)
        assertThat(weatherCalls).hasSize(1)
        clock = at(15, 30)
        weather = ApiResult.Err(ApiFailure.Offline)
        val stale = checkNotNull(repo.assemble("j1", day))
        assertThat(weatherCalls).hasSize(2)
        assertThat(stale.weather).isEqualTo(WeatherStatus.Shown)
        assertThat(stale.log.sections.weather.value?.conditions).isEqualTo("rain")
    }

    @AcceptanceCriterion("AC-F31")
    @Test fun aSnapshotTakenAfterTheDayEndedIsFinal() = runBlocking<Unit> {
        clock = at(9, d = day.plusDays(1))
        repo.assemble("j1", day)
        clock = at(9, d = day.plusDays(3))
        repo.assemble("j1", day)
        assertThat(weatherCalls).hasSize(1)
    }

    @AcceptanceCriterion("AC-F31")
    @Test fun noLocationAndTooFarAheadAreToldApart() = runBlocking<Unit> {
        db.jobDao().upsert(JobEntity(id = "j2", companyId = "c1", name = "Garage subpanel", createdAt = 1, updatedAt = 1))
        assertThat(checkNotNull(repo.assemble("j2", day)).weather).isEqualTo(WeatherStatus.NoLocation)
        weather = ApiResult.Err(ApiFailure.NotFound)
        assertThat(checkNotNull(repo.assemble("j1", day.plusDays(30))).weather).isEqualTo(WeatherStatus.NotYet)
        assertThat(weatherCalls).containsExactly("40.06,-75.4,2026-11-29")
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun anUnchangedReassemblyWritesAndQueuesNothing() = runBlocking<Unit> {
        repo.assemble("j1", day)
        val first = changes.toList()
        clock = at(17, 5)
        repo.assemble("j1", day)
        val id = DailyLogRepository.idFor("j1", day)
        repo.update(id) { it }
        assertThat(changes).isEqualTo(first)
        assertThat(checkNotNull(db.dailyLogDao().get(id)).updatedAt).isEqualTo(at(17))
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun daysListNewestFirstAndMissingJobIsNull() = runBlocking<Unit> {
        repo.assemble("j1", day.minusDays(1))
        repo.assemble("j1", day)
        assertThat(repo.observeDays("j1").first().map { it.day }).containsExactly(day, day.minusDays(1)).inOrder()
        assertThat(repo.assemble("gone", day)).isNull()
        assertThat(repo.observe("j1", day.plusDays(1)).first()).isNull()
    }

    @Test fun theIdIsTheSameOnEveryPhone() {
        assertThat(DailyLogRepository.idFor("j1", day)).isEqualTo(DailyLogRepository.idFor("j1", LocalDate.parse("2026-10-30")))
        assertThat(DailyLogRepository.idFor("j1", day)).isNotEqualTo(DailyLogRepository.idFor("j1", day.plusDays(1)))
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun editsSavedTogetherOrDuringAssemblyAllLand() = runBlocking<Unit> {
        val id = checkNotNull(repo.assemble("j1", day)).log.id
        kotlinx.coroutines.coroutineScope {
            listOf("Visitors", "Safety", "Client").forEach { what ->
                launch(kotlinx.coroutines.Dispatchers.IO) {
                    repo.update(id) { s ->
                        when (what) {
                            "Visitors" -> s.copy(visitors = "Township inspector")
                            "Safety" -> s.copy(safety = "Covered the open floor box")
                            else -> s.copy(clientNotes = "Countertops Monday")
                        }
                    }
                }
            }
            launch(kotlinx.coroutines.Dispatchers.IO) { repo.assemble("j1", day) }
        }
        val s = checkNotNull(repo.observe("j1", day).first()).sections
        assertThat(listOf(s.visitors, s.safety, s.clientNotes)).containsExactly("Township inspector", "Covered the open floor box", "Countertops Monday").inOrder()
    }

    @AcceptanceCriterion("AC-F32")
    @Test fun rangeListsEveryJobsLogOncePerDayWithItsDelays() = runBlocking<Unit> {
        db.jobDao().upsert(JobEntity(id = "j2", companyId = "c1", name = "Ortiz bath", createdAt = 1, updatedAt = 1))
        val hale = checkNotNull(repo.assemble("j1", day)).log.id
        repo.update(hale) { it.copy(delays = listOf(com.kurtz.homerecord.core.model.LogDelay(com.kurtz.homerecord.core.model.DelayReason.WEATHER, "Rain"))) }
        repo.assemble("j2", day.minusDays(2))
        repo.assemble("j1", day.minusDays(9)) // before the range
        // a second row for the same day written by another phone before ids were derived: the derived one wins
        db.dailyLogDao().upsert(com.kurtz.homerecord.core.data.DailyLogEntity(id = "old", jobId = "j1", day = day.toString(), createdAt = 0, updatedAt = 0))
        val logs = repo.observeRange(day.minusDays(6), day).first()
        assertThat(logs.map { it.jobId to it.day }).containsExactly("j1" to day, "j2" to day.minusDays(2))
        assertThat(logs.single { it.jobId == "j1" }.sections.delays.map { it.note }).containsExactly("Rain")
    }
}
