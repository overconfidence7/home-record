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
}
