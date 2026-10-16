package com.kurtz.homerecord.core.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.model.EntryKind
import com.kurtz.homerecord.core.model.LogEntry
import com.kurtz.homerecord.core.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class JobRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var jobs: JobRepository
    private var tick = 100L

    @Before fun setUp() {
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        jobs = JobRepository(db, Source.FIELD_LOG, Clock { tick++ })
    }

    @After fun tearDown() = db.close()

    @Test fun `createJob makes a job, a same-named home and the rooms (AC-52)`() = runTest {
        val job = jobs.createJob("  Elm St Remodel ", address = " 1 Elm ", roomNames = listOf("Kitchen", " ", "kitchen", "Garage"))
        assertEquals("Elm St Remodel", job.name)
        assertEquals("1 Elm", job.address)
        assertEquals(Source.FIELD_LOG, job.source)
        val home = db.homeDao().get(job.homeId)
        assertEquals("Elm St Remodel", home?.name)
        assertEquals(listOf("Kitchen", "Garage"), jobs.getRooms(job).map { it.name })
    }

    @Test fun `blank job name is rejected and creates nothing`() = runTest {
        runCatching { jobs.createJob("   ") }
        assertTrue(db.jobDao().getAll().isEmpty())
        assertTrue(db.homeDao().getAll().isEmpty())
    }

    @Test fun `update renames the home too and rejects blank`() = runTest {
        val job = jobs.createJob("A")
        assertFalse(jobs.updateJob(job.copy(name = " ")))
        assertTrue(jobs.updateJob(job.copy(name = "B", clientName = " Pat ", clientContact = "")))
        val saved = jobs.getJob(job.id)
        assertEquals("B", saved?.name)
        assertEquals("Pat", saved?.clientName)
        assertNull(saved?.clientContact)
        assertEquals("B", db.homeDao().get(job.homeId)?.name)
    }

    @Test fun `summaries sort by latest entry and archive flag round-trips (AC-53, AC-54)`() = runTest {
        val first = jobs.createJob("First")
        val second = jobs.createJob("Second")
        assertEquals(listOf("Second", "First"), jobs.observeSummaries().first().map { it.job.name })
        db.logEntryDao().upsert(
            LogEntry("e1", first.id, "2026-10-13", EntryKind.NOTE, "hi", createdAt = 9_000, updatedAt = 9_000, source = Source.FIELD_LOG).toEntity(),
        )
        assertEquals(listOf("First", "Second"), jobs.observeSummaries().first().map { it.job.name })
        jobs.setArchived(second.id, true)
        assertTrue(jobs.observeSummaries().first().first { it.job.id == second.id }.job.archived)
    }
}
