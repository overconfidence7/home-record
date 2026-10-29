package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.DocumentFiles
import com.kurtz.homerecord.core.media.StoredDocument
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.documents.DocumentViewerUiState
import com.kurtz.homerecord.housemanual.documents.DocumentViewerViewModel
import com.kurtz.homerecord.coreui.pages.PageSource
import com.kurtz.homerecord.housemodel.documents.DocumentRepository
import com.kurtz.homerecord.housemodel.documents.DocumentScope
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
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

private class OneDocument : DocumentFiles {
    override suspend fun import(uri: String) = StoredDocument("documents/a.pdf", "Furnace manual.pdf", "application/pdf", 2048)
    override fun discard(path: String) = Unit
}

private class TwoPages : PageSource {
    var closed = false
    override val pageCount = 2
    override suspend fun render(index: Int, widthPx: Int): ImageBitmap? = null
    override fun close() { closed = true }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DocumentViewerViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: DocumentRepository
    private val vms = mutableListOf<DocumentViewerViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = DocumentRepository(db, OneDocument())
    }

    @After fun tearDown() {
        runBlocking { vms.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(id: String, open: (String, String) -> PageSource?) = DocumentViewerViewModel(repo, id, open).also {
        vms += it
        it.state.launchIn(it.viewModelScope)
    }

    private fun DocumentViewerViewModel.await(what: (DocumentViewerUiState) -> Boolean) = runBlocking { withTimeout(5_000) { state.first(what) } }

    @AcceptanceCriterion("AC-C37")
    @Test fun opensStoredDocumentAsPages() {
        val id = checkNotNull(runBlocking { repo.add("h1", DocumentScope.Home, "u/manual.pdf") })
        val s = vm(id) { _, _ -> TwoPages() }.await { !it.loading }
        assertThat(s.missing).isFalse()
        assertThat(s.name).isEqualTo("Furnace manual.pdf")
        assertThat(s.pages?.pageCount).isEqualTo(2)
        assertThat(s.path).isEqualTo("documents/a.pdf")
    }

    @AcceptanceCriterion("AC-C37")
    @Test fun missingFileOrRecordShowsMissingState() {
        val id = checkNotNull(runBlocking { repo.add("h1", DocumentScope.Home, "u/manual.pdf") })
        assertThat(vm(id) { _, _ -> null }.await { !it.loading }.missing).isTrue()
        assertThat(vm("nope") { _, _ -> TwoPages() }.await { !it.loading }.missing).isTrue()
    }
}
