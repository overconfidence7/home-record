package com.kurtz.homerecord.fieldlog.acceptance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.photo.Annotation
import com.kurtz.homerecord.core.photo.AnnotationTool
import com.kurtz.homerecord.core.photo.JobFiles
import com.kurtz.homerecord.core.photo.NormPoint
import com.kurtz.homerecord.core.photo.PdfMark
import com.kurtz.homerecord.core.photo.PdfMarkup
import com.kurtz.homerecord.core.punch.NewPunch
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.punch.PunchRepository
import com.kurtz.homerecord.core.sync.SyncPhase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.capture.CameraAccess
import com.kurtz.homerecord.fieldlog.capture.CaptureEvent
import com.kurtz.homerecord.fieldlog.capture.CaptureScreen
import com.kurtz.homerecord.fieldlog.capture.CaptureUiState
import com.kurtz.homerecord.fieldlog.capture.GhostShot
import com.kurtz.homerecord.fieldlog.photos.AnnotateEvent
import com.kurtz.homerecord.fieldlog.photos.AnnotateScreen
import com.kurtz.homerecord.fieldlog.photos.AnnotateUiState
import com.kurtz.homerecord.fieldlog.photos.PhotoCell
import com.kurtz.homerecord.fieldlog.photos.PhotoDayUi
import com.kurtz.homerecord.fieldlog.photos.PhotoDetailEvent
import com.kurtz.homerecord.fieldlog.photos.PhotoDetailScreen
import com.kurtz.homerecord.fieldlog.photos.PhotoDetailUiState
import com.kurtz.homerecord.fieldlog.photos.PhotoTile
import com.kurtz.homerecord.fieldlog.photos.PhotosScreen
import com.kurtz.homerecord.fieldlog.photos.PhotosUiState
import com.kurtz.homerecord.fieldlog.today.TodayScreen
import com.kurtz.homerecord.fieldlog.today.TodayUiState
import com.kurtz.homerecord.fieldlog.widget.ClockWidget
import com.kurtz.homerecord.fieldlog.widget.ClockWidgetReceiver
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/**
 * Field Log acceptance on a device for the photo, widget, PDF markup and offline criteria whose rules have JVM tests
 * (GhostPickerTest, AnnotateViewModelTest, PdfMarkupTest, ClockWidgetStateBuilderTest, PunchRepositoryTest): here the
 * screens, the widget registration, Room and the file system are the real ones.
 */
