package com.kurtz.homerecord.fieldlog.files

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.photo.JobFiles
import com.kurtz.homerecord.core.photo.NormPoint
import com.kurtz.homerecord.core.photo.PdfMark
import com.kurtz.homerecord.core.photo.PdfMarkup
import com.kurtz.homerecord.core.photo.PdfTool
import com.kurtz.homerecord.coreui.pages.PageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

// AC-F21: markup on a PDF page (pen, arrow, rectangle, text, highlight) saves as a new version with the author and time;
// version history lists and opens each; the original is never modified
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MarkupViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var files: JobFiles
    private lateinit var markup: PdfMarkup
    private val written = mutableListOf<List<PdfMark>>()
    private var writeOk = true
    private var clock = 1_000L

    private val pages = object : PageSource {
        override val pageCount = 3
        override suspend fun render(index: Int, widthPx: Int): ImageBitmap? = null
    }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        files = JobFiles(db, { clock })
        markup = PdfMarkup(db, { _, marks, _ -> written += marks; writeOk }, { "/files/media" }, { _, _, _, _, _, _ -> }, { "co1" }, { 7L }, { clock })
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun plan(mime: String = PdfMarkup.PDF_MIME) = runBlocking { files.add("j1", "Plans", "Garage plan.pdf", "/m/plan.pdf", mime, 10, "owner") }.id

    private fun editor(id: String, onPhone: Boolean = true, open: (String, String) -> PageSource? = { _, _ -> pages }) =
        MarkupViewModel(MarkupDeps(id, db.jobFileDao(), markup, "lead-1", open, isOnPhone = { onPhone }))

    private fun MarkupViewModel.loaded() = runBlocking { withTimeout(5_000) { state.first { !it.loading } } }
    private fun MarkupViewModel.saved() = runBlocking { withTimeout(5_000) { state.first { !it.saving && (it.savedId != null || it.failed) } } }
    private fun MarkupViewModel.drag(vararg pts: Pair<Float, Float>) = onEvent(MarkupEvent.Gesture(pts.map { NormPoint(it.first, it.second) }))

    @Test fun everyToolMarksThePageShownAndSaveMakesVersionTwoByTheAuthor() {
        val original = plan()
        val vm = editor(original)
        assertThat(vm.loaded().pageCount).isEqualTo(3)
        vm.onEvent(MarkupEvent.Tool(PdfTool.Pen)); vm.drag(0.1f to 0.1f, 0.2f to 0.2f, 0.3f to 0.1f)
        vm.onEvent(MarkupEvent.Tool(PdfTool.Highlight)); vm.drag(0.5f to 0.5f, 0.1f to 0.4f)
        vm.onEvent(MarkupEvent.NextPage)
        vm.onEvent(MarkupEvent.Tool(PdfTool.Arrow)); vm.drag(0.1f to 0.1f, 0.9f to 0.9f)
        vm.onEvent(MarkupEvent.Tool(PdfTool.Rectangle)); vm.drag(0.2f to 0.2f, 0.4f to 0.4f)
        vm.onEvent(MarkupEvent.Tool(PdfTool.Text)); vm.drag(0.3f to 0.6f)
        assertThat(vm.state.value.pendingText).isEqualTo(NormPoint(0.3f, 0.6f))
        vm.onEvent(MarkupEvent.PendingValue("  Move panel  "))
        vm.onEvent(MarkupEvent.ConfirmText)
        val s = vm.state.value
        assertThat(s.marks.map { it::class }).containsExactly(
            PdfMark.Pen::class, PdfMark.Highlight::class, PdfMark.Arrow::class, PdfMark.Rect::class, PdfMark.Text::class,
        ).inOrder()
        assertThat(s.marks.map { it.page }).containsExactly(0, 0, 1, 1, 1).inOrder()
        assertThat((s.marks.last() as PdfMark.Text).text).isEqualTo("Move panel")

        clock = 5_000L
        vm.onEvent(MarkupEvent.Save)
        val id = checkNotNull(vm.saved().savedId)
        val v2 = checkNotNull(runBlocking { db.jobFileDao().get(id) })
        assertThat(v2.version).isEqualTo(2)
        assertThat(v2.author).isEqualTo("lead-1")
        assertThat(v2.createdAt).isEqualTo(5_000L)
        assertThat(v2.parentFileId).isEqualTo(original)
        assertThat(written.single()).isEqualTo(s.marks)
        assertThat(runBlocking { db.jobFileDao().get(original) }?.path).isEqualTo("/m/plan.pdf")
        assertThat(vm.state.value.canSave).isFalse()
    }

    @Test fun undoTakesBackTheLastMarkOnThisPageOnly() {
        val vm = editor(plan()).also { it.loaded() }
        vm.drag(0.1f to 0.1f, 0.2f to 0.2f)
        vm.onEvent(MarkupEvent.NextPage)
        assertThat(vm.state.value.canUndo).isFalse()
        vm.onEvent(MarkupEvent.Undo)
        assertThat(vm.state.value.marks).hasSize(1)
        vm.onEvent(MarkupEvent.PreviousPage)
        vm.onEvent(MarkupEvent.Undo)
        assertThat(vm.state.value.marks).isEmpty()
        assertThat(vm.state.value.canSave).isFalse()
    }

    @Test fun pagesStopAtTheFirstAndLast() {
        val vm = editor(plan()).also { it.loaded() }
        vm.onEvent(MarkupEvent.PreviousPage)
        assertThat(vm.state.value.page).isEqualTo(0)
        repeat(5) { vm.onEvent(MarkupEvent.NextPage) }
        assertThat(vm.state.value.page).isEqualTo(2)
    }

    @Test fun emptyTextIsNotAddedAndAFailedWriteKeepsTheMarks() {
        val vm = editor(plan()).also { it.loaded() }
        vm.onEvent(MarkupEvent.Tool(PdfTool.Text)); vm.drag(0.5f to 0.5f)
        vm.onEvent(MarkupEvent.PendingValue("   ")); vm.onEvent(MarkupEvent.ConfirmText)
        assertThat(vm.state.value.marks).isEmpty()
        assertThat(vm.state.value.pendingText).isNull()
        vm.onEvent(MarkupEvent.Tool(PdfTool.Arrow)); vm.drag(0.1f to 0.1f, 0.5f to 0.5f)
        writeOk = false
        vm.onEvent(MarkupEvent.Save)
        assertThat(vm.saved().failed).isTrue()
        assertThat(vm.state.value.savedId).isNull()
        assertThat(vm.state.value.marks).hasSize(1)
        assertThat(vm.state.value.canSave).isTrue()
    }

    @Test fun onlyAPdfOnThePhoneCanBeMarkedUp() {
        assertThat(editor(plan(mime = "image/jpeg")).loaded().problem).isEqualTo(MarkupProblem.NotAvailable)
        assertThat(editor(plan(), onPhone = false).loaded().problem).isEqualTo(MarkupProblem.NotAvailable)
        assertThat(editor(plan(), open = { _, _ -> null }).loaded().problem).isEqualTo(MarkupProblem.Unreadable)
        assertThat(editor("gone").loaded().problem).isEqualTo(MarkupProblem.Missing)
    }

    @Test fun viewerListsVersionsWithNamesAndOffersMarkUpToThoseWhoManageFiles() {
        val v1 = plan()
        val f = java.io.File.createTempFile("plan", ".pdf").apply { deleteOnExit() }
        runBlocking { db.jobFileDao().upsert(checkNotNull(db.jobFileDao().get(v1)).copy(path = f.path)) }
        clock = 2_000L
        val v2 = checkNotNull(runBlocking { markup.saveVersion(v1, listOf(PdfMark.Arrow(0, NormPoint(0f, 0f), NormPoint(1f, 1f))), "lead-1") })
        val names = mapOf("lead-1" to "Marcus Ortiz", "owner" to "Dana Reyes")
        fun viewer(id: String, manage: Boolean) = FileViewerViewModel(
            FileViewerDeps(id, db.jobFileDao(), { _, _ -> pages }, history = markup::history, memberName = { names[it] }, canManage = { flowOf(manage) }),
        )
        // v1's path exists; v2's path is under /files/media and is not on this test disk, so open v1 for the markable case
        val s = runBlocking { withTimeout(5_000) { viewer(v1, manage = true).state.first { !it.loading && it.canMarkUp } } }
        assertThat(s.versions.map { it.version }).containsExactly(2, 1).inOrder()
        assertThat(s.versions.map { it.id }).containsExactly(v2, v1).inOrder()
        assertThat(s.versions.map { it.author }).containsExactly("Marcus Ortiz", "Dana Reyes").inOrder()
        assertThat(s.versions.single { it.shown }.id).isEqualTo(v1)
        assertThat(s.canMarkUp).isTrue()
        val crew = runBlocking { withTimeout(5_000) { viewer(v1, manage = false).state.first { !it.loading } } }
        assertThat(crew.canMarkUp).isFalse()
    }
}
