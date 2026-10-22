package com.kurtz.homerecord.fieldlog.jobs

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F5: a new job with client and address saves offline and is queued for sync; coordinates resolve later.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobEditTest {
    private lateinit var db: HomeRecordDatabase
    private val changes = mutableListOf<Pair<String, String>>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** Room hops to its own executor, so wait for the form to finish loading, as the screen does. */
    private suspend fun vm(jobId: String? = null, saved: SavedStateHandle = SavedStateHandle()): JobEditViewModel {
        val vm = JobEditViewModel("c1", jobId, db.jobDao(), db.clientDao(), { e, id -> changes += e to id }, saved, { "2026-10-22" }, { 1000L })
        withContext(Dispatchers.Default) { withTimeout(5000) { vm.state.first { !it.loading } } }
        return vm
    }

    private suspend fun JobEditViewModel.save() {
        onEvent(JobEditEvent.Save)
        withContext(Dispatchers.Default) { withTimeout(5000) { state.first { it.saved } } }
    }

    @Test fun nameIsRequiredAndDatesMustBeInOrder() {
        assertThat(JobDraft().canSave).isFalse()
        assertThat(JobDraft(name = "Panel").canSave).isTrue()
        assertThat(JobDraft(name = "Panel", startDate = "2026-11-02", endDate = "2026-11-01").canSave).isFalse()
        assertThat(JobDraft(name = "Panel", budgetHoursText = "12x").canSave).isFalse()
    }

    @Test fun statusFollowsTheDates() {
        assertThat(JobDraft(startDate = "2026-11-01").statusFor("2026-10-22")).isEqualTo("UPCOMING")
        assertThat(JobDraft(endDate = "2026-10-01").statusFor("2026-10-22")).isEqualTo("DONE")
        assertThat(JobDraft(startDate = "2026-10-01").statusFor("2026-10-22")).isEqualTo("ACTIVE")
    }

    @Test fun savingANewJobCreatesTheClientAndQueuesBoth() = runTest {
        val vm = vm()
        vm.onEvent(JobEditEvent.Name("Panel upgrade"))
        vm.onEvent(JobEditEvent.Client("Rivera family"))
        vm.onEvent(JobEditEvent.Address("418 Maple St"))
        vm.onEvent(JobEditEvent.Budget("40"))
        vm.save()
        val job = db.jobDao().listFor("c1").single()
        assertThat(job.name).isEqualTo("Panel upgrade")
        assertThat(job.budgetHours).isEqualTo(40)
        assertThat(job.lat).isNull()
        assertThat(db.clientDao().get(job.clientId!!)?.name).isEqualTo("Rivera family")
        assertThat(changes.map { it.first }).containsExactly("client", "job").inOrder()
    }

    @Test fun anExistingClientIsReusedIgnoringCase() = runTest {
        repeat(2) { n ->
            val vm = vm()
            vm.onEvent(JobEditEvent.Name("Job $n"))
            vm.onEvent(JobEditEvent.Client(if (n == 0) "Okafor" else "okafor"))
            vm.save()
        }
        assertThat(db.clientDao().listFor("c1")).hasSize(1)
        assertThat(db.jobDao().listFor("c1")).hasSize(2)
    }

    @Test fun editingKeepsCoordinatesUntilTheAddressChanges() = runTest {
        val first = vm()
        first.onEvent(JobEditEvent.Name("Deck"))
        first.onEvent(JobEditEvent.Address("1 Elm"))
        first.save()
        val id = db.jobDao().listFor("c1").single().id
        db.jobDao().upsert(db.jobDao().get(id)!!.copy(lat = 39.7, lng = -84.2))

        val same = vm(id)
        same.onEvent(JobEditEvent.Notes("Bring ladder"))
        same.save()
        assertThat(db.jobDao().get(id)?.lat).isEqualTo(39.7)

        val moved = vm(id)
        moved.onEvent(JobEditEvent.Address("2 Oak"))
        moved.save()
        assertThat(db.jobDao().get(id)?.lat).isNull()
    }

    @Test fun unsavedTextSurvivesProcessDeath() = runTest {
        val handle = SavedStateHandle()
        vm(saved = handle).onEvent(JobEditEvent.Name("Half typed"))
        assertThat(vm(saved = handle).state.value.draft.name).isEqualTo("Half typed")
    }
}
