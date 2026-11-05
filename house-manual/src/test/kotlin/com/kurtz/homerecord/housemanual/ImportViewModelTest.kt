package com.kurtz.homerecord.housemanual

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.bundle.BundleRecord
import com.kurtz.homerecord.core.bundle.ConflictChoice
import com.kurtz.homerecord.core.bundle.HomeBundle
import com.kurtz.homerecord.core.bundle.ImportPlanner
import com.kurtz.homerecord.core.bundle.ImportPreview
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.importing.ImportError
import com.kurtz.homerecord.housemanual.importing.ImportEvent
import com.kurtz.homerecord.housemanual.importing.ImportKind
import com.kurtz.homerecord.housemanual.importing.ImportRead
import com.kurtz.homerecord.housemanual.importing.ImportViewModel
import com.kurtz.homerecord.housemanual.importing.ReadImport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

// AC-H17: the import preview counts what the handoff brings per kind, lists conflicts (Keep mine by default, AC-C47)
// and imports with the picks.
@AcceptanceCriterion("AC-H17")
@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {
    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun rec(id: String, updatedAt: Long, vararg extra: Pair<String, String>) = BundleRecord(
        JsonObject(mapOf("id" to JsonPrimitive(id), "createdAt" to JsonPrimitive(1), "updatedAt" to JsonPrimitive(updatedAt)) + extra.map { it.first to JsonPrimitive(it.second) }),
    )

    private val bundle = HomeBundle(
        source = "field-log", exportedAt = 9, homeId = "h1",
        records = mapOf(
            "home" to listOf(rec("h1", 1, "name" to "Ortiz house")),
            "room" to listOf(rec("r1", 5, "name" to "Kitchen"), rec("r2", 5, "name" to "Mudroom")),
            "wall_photo" to listOf(rec("wp1", 5), rec("wp2", 5)),
            "overlay" to listOf(rec("o1", 6, "label" to "Supply line")),
        ),
    )

    private fun read(local: Map<String, List<BundleRecord>> = emptyMap()) =
        ReadImport(ImportPreview(bundle, ImportPlanner.plan(bundle, local)), File("unused"), homeName = "Ortiz house")

    @Test fun previewCountsWhatIsNewPerKind() {
        val vm = ImportViewModel(read = { ImportRead.Ok(read()) }, apply = { _, _ -> true })
        val s = vm.state.value
        assertThat(s.loading).isFalse()
        assertThat(s.homeName).isEqualTo("Ortiz house")
        assertThat(s.counts.associate { it.kind to it.count })
            .containsExactly(ImportKind.Rooms, 2, ImportKind.WallPhotos, 2, ImportKind.Overlays, 1).inOrder()
        assertThat(s.conflicts).isEmpty()
        assertThat(s.nothingNew).isFalse()
    }

    @Test fun conflictsKeepMineUnlessUseImportedIsPicked() {
        // This phone renamed the kitchen with no ledger: a conflict.
        val local = mapOf("room" to listOf(rec("r1", 7, "name" to "Our kitchen")))
        var applied: Map<Pair<String, String>, ConflictChoice>? = null
        val saved = SavedStateHandle()
        val vm = ImportViewModel(read = { ImportRead.Ok(read(local)) }, apply = { _, c -> applied = c; true }, saved = saved)
        val conflict = vm.state.value.conflicts.single()
        assertThat(conflict.name).isEqualTo("Kitchen")
        assertThat(conflict.useImported).isFalse()

        vm.onEvent(ImportEvent.Choose("room", "r1", useImported = true))
        // The pick survives process death: a new view model on the same handle reads it back.
        val again = ImportViewModel(read = { ImportRead.Ok(read(local)) }, apply = { _, _ -> true }, saved = saved)
        assertThat(again.state.value.conflicts.single().useImported).isTrue()

        vm.onEvent(ImportEvent.Import)
        assertThat(applied).containsExactly("room" to "r1", ConflictChoice.UseImported)
        assertThat(vm.state.value.done).isTrue()
    }

    @Test fun unreadableFileAndFailedImportSayWhat() {
        val bad = ImportViewModel(read = { ImportRead.Failed(ImportError.NotABundle) }, apply = { _, _ -> true })
        assertThat(bad.state.value.error).isEqualTo(ImportError.NotABundle)

        val failing = ImportViewModel(read = { ImportRead.Ok(read()) }, apply = { _, _ -> false })
        failing.onEvent(ImportEvent.Import)
        assertThat(failing.state.value.error).isEqualTo(ImportError.Failed)
        assertThat(failing.state.value.done).isFalse()
        assertThat(failing.state.value.importing).isFalse()
    }

    @Test fun sameBundleAgainHasNothingNew() {
        val vm = ImportViewModel(read = { ImportRead.Ok(read(local = bundle.records)) }, apply = { _, _ -> true })
        assertThat(vm.state.value.nothingNew).isTrue()
    }
}
