package com.kurtz.homerecord.fieldlog.files

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobFileEntity
import com.kurtz.homerecord.core.photo.JobFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-F19: a picked file is stored and queued for upload; AC-F23: folders, rename, delete and search by name
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FilesViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private val uploaded = java.util.concurrent.CopyOnWriteArrayList<JobFileEntity>()
    private lateinit var vm: FilesViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        val files = JobFiles(db)
        vm = FilesViewModel(FilesDeps("j1", db.jobFileDao(), files, "m1", queueUpload = { uploaded += it }))
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** Room emits on its own executor, so wait for the state to reach [until] instead of reading it once. */
    private fun current(until: (FilesUiState) -> Boolean = { true }): FilesUiState = runBlocking {
        withTimeout(5_000) { vm.state.first { !it.loading && until(it) } }
    }

    private fun add(name: String) = vm.added(ImportedFile(name, "/media/$name", "application/pdf", 1_000))

    @Test fun addedFileLandsInOpenFolderAndIsQueuedForUpload() {
        add("plan.pdf")
        assertThat(current { it.files.isNotEmpty() }.files.map { it.name }).containsExactly("plan.pdf")
        runBlocking { withTimeout(5_000) { while (uploaded.isEmpty()) kotlinx.coroutines.delay(10) } }
        assertThat(uploaded.single().name).isEqualTo("plan.pdf")
        assertThat(uploaded.single().author).isEqualTo("m1")
    }

    @Test fun newFolderThenOpenItAndAddThere() {
        vm.onEvent(FilesEvent.CreateFolder("Permits"))
        assertThat(current { it.folders.isNotEmpty() }.folders).containsExactly("Permits")
        vm.onEvent(FilesEvent.OpenFolder("Permits"))
        add("permit.pdf")
        assertThat(current { it.folder == "Permits" }.folder).isEqualTo("Permits")
        assertThat(current { it.files.isNotEmpty() }.files.map { it.name }).containsExactly("permit.pdf")
        vm.onEvent(FilesEvent.Up)
        assertThat(current { it.folder == "" && it.folders.isNotEmpty() }.files).isEmpty()
        assertThat(current().folders).containsExactly("Permits")
    }

    @Test fun duplicateFolderShowsNotice() {
        vm.onEvent(FilesEvent.CreateFolder("Permits"))
        current { it.folders.isNotEmpty() }
        vm.onEvent(FilesEvent.CreateFolder("Permits"))
        assertThat(current { it.notice != null }.notice).isEqualTo(FilesNotice.FolderExists)
        vm.onEvent(FilesEvent.DismissNotice)
        assertThat(current { it.notice == null }.notice).isNull()
    }

    @Test fun unreadableFileShowsNotice() {
        vm.added(null)
        assertThat(current().notice).isEqualTo(FilesNotice.AddFailed)
    }

    @Test fun renameAndDeleteAskFirst() {
        add("plan.pdf")
        val id = current { it.files.isNotEmpty() }.files.single().id
        vm.onEvent(FilesEvent.AskRename(id))
        assertThat(current { it.dialog != null }.dialog).isEqualTo(FilesDialog.Rename(id, "plan.pdf"))
        vm.onEvent(FilesEvent.Rename(id, "Floor plan.pdf"))
        current { it.dialog == null }
        assertThat(current { it.files.single().name == "Floor plan.pdf" }.files.single().name).isEqualTo("Floor plan.pdf")
        vm.onEvent(FilesEvent.AskDelete(id))
        assertThat(current { it.dialog != null }.dialog).isInstanceOf(FilesDialog.Delete::class.java)
        vm.onEvent(FilesEvent.Delete(id))
        assertThat(current { it.files.isEmpty() && it.dialog == null }.files).isEmpty()
    }

    @Test fun searchFindsByNameAcrossFolders() {
        vm.onEvent(FilesEvent.CreateFolder("Permits"))
        add("Floor plan.pdf")
        current { it.folders.isNotEmpty() && it.files.isNotEmpty() }
        vm.onEvent(FilesEvent.OpenFolder("Permits"))
        add("Electrical permit.pdf")
        current { it.folder == "Permits" && it.files.isNotEmpty() }
        vm.onEvent(FilesEvent.Up)
        vm.onEvent(FilesEvent.Query("PERMIT"))
        assertThat(current { it.results.isNotEmpty() }.results.map { it.name }).containsExactly("Electrical permit.pdf")
        vm.onEvent(FilesEvent.Query(""))
        assertThat(current().results).isEmpty()
    }

    @Test fun uploadStatusAndOfflineToggleReachTheRow() {
        val status = kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, com.kurtz.homerecord.core.photo.UploadStatus>())
        val toggled = mutableListOf<Pair<String, Boolean>>()
        var result = com.kurtz.homerecord.core.photo.OnPhone.Ready
        val files = JobFiles(db)
        val vm2 = FilesViewModel(FilesDeps("j2", db.jobFileDao(), files, "m1", uploads = status, setOffline = { id, on -> toggled += id to on; result }))
        runBlocking { files.add("j2", "", "plan.pdf", "/m/plan.pdf", "application/pdf", 10, "m1") }
        val row = runBlocking { withTimeout(5_000) { vm2.state.first { it.files.isNotEmpty() } } }.files.single()
        assertThat(row.upload).isEqualTo(com.kurtz.homerecord.core.photo.UploadStatus.None)
        status.value = mapOf(row.id to com.kurtz.homerecord.core.photo.UploadStatus.Uploading)
        assertThat(runBlocking { withTimeout(5_000) { vm2.state.first { it.files.firstOrNull()?.upload == com.kurtz.homerecord.core.photo.UploadStatus.Uploading } } }.files).hasSize(1)
        vm2.onEvent(FilesEvent.ToggleOffline(row.id, false))
        assertThat(toggled).containsExactly(row.id to false)
        result = com.kurtz.homerecord.core.photo.OnPhone.NoNetwork
        vm2.onEvent(FilesEvent.ToggleOffline(row.id, true))
        assertThat(runBlocking { withTimeout(5_000) { vm2.state.first { it.notice != null } } }.notice).isEqualTo(FilesNotice.DownloadFailed)
    }

    // AC-F3: Crew and Sub only view; a stray add, folder, rename or delete changes nothing and the state says so
    @Test fun crewCannotAddRenameOrDelete() {
        val files = JobFiles(db)
        runBlocking { files.add("j3", "", "plan.pdf", "/m/plan.pdf", "application/pdf", 10, "m1") }
        val crew = FilesViewModel(FilesDeps("j3", db.jobFileDao(), files, "m2", canManage = kotlinx.coroutines.flow.flowOf(false)))
        val s = runBlocking { withTimeout(5_000) { crew.state.first { it.files.isNotEmpty() } } }
        assertThat(s.canManage).isFalse()
        val id = s.files.single().id
        crew.added(ImportedFile("extra.pdf", "/media/extra.pdf", "application/pdf", 1))
        crew.onEvent(FilesEvent.SetAddMenu(true))
        crew.onEvent(FilesEvent.CreateFolder("Permits"))
        crew.onEvent(FilesEvent.AskRename(id))
        crew.onEvent(FilesEvent.Rename(id, "Renamed.pdf"))
        crew.onEvent(FilesEvent.AskDelete(id))
        crew.onEvent(FilesEvent.Delete(id))
        runBlocking { kotlinx.coroutines.delay(200) }
        val rows = runBlocking { db.jobFileDao().listFor("j3") }
        assertThat(rows.map { it.name }).containsExactly("plan.pdf")
        assertThat(crew.state.value.dialog).isNull()
        assertThat(crew.state.value.addMenuOpen).isFalse()
        assertThat(uploaded).isEmpty()
    }

    // rule 13: Add file and New folder close the FAB menu they were picked from
    @Test fun addMenuOpensAndClosesOnEitherChoice() {
        vm.onEvent(FilesEvent.SetAddMenu(true))
        assertThat(current { it.canManage && it.addMenuOpen }.addMenuOpen).isTrue()
        vm.onEvent(FilesEvent.NewFolder)
        val folder = current { !it.addMenuOpen }
        assertThat(folder.dialog).isEqualTo(FilesDialog.NewFolder)
        vm.onEvent(FilesEvent.DismissDialog)
        vm.onEvent(FilesEvent.SetAddMenu(true))
        current { it.addMenuOpen }
        vm.onEvent(FilesEvent.AddFile)
        assertThat(current { !it.addMenuOpen }.dialog).isNull()
    }

    // AC-F22: a scanned document lands as one PDF in the open folder, named by date and numbered when the name is taken
    @Test fun scannedPdfIsNamedByDateAndNumberedInTheOpenFolder() {
        vm.onEvent(FilesEvent.SetAddMenu(true))
        current { it.addMenuOpen }
        vm.onEvent(FilesEvent.ScanDocument)
        assertThat(current { !it.addMenuOpen }.addMenuOpen).isFalse()
        vm.scanned(ImportedFile("a.pdf", "/media/a.pdf", "application/pdf", 9_000), "Scan Oct 31, 2026")
        current { it.files.size == 1 }
        vm.scanned(ImportedFile("b.pdf", "/media/b.pdf", "application/pdf", 9_000), "Scan Oct 31, 2026")
        val rows = current { it.files.size == 2 }.files
        assertThat(rows.map { it.name }).containsExactly("Scan Oct 31, 2026.pdf", "Scan Oct 31, 2026 (2).pdf")
        assertThat(rows.map { it.mime }.toSet()).containsExactly("application/pdf")
        runBlocking { withTimeout(5_000) { while (uploaded.size < 2) kotlinx.coroutines.delay(10) } }
    }

    // AC-F22
    @Test fun scanNameSkipsTakenNamesIgnoringCase() {
        assertThat(FilesViewModel.scanName("Scan Oct 31, 2026", emptySet())).isEqualTo("Scan Oct 31, 2026.pdf")
        assertThat(FilesViewModel.scanName("Scan Oct 31, 2026", setOf("scan oct 31, 2026.PDF", "Scan Oct 31, 2026 (2).pdf"))).isEqualTo("Scan Oct 31, 2026 (3).pdf")
    }

    // AC-F22: a scan that gives nothing back says so and adds nothing
    @Test fun failedScanShowsNotice() {
        vm.scanned(null, "")
        val state = current { it.notice != null }
        assertThat(state.notice).isEqualTo(FilesNotice.ScanFailed)
        assertThat(state.files).isEmpty()
    }
}
