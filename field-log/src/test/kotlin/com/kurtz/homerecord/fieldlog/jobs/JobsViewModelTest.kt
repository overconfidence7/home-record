package com.kurtz.homerecord.fieldlog.jobs

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.RoleGates
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
import java.util.concurrent.Executors

// AC-F6, AC-U11: FL-Jobs groups a long job list and builds its rows off the main thread that draws it.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobsViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private val mappingExecutor = Executors.newSingleThreadExecutor { Thread(it, "jobs-mapping") }
    private val mapping = mappingExecutor.asCoroutineDispatcher()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        runBlocking {
            List(200) { i ->
                JobEntity(id = "j$i", companyId = "c1", name = "Panel upgrade ${i + 1}", address = "${100 + i} Maple Ave", createdAt = i.toLong(), updatedAt = i.toLong())
            }.forEach { db.jobDao().upsert(it) }
        }
    }

    @After fun tearDown() {
        db.close()
        mapping.close()
        Dispatchers.resetMain()
    }

    // AC-F6, AC-U11
    @Test fun twoHundredJobsAreGroupedOnTheMappingDispatcher() {
        val mappedOn = mutableListOf<String>()
        val vm = JobsViewModel(
            "c1", db.jobDao(), db.clientDao(), db.assignmentDao(), flowOf(RoleGates(CompanyRole.Owner)), SavedStateHandle(),
            todayProvider = { synchronized(mappedOn) { mappedOn += Thread.currentThread().name.substringBefore(" @") }; "2026-10-31" },
            mapping = mapping,
        )
        val state = runBlocking { withTimeout(10_000) { vm.state.first { !it.loading } } }
        assertThat(state.groups.active).hasSize(200)
        assertThat(state.groups.active.first().supporting).isEqualTo("100 Maple Ave")
        assertThat(synchronized(mappedOn) { mappedOn.toList() }).isNotEmpty()
        assertThat(synchronized(mappedOn) { mappedOn.toSet() }).containsExactly("jobs-mapping")
    }
}
