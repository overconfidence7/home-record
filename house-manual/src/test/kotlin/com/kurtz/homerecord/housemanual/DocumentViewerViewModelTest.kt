package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.DocumentFiles
import com.kurtz.homerecord.core.media.StoredDocument
import com.kurtz.homerecord.core.photo.OnPhone
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

    private fun vm(id: String, fetch: suspend (String) -> OnPhone = { OnPhone.NotUploaded }, open: (String, String) -> PageSource?) =
        DocumentViewerViewModel(repo, id, open, fetch).also {
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

    // T075a2: a document added on another phone has path "" here; the viewer fetches it from the cloud, then opens it.
    @AcceptanceCriterion("AC-H15")
    @Test fun documentOnlyInTheCloudIsFetchedThenOpened() {
        val id = checkNotNull(runBlocking { repo.add("h1", DocumentScope.Home, "u/permit.pdf") })
        runBlocking { db.documentDao().upsert(checkNotNull(db.documentDao().get(id)).copy(path = "", r2Key = "h1/document/$id/a.pdf")) }
        val asked = mutableListOf<String>()
        val opened = mutableListOf<String>()
        val s = vm(id, fetch = { asked += it; db.documentDao().setLocalPath(it, "/cache/downloads/a.pdf"); OnPhone.Ready }) { path, _ -> opened += path; TwoPages() }
            .await { !it.loading }
        assertThat(asked).containsExactly(id)
        assertThat(opened).containsExactly("/cache/downloads/a.pdf")
        assertThat(s.missing).isFalse()
        assertThat(s.path).isEqualTo("/cache/downloads/a.pdf")
    }

    // T075a2: with no connection the viewer says so, and Retry fetches again once the phone is back online.
    @AcceptanceCriterion("AC-H15")
    @Test fun offlineShowsNoConnectionAndRetryFetchesAgain() {
        val id = checkNotNull(runBlocking { repo.add("h1", DocumentScope.Home, "u/permit.pdf") })
        runBlocking { db.documentDao().upsert(checkNotNull(db.documentDao().get(id)).copy(path = "", r2Key = "h1/document/$id/a.pdf")) }
        var online = false
        val viewer = vm(id, fetch = {
            if (online) { db.documentDao().setLocalPath(it, "/cache/downloads/a.pdf"); OnPhone.Ready } else OnPhone.NoNetwork
        }) { _, _ -> TwoPages() }
        val first = viewer.await { !it.loading }
        assertThat(first.missing).isTrue()
        assertThat(first.offline).isTrue()
        online = true
        viewer.onEvent(com.kurtz.homerecord.housemanual.documents.DocumentViewerEvent.Retry)
        val second = viewer.await { !it.loading && !it.missing }
        assertThat(second.offline).isFalse()
        assertThat(second.pages?.pageCount).isEqualTo(2)
    }

    // A file that is gone with no cloud copy is still "can't be opened", not "no connection".
    @AcceptanceCriterion("AC-C37")
    @Test fun goneFileWithNoCloudCopyIsMissingNotOffline() {
        val id = checkNotNull(runBlocking { repo.add("h1", DocumentScope.Home, "u/manual.pdf") })
        val s = vm(id) { _, _ -> null }.await { !it.loading }
        assertThat(s.missing).isTrue()
        assertThat(s.offline).isFalse()
    }
}