@RunWith(AndroidJUnit4::class)
class PhotosClockAndOfflineAcceptanceTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show(content: @Composable () -> Unit) = rule.setContent {
        CompositionLocalProvider(LocalTagPrefix provides "fl") {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { content() }
        }
    }

    private fun tile(id: String) = PhotoTile(id, "/none/$id.jpg", "Kitchen", "Rough-in")

    @AcceptanceCriterion("AC-F25")
    @Test fun afterShotShowsTheBeforeAsAGhostOnlyWhenTurnedOn() {
        val events = mutableListOf<CaptureEvent>()
        var state by mutableStateOf(
            CaptureUiState(
                loading = false, jobName = "Harlan kitchen remodel", access = CameraAccess.Granted,
                ghost = GhostShot("p-before", "/none/before.jpg"),
            ),
        )
        show { CaptureScreen(state, { events += it }, onBack = {}, onPickGallery = {}, camera = { Box(it) }) }
        rule.onNodeWithTag("fl_capture_ghost").assertDoesNotExist()
        rule.onNodeWithTag("fl_capture_ghost_toggle").assertIsDisplayed().performClick()
        assertThat(events).contains(CaptureEvent.ToggleGhost)
        state = state.copy(ghostOn = true)
        rule.onNodeWithTag("fl_capture_ghost").assertExists()
    }

    @AcceptanceCriterion("AC-F25")
    @Test fun beforeAndAfterSitSideBySideInTheGallery() {
        show {
            PhotosScreen(
                PhotosUiState(
                    loading = false, count = 3,
                    days = listOf(PhotoDayUi(LocalDate.of(2026, 10, 21), listOf(PhotoCell.Pair(tile("b1"), tile("a1")), PhotoCell.Single(tile("p1"))))),
                ),
                onEvent = {},
            )
        }
        val before = rule.onNodeWithTag("fl_photos_tile_b1").getUnclippedBoundsInRoot()
        val after = rule.onNodeWithTag("fl_photos_tile_a1").getUnclippedBoundsInRoot()
        assertThat(after.top).isEqualTo(before.top)
        assertThat(after.left).isGreaterThan(before.right)
    }

    @AcceptanceCriterion("AC-F25")
    @Test fun aPhotoIsMarkedBeforeFromItsPage() {
        val events = mutableListOf<PhotoDetailEvent>()
        var state by mutableStateOf(
            PhotoDetailUiState(loading = false, imagePath = "/none/p1.jpg", caption = "Panel before rough-in", takenAt = 1_761_000_000_000),
        )
        show { PhotoDetailScreen(state, { events += it }, onShare = {}, onAnnotate = {}, onBack = {}) }
        rule.onNodeWithTag("fl_photo_mark_before").assertIsOff().performSemanticsAction(SemanticsActions.OnClick)
        assertThat(events).contains(PhotoDetailEvent.ToggleBefore)
        state = state.copy(isBefore = true)
        rule.onNodeWithTag("fl_photo_mark_before").assertIsOn()
    }

    @AcceptanceCriterion("AC-F26")
    @Test fun annotateOffersEveryToolUndoAndSavesACopy() {
        val events = mutableListOf<AnnotateEvent>()
        var state by mutableStateOf(AnnotateUiState(loading = false, imagePath = "/none/p1.jpg"))
        show { AnnotateScreen(state, { events += it }, onClose = {}) }
        rule.onNodeWithTag("fl_annotate_canvas").assertIsDisplayed()
        rule.onNodeWithTag("fl_annotate_undo").assertIsNotEnabled()
        rule.onNodeWithTag("fl_save").assertIsNotEnabled()
        AnnotationTool.entries.forEach { t ->
            rule.onNodeWithTag("fl_annotate_tool_${t.name.lowercase()}").performSemanticsAction(SemanticsActions.OnClick)
        }
        assertThat(events.filterIsInstance<AnnotateEvent.Tool>().map { it.tool }).containsExactlyElementsIn(AnnotationTool.entries).inOrder()
        state = state.copy(marks = listOf(Annotation.Arrow(NormPoint(.1f, .1f), NormPoint(.4f, .4f))))
        rule.onNodeWithTag("fl_annotate_undo").assertIsEnabled().performClick()
        rule.onNodeWithTag("fl_save").assertIsEnabled().performClick()
        assertThat(events).containsAtLeast(AnnotateEvent.Undo, AnnotateEvent.Save).inOrder()
    }

    @AcceptanceCriterion("AC-F17")
    @Test fun clockWidgetIsInstalledAsTwoByTwoAndOpensTheClock() {
        val providers = AppWidgetManager.getInstance(context).getInstalledProvidersForPackage(context.packageName, null)
        val clock = providers.singleOrNull { it.provider.className == ClockWidgetReceiver::class.java.name }
        assertThat(clock).isNotNull()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertThat(clock?.targetCellWidth).isEqualTo(2)
            assertThat(clock?.targetCellHeight).isEqualTo(2)
        }
        // The widget's button starts MainActivity with this extra, which asks the shell for the Clock tab.
        assertThat(ClockWidget.OPEN_CLOCK.name).isEqualTo("open_clock")
    }

    @AcceptanceCriterion("AC-F21")
    @Test fun pdfMarkupSavesANewVersionAndLeavesTheOriginalFileUntouched() = runBlocking<Unit> {
        val dir = File(context.cacheDir, "acf21").apply { deleteRecursively(); mkdirs() }
        val original = File(dir, "plan.pdf")
        PdfDocument().run {
            finishPage(startPage(PdfDocument.PageInfo.Builder(612, 792, 1).create()))
            original.outputStream().use { writeTo(it) }
            close()
        }
        val bytes = original.readBytes()
        val db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).build()
        try {
            var clock = 1_000L
            val plan = JobFiles(db, { clock }).add("j1", "Plans", "Plan.pdf", original.path, PdfMarkup.PDF_MIME, original.length(), "Sam")
            val markup = PdfMarkup(
                db, { src, _, dest -> File(src).copyTo(File(dest), overwrite = true); true }, { dir.path },
                { _, _, _, _, _, _ -> }, { "co1" }, now = { clock },
            )
            clock = 2_000L
            val id = markup.saveVersion(plan.id, listOf(PdfMark.Arrow(0, NormPoint(.1f, .1f), NormPoint(.5f, .5f))), "Maria")
            assertThat(id).isNotNull()
            val v2 = db.jobFileDao().get(requireNotNull(id))
            assertThat(v2?.version).isEqualTo(2)
            assertThat(v2?.author).isEqualTo("Maria")
            assertThat(v2?.createdAt).isEqualTo(2_000L)
            assertThat(v2?.path?.let { File(it).exists() }).isTrue()
            assertThat(original.readBytes()).isEqualTo(bytes)
            assertThat(markup.history(id).map { it.version }).containsExactly(2, 1).inOrder()
        } finally {
            db.close()
            dir.deleteRecursively()
        }
    }

    @AcceptanceCriterion("AC-F52")
    @Test fun punchSavesWithNoNetworkAndTodayCountsWhatIsWaiting() = runBlocking<Unit> {
        val db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).build()
        try {
            val queued = mutableListOf<Pair<String, String>>()
            val repo = PunchRepository(db, { 1_000L }, { e, id -> queued += e to id })
            val id = repo.record(NewPunch("c1", "m1", "j1", PunchKind.IN, at = 1_000L))
            assertThat(db.punchDao().get(id)).isNotNull()
            assertThat(queued).containsExactly("punch" to id)
        } finally {
            db.close()
        }
        show { TodayScreen(TodayUiState("QA Electric", CompanyRole.Owner, SyncPhase.Offline, pendingChanges = 2), onInviteCrew = {}) }
        rule.onNodeWithText("Offline · 2 changes waiting").assertIsDisplayed()
    }
}
