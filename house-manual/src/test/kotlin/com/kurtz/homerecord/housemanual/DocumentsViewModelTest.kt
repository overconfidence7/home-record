package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.DocumentFiles
import com.kurtz.homerecord.core.media.StoredDocument
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.documents.DocumentForm
import com.kurtz.homerecord.housemanual.documents.DocumentsEvent
import com.kurtz.homerecord.housemanual.documents.DocumentsUiState
import com.kurtz.homerecord.housemanual.documents.DocumentsViewModel
import com.kurtz.homerecord.housemodel.documents.DocumentRepository
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

private class FakeDocumentFiles : DocumentFiles {
    val known = mapOf(
        "u/manual.pdf" to StoredDocument("documents/a.pdf", "Furnace manual.pdf", "application/pdf", 2048),
        "u/notes.txt" to StoredDocument("documents/c.txt", "notes.txt", "text/plain", 10),
    )
    override suspend fun import(uri: String) = known[uri]
    override fun discard(path: String) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class DocumentsViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: DocumentRepository
    private val vms = mutableListOf<DocumentsViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = DocumentRepository(db, FakeDocumentFiles())
    }

    @After fun tearDown() {
        runBlocking { vms.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = DocumentsViewModel(repo, "h1", saved).also {
        vms += it
        it.state.launchIn(it.viewModelScope)
    }

    private fun DocumentsViewModel.await(what: (DocumentsUiState) -> Boolean) = runBlocking { withTimeout(5_000) { state.first(what) } }

    @AcceptanceCriterion("AC-C37")
    @Test fun pickedPdfIsListedAndOtherTypesShowNotice() {
        val vm = vm()
        vm.onEvent(DocumentsEvent.Picked("u/notes.txt"))
        vm.await { it.unsupportedNotice }
        vm.onEvent(DocumentsEvent.Picked("u/manual.pdf"))
        val s = vm.await { it.folders.isNotEmpty() && !it.unsupportedNotice }
        assertThat(s.folders.single().documents.map { it.name }).containsExactly("Furnace manual.pdf")
    }

    @AcceptanceCriterion("AC-C37")
    @Test fun renameAndMoveToFolderThenDelete() {
        val vm = vm()
        vm.onEvent(DocumentsEvent.Picked("u/manual.pdf"))
        val id = vm.await { it.folders.isNotEmpty() }.folders.single().documents.single().id
        vm.onEvent(DocumentsEvent.OpenForm(id))
        vm.onEvent(DocumentsEvent.ChangeForm(DocumentForm(id, "  ", "Appliances")))
        vm.onEvent(DocumentsEvent.SaveForm)
        vm.await { it.showFormErrors }
        vm.onEvent(DocumentsEvent.ChangeForm(DocumentForm(id, "Furnace manual", "Appliances")))
        vm.onEvent(DocumentsEvent.SaveForm)
        val s = vm.await { it.form == null && it.folders.singleOrNull()?.name == "Appliances" }
        assertThat(s.folders.single().documents.single().name).isEqualTo("Furnace manual")
        vm.onEvent(DocumentsEvent.AskDelete(id))
        vm.onEvent(DocumentsEvent.ConfirmDelete)
        vm.await { it.folders.isEmpty() && it.confirmDeleteId == null }
    }

    @AcceptanceCriterion("AC-C37")
    @Test fun openFormSurvivesProcessDeath() {
        val saved = SavedStateHandle()
        val first = vm(saved)
        first.onEvent(DocumentsEvent.Picked("u/manual.pdf"))
        val id = first.await { it.folders.isNotEmpty() }.folders.single().documents.single().id
        first.onEvent(DocumentsEvent.OpenForm(id))
        first.onEvent(DocumentsEvent.ChangeForm(DocumentForm(id, "Manual", "Permits")))
        val second = vm(saved)
        second.await { it.form != null }
        assertThat(second.state.value.form).isEqualTo(DocumentForm(id, "Manual", "Permits"))
    }
}
