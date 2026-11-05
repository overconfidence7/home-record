package com.kurtz.homerecord.fieldlog.handoff

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.bundle.BundleRecord
import com.kurtz.homerecord.core.bundle.ExportedHome
import com.kurtz.homerecord.core.bundle.HomeBundle
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

/** AC-F51: FL-Handoff counts the job's house and writes the `.homerecord` for the share sheet. */
@OptIn(ExperimentalCoroutinesApi::class)
@AcceptanceCriterion("AC-F51")
class HandoffViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    private fun rec(id: String) = BundleRecord(buildJsonObject { put("id", id); put("createdAt", 1L); put("updatedAt", 1L) })

    private fun home(rooms: Int) = ExportedHome(
        HomeBundle(source = "field-log", exportedAt = 1L, homeId = "h1", records = mapOf("room" to List(rooms) { rec("r$it") })),
        emptyMap(),
    )

    @Test fun loadsTheSummaryOfTheJobsHouse() { // AC-F51
        val vm = HandoffViewModel("Hale kitchen", load = { home(3) }, write = { null })
        val s = vm.state.value
        assertThat(s.loading).isFalse()
        assertThat(s.jobName).isEqualTo("Hale kitchen")
        assertThat(s.summary?.rooms).isEqualTo(3)
    }

    @Test fun exportReadsTheHouseAgainAndHandsTheFileToTheShareSheet() { // AC-F51
        var rooms = 1
        val written = mutableListOf<ExportedHome>()
        val vm = HandoffViewModel("Hale kitchen", load = { home(rooms) }, write = { written += it; File("/cache/handoff/Hale kitchen 2026-11-05.homerecord") })
        rooms = 2
        vm.onEvent(HandoffEvent.Export)
        assertThat(written.single().bundle.count("room")).isEqualTo(2)
        assertThat(vm.state.value.sharePath).endsWith("Hale kitchen 2026-11-05.homerecord")
        assertThat(vm.state.value.summary?.rooms).isEqualTo(2)
        vm.onEvent(HandoffEvent.ShareHandled)
        assertThat(vm.state.value.sharePath).isNull()
    }

    @Test fun aFileThatCannotBeWrittenSaysSoAndExportCanBeTriedAgain() { // AC-F51
        var fail = true
        val vm = HandoffViewModel("Hale kitchen", load = { home(1) }, write = { if (fail) error("disk full") else File("/cache/handoff/x.homerecord") })
        vm.onEvent(HandoffEvent.Export)
        assertThat(vm.state.value.failed).isTrue()
        assertThat(vm.state.value.exporting).isFalse()
        fail = false
        vm.onEvent(HandoffEvent.Export)
        assertThat(vm.state.value.failed).isFalse()
        assertThat(vm.state.value.sharePath).isNotNull()
    }

    @Test fun anEmptyHouseOrAMissingOneIsNotExported() { // AC-F51
        var writes = 0
        val empty = HandoffViewModel("Garage subpanel", load = { home(0) }, write = { writes++; File("x") })
        empty.onEvent(HandoffEvent.Export)
        val gone = HandoffViewModel("Garage subpanel", load = { null }, write = { writes++; File("x") })
        gone.onEvent(HandoffEvent.Export)
        assertThat(writes).isEqualTo(0)
        assertThat(gone.state.value.missing).isTrue()
    }
}
