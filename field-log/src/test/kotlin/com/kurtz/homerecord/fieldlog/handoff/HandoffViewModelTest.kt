package com.kurtz.homerecord.fieldlog.handoff

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.HandoffRepository
import com.kurtz.homerecord.core.repo.JobRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HandoffViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: HomeRecordDatabase
    private lateinit var jobs: JobRepository
    private var bundlePath: String? = "shared/h.homerecord"
    private val calls = mutableListOf<String>()
    private val exporter = object : HandoffExporter {
        override suspend fun exportBundle(jobId: String): String? = bundlePath.also { calls += "bundle" }
        override suspend fun exportPdf(jobId: String): String? = "shared/h.pdf".also { calls += "pdf" }
    }

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        jobs = JobRepository(db, Source.FIELD_LOG)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun vm(jobId: String) =
        HandoffViewModel(jobId, jobs, HandoffRepository(db), exporter).also { v -> v.state.first { it.loaded } }

    @Test fun `loads counts for the job's home (AC-71)`() = runTest(dispatcher) {
        val job = jobs.createJob("Elm", roomNames = listOf("Kitchen", "Bath"))
        val state = vm(job.id).state.value
        assertEquals("Elm", state.jobName)
        assertEquals(2, state.counts?.rooms)
    }

    @Test fun `unknown job loads with no counts`() = runTest(dispatcher) {
        val state = vm("nope").state.value
        assertNull(state.counts)
        assertTrue(state.loaded)
    }

    @Test fun `export bundle exposes a share until acknowledged (AC-72)`() = runTest(dispatcher) {
        val v = vm(jobs.createJob("Elm").id)
        v.onEvent(HandoffEvent.ExportBundle)
        advanceUntilIdle()
        assertEquals(HandoffShare("shared/h.homerecord", HandoffFormat.BUNDLE), v.state.value.ready)
        v.onEvent(HandoffEvent.Shared)
        assertNull(v.state.value.ready)
    }

    @Test fun `export pdf and failure flag`() = runTest(dispatcher) {
        val v = vm(jobs.createJob("Elm").id)
        v.onEvent(HandoffEvent.ExportPdf)
        advanceUntilIdle()
        assertEquals(HandoffFormat.PDF, v.state.value.ready?.format)
        bundlePath = null
        v.onEvent(HandoffEvent.Shared)
        v.onEvent(HandoffEvent.ExportBundle)
        advanceUntilIdle()
        assertTrue(v.state.value.failed)
        assertFalse(v.state.value.exporting)
        assertEquals(listOf("pdf", "bundle"), calls)
    }
}
