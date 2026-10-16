package com.kurtz.homerecord.fieldlog.jobedit

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
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class JobEditViewModelTest {
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

    private fun vm(id: String? = null) = JobEditViewModel(jobs, id) { it.name }

    @Test fun `save needs a non-space name (AC-52)`() = runTest(dispatcher) {
        val vm = vm()
        assertFalse(vm.state.value.canSave)
        vm.onEvent(JobEditEvent.NameChanged("   "))
        assertFalse(vm.state.value.canSave)
        vm.onEvent(JobEditEvent.Save)
        advanceUntilIdle()
        assertTrue(db.jobDao().getAll().isEmpty())
        vm.onEvent(JobEditEvent.NameChanged("Elm"))
        assertTrue(vm.state.value.canSave)
    }

    @Test fun `save creates job, home and chosen rooms (AC-52)`() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(JobEditEvent.NameChanged("Elm"))
        vm.onEvent(JobEditEvent.ToggleRoom(QuickRoom.KITCHEN))
        vm.onEvent(JobEditEvent.CustomInputChanged("Sunroom"))
        vm.onEvent(JobEditEvent.AddCustomRoom)
        vm.onEvent(JobEditEvent.Save)
        vm.done.first()
        val job = db.jobDao().getAll().single()
        assertEquals("Elm", db.homeDao().get(job.homeId)?.name)
        assertEquals(listOf("KITCHEN", "Sunroom"), jobs.getRooms(jobs.getJob(job.id)!!).map { it.name })
    }

    @Test fun `editing loads the job and archive persists (AC-54)`() = runTest(dispatcher) {
        val job = jobs.createJob("Elm", clientName = "Pat")
        val vm = vm(job.id)
        assertEquals("Pat", vm.state.first { it.name.isNotEmpty() }.clientName)
        vm.onEvent(JobEditEvent.ArchivedChanged(true))
        vm.onEvent(JobEditEvent.Save)
        vm.done.first()
        assertTrue(jobs.getJob(job.id)!!.archived)
    }

    @Test fun `duplicate custom rooms are ignored`() {
        val vm = vm()
        repeat(2) {
            vm.onEvent(JobEditEvent.CustomInputChanged("Porch"))
            vm.onEvent(JobEditEvent.AddCustomRoom)
        }
        assertEquals(listOf("Porch"), vm.state.value.customRooms)
    }
}
