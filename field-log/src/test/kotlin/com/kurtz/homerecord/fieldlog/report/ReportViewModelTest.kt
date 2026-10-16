package com.kurtz.homerecord.fieldlog.report

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.JobRepository
import com.kurtz.homerecord.core.repo.LogEntryRepository
import com.kurtz.homerecord.core.repo.ReportRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HomeRecordDatabase
    private lateinit var jobs: JobRepository
    private lateinit var entries: LogEntryRepository
    private lateinit var reports: ReportRepository
    private val today = LocalDate.now().toString()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        jobs = JobRepository(db, Source.FIELD_LOG)
        entries = LogEntryRepository(db, Source.FIELD_LOG)
        reports = ReportRepository(db, Source.FIELD_LOG)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun vm(jobId: String) =
        ReportViewModel(jobId, today, jobs, reports).also { v -> v.state.first { it.loaded } }

    @Test fun `prefills work completed from notes and counts photos (AC-67)`() = runTest(dispatcher) {
        val job = jobs.createJob("Elm")
        entries.addNote(job.id, "Framed wall")
        entries.addNote(job.id, "Hung drywall")
        entries.addPhoto(job.id, "media/a.jpg")
        val s = vm(job.id).state.value
        assertEquals("Framed wall\nHung drywall", s.workCompleted)
        assertEquals(1, s.photoCount)
        assertEquals("Elm", s.jobName)
        assertEquals(DelayReason.NONE, s.delayReason)
    }

    @Test fun `edits autosave as a DailyReport draft and survive reopening (AC-67)`() = runTest(dispatcher) {
        val job = jobs.createJob("Elm")
        val v = vm(job.id)
        v.onEvent(ReportEvent.WorkChanged("Edited work"))
        v.onEvent(ReportEvent.CrewChanged("4x"))
        v.onEvent(ReportEvent.HoursChanged("7.5"))
        v.onEvent(ReportEvent.DelayReasonChanged(DelayReason.INSPECTION))
        v.onEvent(ReportEvent.WeatherChanged("  Rain "))
        v.state.first { it.saved }
        val again = vm(job.id).state.value
        assertEquals("Edited work", again.workCompleted)
        assertEquals("4", again.crew)
        assertEquals("7.5", again.hours)
        assertEquals(DelayReason.INSPECTION, again.delayReason)
        assertEquals("Rain", again.weather)
        assertEquals(1, db.dailyReportDao().getAll().size)
    }

    @Test fun `viewing a day without editing stores nothing`() = runTest(dispatcher) {
        val job = jobs.createJob("Elm")
        val v = vm(job.id)
        v.flush()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(db.dailyReportDao().getAll().isEmpty())
    }

    @Test fun `changing the day saves the current draft then loads the other day`() = runTest(dispatcher) {
        val job = jobs.createJob("Elm")
        val v = vm(job.id)
        v.onEvent(ReportEvent.WorkChanged("Today's work"))
        v.onEvent(ReportEvent.DayChanged("2000-01-01"))
        v.state.first { it.day == "2000-01-01" && it.loaded }
        assertEquals("", v.state.value.workCompleted)
        assertEquals("Today's work", reports.draftFor(job.id, today).workCompleted)
    }
}
