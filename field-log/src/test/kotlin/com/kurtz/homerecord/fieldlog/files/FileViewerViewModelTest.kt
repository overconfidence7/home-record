package com.kurtz.homerecord.fieldlog.files

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.photo.JobFiles
import com.kurtz.homerecord.coreui.pages.PageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
import java.io.File

// AC-F19: the viewer opens a PDF as pages; AC-F20: a file that is not on the phone shows a prompt instead of a blank page
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class FileViewerViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var files: JobFiles
    private val opened = mutableListOf<Pair<String, String>>()

    private val pages = object : PageSource {
        override val pageCount = 3
        override suspend fun render(index: Int, widthPx: Int): ImageBitmap? = null
    }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        files = JobFiles(db)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewer(id: String, open: (String, String) -> PageSource? = { p, m -> opened += p to m; pages }) =
        FileViewerViewModel(FileViewerDeps(id, db.jobFileDao(), open))

    private fun FileViewerViewModel.loaded() = runBlocking { withTimeout(5_000) { state.first { !it.loading } } }

    private fun stored(name: String, mime: String, exists: Boolean = true): String {
        val f = File.createTempFile("viewer", name).apply { deleteOnExit() }
        if (!exists) f.delete()
        val row = runBlocking { files.add("j1", "", name, f.absolutePath, mime, 10, "m1") }
        return row.id
    }

    @Test fun pdfOpensAsPages() {
        val s = viewer(stored("plan.pdf", "application/pdf")).loaded()
        assertThat(s.problem).isNull()
        assertThat(s.pages?.pageCount).isEqualTo(3)
        assertThat(s.name).isEqualTo("plan.pdf")
        assertThat(opened.single().second).isEqualTo("application/pdf")
    }

    @Test fun photoOpensAsOnePage() {
        val s = viewer(stored("site.jpg", "image/jpeg")).loaded()
        assertThat(s.problem).isNull()
        assertThat(s.pages).isNotNull()
    }

    @Test fun fileNotOnPhoneShowsPromptAndOpensNothing() {
        val s = viewer(stored("permit.pdf", "application/pdf", exists = false)).loaded()
        assertThat(s.problem).isEqualTo(FileViewerProblem.NotOnPhone)
        assertThat(s.pages).isNull()
        assertThat(opened).isEmpty()
    }

    @Test fun otherKindsCanOnlyBeShared() {
        val s = viewer(stored("quote.xlsx", "application/vnd.ms-excel")).loaded()
        assertThat(s.problem).isEqualTo(FileViewerProblem.NoPreview)
        assertThat(s.path).isNotEmpty()
    }

    @Test fun damagedPdfIsUnreadable() {
        val s = viewer(stored("bad.pdf", "application/pdf")) { _, _ -> null }.loaded()
        assertThat(s.problem).isEqualTo(FileViewerProblem.Unreadable)
    }

    @Test fun deletedOrUnknownFileIsMissing() {
        assertThat(viewer("nope").loaded().problem).isEqualTo(FileViewerProblem.Missing)
        val id = stored("gone.pdf", "application/pdf")
        runBlocking { files.delete(id) }
        assertThat(viewer(id).loaded().problem).isEqualTo(FileViewerProblem.Missing)
    }

    @Test fun fileNotOnPhoneDownloadsOnOpenThenShowsPages() {
        val id = stored("permit.pdf", "application/pdf", exists = false)
        val downloaded = File.createTempFile("download", ".pdf").apply { deleteOnExit() }
        val vm = FileViewerViewModel(FileViewerDeps(id, db.jobFileDao(), { _, _ -> pages }, ensureOnPhone = {
            files.setLocal(it, false, downloaded.absolutePath)
            com.kurtz.homerecord.core.photo.OnPhone.Ready
        }))
        val s = vm.loaded()
        assertThat(s.problem).isNull()
        assertThat(s.pages?.pageCount).isEqualTo(3)
    }

    @Test fun downloadThatFailsShowsPromptAndRetryTriesAgain() {
        val id = stored("permit.pdf", "application/pdf", exists = false)
        var calls = 0
        val vm = FileViewerViewModel(FileViewerDeps(id, db.jobFileDao(), { _, _ -> pages }, ensureOnPhone = {
            calls++
            com.kurtz.homerecord.core.photo.OnPhone.NoNetwork
        }))
        assertThat(vm.loaded().problem).isEqualTo(FileViewerProblem.NotOnPhone)
        vm.onRetry()
        runBlocking { withTimeout(5_000) { while (calls < 2) kotlinx.coroutines.delay(10) } }
        assertThat(vm.loaded().problem).isEqualTo(FileViewerProblem.NotOnPhone)
    }
}
