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
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.log.DailyLogRepository
import com.kurtz.homerecord.core.model.DailyLogSections
import com.kurtz.homerecord.core.model.LogWeather
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

// AC-F30 the weather section is editable and a hand edit is never replaced; AC-F31 "Look it up again" goes back to the proxy
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LogWeatherTest {
    private lateinit var db: HomeRecordDatabase
    private val day = LocalDate.of(2026, 10, 30)
    private val zone = ZoneOffset.UTC
    private fun at(h: Int, d: LocalDate = day) = d.atTime(h, 0).toInstant(zone).toEpochMilli()
    private var weather: ApiResult<LogWeather> = ApiResult.Ok(LogWeather(highF = 58, lowF = 41, precipIn = 0.12, conditions = "rain", fetchedAt = at(9)))
    private var lookups = 0
    private var now = at(17)
    private val gates = MutableStateFlow(RoleGates(CompanyRole.Owner))
    private lateinit var repo: DailyLogRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DailyLogRepository(db, weatherApi = { _, _, _ -> lookups++; weather }, now = { now }, zone = { zone })
        runBlocking {
            db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", lat = 40.06, lng = -75.4, createdAt = 1, updatedAt = 1))
        }
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun form(saved: SavedStateHandle = SavedStateHandle()) = LogWeatherViewModel(LogWeatherDeps("j1", day, repo, gates), saved)

    private suspend fun LogWeatherViewModel.settled(check: (LogWeatherUiState) -> Boolean = { true }) =
        withTimeout(5_000) { state.first { !it.loading && check(it) } }

    private suspend fun saved(): DailyLogSections =
        DailyLogSections.fromJson(checkNotNull(db.dailyLogDao().get(DailyLogRepository.idFor("j1", day))).sectionsJson)

    @AcceptanceCriterion("AC-F30")
    @Test fun typedWeatherIsSavedAndALaterLookupNeverReplacesIt() = runBlocking<Unit> {
        repo.assemble("j1", day)
        val v = form()
        val s = v.settled()
        assertThat(s.draft).isEqualTo(WeatherDraft("rain", "58", "41", "0.12"))
        assertThat(s.handEntered).isFalse()
        assertThat(s.dirty).isFalse()
        v.onEvent(LogWeatherEvent.Conditions("showers"))
        v.onEvent(LogWeatherEvent.High("61"))
        v.onEvent(LogWeatherEvent.Low("44"))
        v.onEvent(LogWeatherEvent.Precip("0.3"))
        assertThat(v.state.value.dirty).isTrue()
        v.onEvent(LogWeatherEvent.Save)
        v.settled { it.done }

        val w = saved().weather
        assertThat(w.edited).isTrue()
        assertThat(w.value).isEqualTo(LogWeather(highF = 61, lowF = 44, precipIn = 0.3, conditions = "showers", fetchedAt = at(9)))

        // hours later the lookup is due again: the typed weather stays and is not even fetched
        now = at(23)
        val before = lookups
        repo.assemble("j1", day)
        assertThat(saved().weather.value?.conditions).isEqualTo("showers")
        assertThat(lookups).isEqualTo(before)
        assertThat(form().settled().handEntered).isTrue()
    }

    @AcceptanceCriterion("AC-F31")
    @Test fun lookItUpAgainClearsTheHandEditAndFetchesTheProxy() = runBlocking<Unit> {
        repo.assemble("j1", day)
        repo.update(DailyLogRepository.idFor("j1", day)) { LogEdits.weather(it, LogWeather(highF = 70, lowF = 60, conditions = "clear")) }
        weather = ApiResult.Ok(LogWeather(highF = 57, lowF = 40, precipIn = 0.2, conditions = "rain", fetchedAt = at(17)))
        val v = form()
        assertThat(v.settled().handEntered).isTrue()
        v.onEvent(LogWeatherEvent.LookUpAgain)
        v.settled { it.done }
        val w = saved().weather
        assertThat(w.edited).isFalse()
        assertThat(w.value).isEqualTo(LogWeather(highF = 57, lowF = 40, precipIn = 0.2, conditions = "rain", fetchedAt = at(17)))
    }

    @AcceptanceCriterion("AC-F31")
    @Test fun lookItUpAgainOfflineKeepsTheTypedWeatherShownButNoLongerHandEntered() = runBlocking<Unit> {
        repo.assemble("j1", day)
        repo.update(DailyLogRepository.idFor("j1", day)) { LogEdits.weather(it, LogWeather(highF = 70, lowF = 60, conditions = "clear")) }
        weather = ApiResult.Err(ApiFailure.Offline)
        val v = form()
        v.settled()
        v.onEvent(LogWeatherEvent.LookUpAgain)
        v.settled { it.done }
        assertThat(saved().weather.edited).isFalse()
        assertThat(saved().weather.value?.conditions).isEqualTo("clear")
        // the next online lookup replaces it
        weather = ApiResult.Ok(LogWeather(highF = 57, lowF = 40, conditions = "rain", fetchedAt = at(18)))
        repo.assemble("j1", day)
        assertThat(saved().weather.value?.conditions).isEqualTo("rain")
    }

    @AcceptanceCriterion("AC-F30")
    @Test fun saveWithErrorsShowsThemAndSavesNothing() = runBlocking<Unit> {
        weather = ApiResult.Err(ApiFailure.Offline)
        repo.assemble("j1", day)
        val v = form()
        assertThat(v.settled().draft).isEqualTo(WeatherDraft())
        v.onEvent(LogWeatherEvent.High("48"))
        v.onEvent(LogWeatherEvent.Low("52"))
        v.onEvent(LogWeatherEvent.Save)
        val s = v.settled { it.showErrors }
        assertThat(s.done).isFalse()
        assertThat(s.check.conditionsMissing).isTrue()
        assertThat(s.check.low).isEqualTo(WeatherFieldError.LOW_ABOVE_HIGH)
        assertThat(saved().weather.value).isNull()
    }

    @Test fun theTypedFormSurvivesProcessDeath() = runBlocking<Unit> {
        repo.assemble("j1", day)
        val handle = SavedStateHandle()
        val v = form(handle)
        v.settled()
        v.onEvent(LogWeatherEvent.Conditions("snow"))
        v.onEvent(LogWeatherEvent.Low("abc"))
        v.onEvent(LogWeatherEvent.Save)
        val restored = form(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) })).settled()
        assertThat(restored.draft).isEqualTo(WeatherDraft("snow", "58", "abc", "0.12"))
        assertThat(restored.showErrors).isTrue()
        assertThat(restored.dirty).isTrue()
    }

    @Test fun noLogOnThePhoneOrCrewShowTheErrorState() = runBlocking<Unit> {
        assertThat(form().settled().failed).isTrue()
        repo.assemble("j1", day)
        gates.value = RoleGates(CompanyRole.Crew)
        assertThat(form().settled().failed).isTrue()
        gates.value = RoleGates(CompanyRole.Owner)
        val v = form()
        v.onEvent(LogWeatherEvent.Retry)
        assertThat(v.settled().failed).isFalse()
    }

    @Test fun formParsesTemperaturesAndRainAndFlagsBadInput() { // AC-F30
        val ok = LogWeatherForm.check(WeatherDraft("rain", " 58.4°", "-3", "0,25\""))
        assertThat(ok.weather).isEqualTo(LogWeather(highF = 58, lowF = -3, precipIn = 0.25, conditions = "rain"))
        assertThat(LogWeatherForm.check(WeatherDraft("fog")).weather).isEqualTo(LogWeather(conditions = "fog"))

        val bad = LogWeatherForm.check(WeatherDraft("unknown", "hot", "200", "-1"))
        assertThat(bad.weather).isNull()
        assertThat(bad.conditionsMissing).isTrue()
        assertThat(bad.high).isEqualTo(WeatherFieldError.NOT_A_NUMBER)
        assertThat(bad.low).isEqualTo(WeatherFieldError.OUT_OF_RANGE)
        assertThat(bad.precip).isEqualTo(WeatherFieldError.OUT_OF_RANGE)
        assertThat(LogWeatherForm.check(WeatherDraft("clear", "40", "41")).low).isEqualTo(WeatherFieldError.LOW_ABOVE_HIGH)

        assertThat(LogWeatherForm.draftOf(LogWeather(highF = 58, lowF = 41, precipIn = 0.0, conditions = "unknown")))
            .isEqualTo(WeatherDraft(null, "58", "41", "0"))
        assertThat(LogWeatherForm.draftOf(null)).isEqualTo(WeatherDraft())
    }
}
