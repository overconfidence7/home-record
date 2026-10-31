package com.kurtz.homerecord.fieldlog.acceptance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.NoteEntity
import com.kurtz.homerecord.core.log.DailyLogRepository
import com.kurtz.homerecord.core.log.WeatherStatus
import com.kurtz.homerecord.core.lookup.RetrofitWeatherApi
import com.kurtz.homerecord.core.model.LogSection
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The daily log's draft and weather on a device (AC-F30, AC-F31): real Room and the real Retrofit weather client.
 * The weather host is one nothing listens on, so the lookup fails as it does with no signal; the screen arrives in T043d.
 */
@RunWith(AndroidJUnit4::class)
class DailyLogAcceptanceTest {
    private lateinit var db: HomeRecordDatabase
    private val day = LocalDate.now()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    @AcceptanceCriterion("AC-F30", "AC-F31")
    @Test fun offlineDraftHasNoWeatherAndKeepsHandEdits() = runBlocking<Unit> {
        val offline = RetrofitWeatherApi.create("http://127.0.0.1:9/", OkHttpClient())
        val repo = DailyLogRepository(db, offline)
        val now = System.currentTimeMillis()
        db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", lat = 40.06, lng = -75.4, createdAt = now, updatedAt = now))
        db.noteDao().upsert(NoteEntity(id = "n1", jobId = "j1", day = day.toString(), text = "Set the base cabinets", createdAt = now, updatedAt = now))

        val first = checkNotNull(repo.assemble("j1", day))
        assertThat(first.weather).isEqualTo(WeatherStatus.Offline)
        assertThat(first.log.sections.weather.value).isNull()
        assertThat(first.log.sections.workDone.value).containsExactly("Set the base cabinets")

        repo.update(first.log.id) { it.copy(workDone = LogSection(listOf("Base cabinets set and shimmed"), edited = true)) }
        val again = checkNotNull(repo.assemble("j1", day))
        assertThat(again.log.sections.workDone.value).containsExactly("Base cabinets set and shimmed")
        assertThat(db.dailyLogDao().listFor("j1")).hasSize(1)
    }
}
