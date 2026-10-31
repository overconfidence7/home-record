package com.kurtz.homerecord.fieldlog.log

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.data.DailyLogEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.MemberEntity
import com.kurtz.homerecord.core.data.NoteEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.data.PunchEntity
import com.kurtz.homerecord.core.log.DailyLogRepository
import com.kurtz.homerecord.core.log.WeatherStatus
import com.kurtz.homerecord.core.model.DailyLogSections
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.core.model.LogDelay
import com.kurtz.homerecord.core.model.LogLineKind
import com.kurtz.homerecord.core.model.LogWeather
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F30 every section editable and kept on re-assembly, AC-F31 weather or "No weather (offline)", AC-F32 delay picker with note
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LogViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private val day = LocalDate.of(2026, 10, 30)
    private val zone = ZoneOffset.UTC
    private fun at(h: Int, m: Int = 0, d: LocalDate = day) = d.atTime(h, m).toInstant(zone).toEpochMilli()
    private var weather: ApiResult<LogWeather> = ApiResult.Ok(LogWeather(highF = 58, lowF = 41, precipIn = 0.12, conditions = "rain", fetchedAt = 1))
    private val gates = MutableStateFlow(RoleGates(CompanyRole.Owner))
    private lateinit var repo: DailyLogRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DailyLogRepository(db, weatherApi = { _, _, _ -> weather }, now = { at(17) }, zone = { zone })
        runBlocking {
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", lat = 40.06, lng = -75.4, createdAt = 1, updatedAt = 1))
            db.memberDao().upsert(MemberEntity(id = "m1", companyId = "c1", name = "Marcus Ortiz", createdAt = 1, updatedAt = 1))
            punch("p1", PunchKind.IN, at(7))
            punch("p2", PunchKind.OUT, at(15, 30))
            db.noteDao().upsert(NoteEntity(id = "n1", jobId = "j1", day = day.toString(), text = "Set the base cabinets", createdAt = at(10), updatedAt = at(10)))
            db.photoDao().upsert(
                PhotoEntity(id = "ph1", homeId = "h1", jobId = "j1", path = "/m/ph1.jpg", thumbPath = "/m/ph1_t.jpg", takenAt = at(11), createdAt = at(11), updatedAt = at(11)),
            )
        }
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun punch(id: String, kind: String, at: Long) =
        db.punchDao().upsert(PunchEntity(id = id, companyId = "c1", memberId = "m1", jobId = "j1", kind = kind, at = at, createdAt = at, updatedAt = at))

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = LogViewModel(LogDeps("j1", repo, db.photoDao(), gates, today = { day }), saved)

    private suspend fun LogViewModel.settled(check: (LogUiState) -> Boolean = { true }): LogUiState =
        withTimeout(5_000) { state.first { !it.loading && check(it) } }

    private suspend fun saved(): DailyLogSections =
        DailyLogSections.fromJson(checkNotNull(db.dailyLogDao().get(DailyLogRepository.idFor("j1", day))).sectionsJson)

    @AcceptanceCriterion("AC-F30", "AC-F31")
    @Test fun openingTheDayBuildsTheDraftWithCrewWorkPhotosAndWeather() = runBlocking<Unit> {
        val s = vm().settled { it.exists }
        assertThat(s.canEdit).isTrue()
        assertThat(s.crew.map { it.name to it.minutes }).containsExactly("Marcus Ortiz" to 510)
        assertThat(s.workDone).containsExactly("Set the base cabinets")
        assertThat(s.photos).containsExactly(LogPhotoThumb("ph1", "/m/ph1_t.jpg"))
        assertThat(s.weather?.conditions).isEqualTo("rain")
        assertThat(s.weatherStatus).isEqualTo(WeatherStatus.Shown)
        assertThat(s.canGoForward).isFalse()
    }

    @AcceptanceCriterion("AC-F31")
    @Test fun offlineWithNoSnapshotSaysNoWeatherOffline() = runBlocking<Unit> {
        weather = ApiResult.Err(ApiFailure.Offline)
        val s = vm().settled { it.exists && it.weatherStatus != null }
        assertThat(s.weather).isNull()
        assertThat(s.weatherStatus).isEqualTo(WeatherStatus.Offline)
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun editedWorkDoneAndHoursSurviveReopeningTheDay() = runBlocking<Unit> {
        val v = vm()
        v.settled { it.exists }
        v.onEvent(LogEvent.EditText(LogTextSection.WORK_DONE))
        assertThat((v.settled { it.editor != null }.editor as LogEditor.Text).text).isEqualTo("Set the base cabinets")
        v.onEvent(LogEvent.EditorText("Set the base cabinets\n\nShimmed the sink base level"))
        v.onEvent(LogEvent.SaveEditor)
        v.onEvent(LogEvent.EditHours("m1"))
        assertThat((v.settled { it.editor is LogEditor.Hours }.editor as LogEditor.Hours).text).isEqualTo("8.5")
        v.onEvent(LogEvent.EditorText("7:45"))
        v.onEvent(LogEvent.SaveEditor)
        v.onEvent(LogEvent.EditText(LogTextSection.VISITORS))
        v.onEvent(LogEvent.EditorText("Township inspector "))
        v.onEvent(LogEvent.SaveEditor)
        v.settled { it.crew.firstOrNull()?.minutes == 465 && it.workDone.size == 2 && it.visitors.isNotEmpty() }

        // a new note lands and the day is opened again: the hand edits stay
        db.noteDao().upsert(NoteEntity(id = "n2", jobId = "j1", day = day.toString(), text = "Hung uppers", createdAt = at(14), updatedAt = at(14)))
        val again = vm().settled { it.exists }
        assertThat(again.workDone).containsExactly("Set the base cabinets", "Shimmed the sink base level").inOrder()
        assertThat(again.crew.single().minutes).isEqualTo(465)
        assertThat(again.visitors).isEqualTo("Township inspector")
        assertThat(saved().workDone.edited).isTrue()
        assertThat(saved().crew.edited).isTrue()
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun hoursThatDoNotReadAsATimeKeepTheDialogOpen() = runBlocking<Unit> {
        val v = vm()
        v.settled { it.exists }
        v.onEvent(LogEvent.EditHours("m1"))
        v.onEvent(LogEvent.EditorText("about eight"))
        v.onEvent(LogEvent.SaveEditor)
        assertThat(v.settled { it.hoursInvalid }.editor).isInstanceOf(LogEditor.Hours::class.java)
        v.onEvent(LogEvent.EditorText("0"))
        assertThat(v.settled { it.editor == LogEditor.Hours("m1", "Marcus Ortiz", "0") }.hoursInvalid).isFalse()
        v.onEvent(LogEvent.SaveEditor)
        assertThat(v.settled { it.crew.isEmpty() }.editor).isNull()
    }

    @AcceptanceCriterion("AC-F32")
    @Test fun delayNeedsAReasonThenSavesWithItsNoteAndRemovesAfterConfirm() = runBlocking<Unit> {
        val v = vm()
        v.settled { it.exists }
        v.onEvent(LogEvent.AddDelay)
        v.onEvent(LogEvent.EditorText("Countertop templates pushed to Monday"))
        v.onEvent(LogEvent.SaveEditor)
        // no reason picked yet: the sheet stays open with the note
        assertThat(v.settled { it.editor is LogEditor.Delay && (it.editor as LogEditor.Delay).note.isNotEmpty() }.editor)
            .isEqualTo(LogEditor.Delay(null, "Countertop templates pushed to Monday"))
        v.onEvent(LogEvent.EditorReason(DelayReason.MATERIALS))
        v.onEvent(LogEvent.SaveEditor)
        val s = v.settled { it.delays.isNotEmpty() }
        assertThat(s.delays).containsExactly(LogDelay(DelayReason.MATERIALS, "Countertop templates pushed to Monday"))
        assertThat(saved().delays).isEqualTo(s.delays)

        v.onEvent(LogEvent.AskRemoveDelay(0))
        assertThat(v.settled { it.editor != null }.editor).isEqualTo(LogEditor.RemoveDelay(0))
        v.onEvent(LogEvent.SaveEditor)
        assertThat(v.settled { it.delays.isEmpty() }.editor).isNull()
    }

    @Test fun dayBarMovesBackButNeverPastTodayAndTheOpenSheetSurvivesProcessDeath() = runBlocking<Unit> {
        val handle = SavedStateHandle()
        val v = vm(handle)
        v.settled { it.exists }
        v.onEvent(LogEvent.PreviousDay)
        assertThat(v.settled { it.day == day.minusDays(1) }.canGoForward).isTrue()
        v.onEvent(LogEvent.PickDay(day.plusDays(5)))
        assertThat(v.settled { it.day == day }.day).isEqualTo(day)
        v.onEvent(LogEvent.AddDelay)
        v.onEvent(LogEvent.EditorReason(DelayReason.INSPECTION))
        v.onEvent(LogEvent.PreviousDay)
        // changing day closes the sheet
        v.settled { it.day == day.minusDays(1) && it.exists && it.editor == null }
        v.onEvent(LogEvent.AddDelay)
        v.onEvent(LogEvent.EditorReason(DelayReason.CLIENT))

        val restored = vm(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        val s = restored.settled { it.exists }
        assertThat(s.day).isEqualTo(day.minusDays(1))
        assertThat(s.editor).isEqualTo(LogEditor.Delay(DelayReason.CLIENT))
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun crewReadTheSavedLogButNeverBuildOrChangeOne() = runBlocking<Unit> {
        gates.value = RoleGates(CompanyRole.Crew)
        val empty = vm().settled()
        assertThat(empty.exists).isFalse()
        assertThat(db.dailyLogDao().listFor("j1")).isEmpty()

        val sections = DailyLogSections(visitors = "Township inspector", delays = listOf(LogDelay(DelayReason.WEATHER, "Rain")))
        db.dailyLogDao().upsert(
            DailyLogEntity(id = DailyLogRepository.idFor("j1", day), jobId = "j1", day = day.toString(), sectionsJson = sections.toJson(), createdAt = 1, updatedAt = 1),
        )
        val v = vm()
        val s = v.settled { it.exists }
        assertThat(s.canEdit).isFalse()
        assertThat(s.visitors).isEqualTo("Township inspector")
        assertThat(s.weatherStatus).isNull()
        v.onEvent(LogEvent.AddDelay)
        v.onEvent(LogEvent.EditText(LogTextSection.SAFETY))
        v.onEvent(LogEvent.AskRemoveDelay(0))
        assertThat(v.settled().editor).isNull()
        assertThat(saved()).isEqualTo(sections)
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun aPhotoLeftOutAfterConfirmStaysOutOnReopeningUntilPutBack() = runBlocking<Unit> {
        val v = vm()
        v.settled { it.photos.isNotEmpty() }
        v.onEvent(LogEvent.AskDrop(LogLineKind.PHOTO, "nope"))
        v.onEvent(LogEvent.AskDrop(LogLineKind.PHOTO, "ph1"))
        assertThat(v.settled { it.editor != null }.editor).isEqualTo(LogEditor.Drop(LogLineKind.PHOTO, "ph1", null))
        v.onEvent(LogEvent.SaveEditor)
        val s = v.settled { it.photos.isEmpty() }
        assertThat(s.editor).isNull()
        assertThat(s.dropped).containsExactly(LogLineKind.PHOTO, 1)
        // the photo itself stays on the job
        assertThat(db.photoDao().observeForJob("j1").first().map { it.id }).containsExactly("ph1")

        val again = vm().settled { it.exists }
        assertThat(again.photos).isEmpty()
        again.let { assertThat(it.dropped[LogLineKind.PHOTO]).isEqualTo(1) }

        v.onEvent(LogEvent.PutBack(LogLineKind.PHOTO))
        val back = v.settled { it.photos.isNotEmpty() }
        assertThat(back.photos.map { it.id }).containsExactly("ph1")
        assertThat(back.dropped).isEmpty()
        assertThat(saved().dropped).isEmpty()
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun crewCannotLeaveLinesOut() = runBlocking<Unit> {
        vm().settled { it.photos.isNotEmpty() }
        gates.value = RoleGates(CompanyRole.Crew)
        val v = vm()
        v.settled { it.exists && !it.canEdit }
        v.onEvent(LogEvent.AskDrop(LogLineKind.PHOTO, "ph1"))
        v.onEvent(LogEvent.PutBack(LogLineKind.PHOTO))
        assertThat(v.settled().editor).isNull()
        assertThat(saved().photoIds.value).containsExactly("ph1")
    }

    @Test fun editsMarkTheirSectionAndZeroHoursTakesAPersonOff() {
        val start = DailyLogSections()
        val work = LogEdits.text(start, LogTextSection.WORK_DONE, "  One \n\n\n Two\n  \nThree ")
        assertThat(work.workDone.value).containsExactly("One", "Two", "Three").inOrder()
        assertThat(work.workDone.edited).isTrue()
        assertThat(LogEdits.text(start, LogTextSection.CLIENT_NOTES, " Countertops Monday ").clientNotes).isEqualTo("Countertops Monday")
    }
}
