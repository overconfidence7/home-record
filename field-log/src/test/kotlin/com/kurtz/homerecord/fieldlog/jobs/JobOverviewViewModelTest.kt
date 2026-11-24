package com.kurtz.homerecord.fieldlog.jobs

import com.kurtz.homerecord.fieldlog.ViewModelScopes
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F48: "Start the house" on the job's House tab creates its home through the ViewModel, once.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobOverviewViewModelTest {
    private val scopes = ViewModelScopes()
    private lateinit var db: HomeRecordDatabase

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking { db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Kitchen remodel", createdAt = 1L, updatedAt = 1L)) }
    }

    @After fun tearDown() {
        scopes.cancelAll()
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(provision: suspend (String) -> String?) = scopes.track(JobOverviewViewModel(
        "c1", "j1", db.jobDao(), db.clientDao(), db.jobCrewDao(), db.memberDao(), db.assignmentDao(), db.taskDao(),
        db.punchDao(), db.photoDao(), flowOf(RoleGates(CompanyRole.Owner)), provision,
    ))

    // AC-F48
    @Test fun startHouseProvisionsTheJobHomeOnceWhileItRuns() {
        val asked = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val vm = vm { id -> asked += id; gate.await(); "h1" }
        vm.startHouse()
        vm.startHouse() // a second tap while the first runs
        assertThat(asked).containsExactly("j1")
        gate.complete(Unit)
        vm.startHouse() // the first finished, so a retry is allowed
        assertThat(asked).containsExactly("j1", "j1")
    }

    // AC-U4: a read that throws shows the error state with Retry instead of ending the ViewModel; Retry reads again.
    @com.kurtz.homerecord.core.testing.AcceptanceCriterion("AC-U4")
    @Test fun aFailedReadShowsTheErrorStateAndRetryReadsAgain() {
        var reads = 0
        val real = db.clientDao()
        val clients = object : com.kurtz.homerecord.core.data.ClientDao by real {
            override fun observeFor(parentId: String) = kotlinx.coroutines.flow.flow {
                if (reads++ == 0) throw IllegalStateException("database disk image is malformed")
                emitAll(real.observeFor(parentId))
            }
        }
        val vm = scopes.track(JobOverviewViewModel(
            "c1", "j1", db.jobDao(), clients, db.jobCrewDao(), db.memberDao(), db.assignmentDao(), db.taskDao(),
            db.punchDao(), db.photoDao(), flowOf(RoleGates(CompanyRole.Owner)),
        ))
        val failed = runBlocking { withTimeout(5_000) { vm.state.first { it.loadFailed } } }
        assertThat(failed.loading).isFalse()
        vm.retry()
        val read = runBlocking { withTimeout(5_000) { vm.state.first { !it.loading && !it.loadFailed } } }
        assertThat(read.name).isEqualTo("Kitchen remodel")
    }
}
