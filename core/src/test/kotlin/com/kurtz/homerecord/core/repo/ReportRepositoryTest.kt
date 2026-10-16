package com.kurtz.homerecord.core.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.core.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReportRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var jobs: JobRepository
    private lateinit var entries: LogEntryRepository
    private lateinit var reports: ReportRepository
    private var tick = 100L
    private val zone = java.time.ZoneOffset.UTC
    private val clock = Clock { tick++ }

    @Before fun setUp() {
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        jobs = JobRepository(db, Source.FIELD_LOG, clock)
        entries = LogEntryRepository(db, Source.FIELD_LOG, clock, zone = { zone })
        reports = ReportRepository(db, Source.FIELD_LOG, clock)
    }

    @After fun tearDown() = db.close()

    private val today get() = java.time.Instant.ofEpochMilli(0).atZone(zone).toLocalDate().toString()

    @Test fun `draft is prefilled from notes and photos then saved edits win (AC-67)`() = runTest {
        val job = jobs.createJob("Elm")
        entries.addNote(job.id, "Framed wall")
        entries.addNote(job.id, "Hung drywall")
        entries.addPhoto(job.id, "media/a.jpg")
        val day = today
        val draft = reports.draftFor(job.id, day)
        assertEquals("Framed wall\nHung drywall", draft.workCompleted)
        assertEquals(1, reports.photosFor(job.id, day).size)

        reports.save(draft.copy(workCompleted = "Edited", delayReason = DelayReason.WEATHER, crewCount = 3, hours = 7.5))
        entries.addNote(job.id, "Later note")
        val again = reports.draftFor(job.id, day)
        assertEquals("Edited", again.workCompleted)
        assertEquals(DelayReason.WEATHER, again.delayReason)
        assertEquals(3, again.crewCount)
        assertEquals(7.5, again.hours ?: 0.0, 0.0)
        assertEquals(1, db.dailyReportDao().getAll().size)
    }

    @Test fun `profile is a single row updated in place`() = runTest {
        assertNull(reports.getProfile())
        reports.saveProfile(" Acme ", " ", "a@b.c", null)
        val second = reports.saveProfile("Acme Co", "555", null, "media/logo.jpg")
        val saved = reports.observeProfile().first()
        assertEquals("Acme Co", saved?.name)
        assertEquals("555", saved?.phone)
        assertNull(saved?.email)
        assertEquals(second.id, saved?.id)
        assertEquals(1, listOf(db.companyProfileDao().getFirst()).size)
    }
}
