package com.kurtz.homerecord.fieldlog.jobs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.JobRepository
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
class JobsViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var jobs: JobRepository
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        jobs = JobRepository(db, Source.FIELD_LOG)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun `archived jobs hide until the toggle is on (AC-54)`() = runTest(dispatcher) {
        val a = jobs.createJob("Active")
        val b = jobs.createJob("Done")
        jobs.setArchived(b.id, true)
        val vm = JobsViewModel(jobs)
        val hidden = vm.state.first { it.loaded }
        assertEquals(listOf(a.id), hidden.jobs.map { it.job.id })
        assertEquals(1, hidden.archivedCount)
        vm.onEvent(JobsEvent.ShowArchived(true))
        val shown = vm.state.first { it.showArchived && it.jobs.size == 2 }
        assertEquals(setOf(a.id, b.id), shown.jobs.map { it.job.id }.toSet())
    }

    @Test fun `empty database loads with no jobs`() = runTest(dispatcher) {
        val vm = JobsViewModel(jobs)
        val loaded = vm.state.first { it.loaded }
        assertTrue(loaded.jobs.isEmpty())
    }
}
