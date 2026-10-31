package com.kurtz.homerecord.fieldlog.acceptance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.photo.NormPoint
import com.kurtz.homerecord.core.photo.PdfMark
import com.kurtz.homerecord.core.photo.PdfTool
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.pages.PageSource
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.files.AndroidMarkedPdfWriter
import com.kurtz.homerecord.fieldlog.files.FileVersion
import com.kurtz.homerecord.fieldlog.files.FileViewerEvent
import com.kurtz.homerecord.fieldlog.files.FileViewerScreen
import com.kurtz.homerecord.fieldlog.files.FileViewerUiState
import com.kurtz.homerecord.fieldlog.files.MarkupEvent
import com.kurtz.homerecord.fieldlog.files.MarkupScreen
import com.kurtz.homerecord.fieldlog.files.MarkupUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * PDF markup on a device (AC-F21): the real writer over PdfRenderer and PdfDocument makes a new file with the marks on
 * the right page and leaves the original byte for byte; the Mark up screen shows all five tools and the page switcher;
 * FL-File offers Mark up and Version history. Rules: PdfMarkSketchTest, MarkupViewModelTest, PdfMarkupTest.
 */
@RunWith(AndroidJUnit4::class)
class PdfMarkupAcceptanceTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dir by lazy { File(context.cacheDir, "acceptance-markup").apply { deleteRecursively(); mkdirs() } }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    private fun show(content: @Composable () -> Unit) = rule.setContent {
        CompositionLocalProvider(LocalTagPrefix provides "fl") {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { content() }
        }
    }

    private fun planPdf(pages: Int): File {
        val file = File(dir, "garage-plan.pdf")
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 24f }
        repeat(pages) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(612, 792, i + 1).create())
            page.canvas.drawText("Sheet E-${i + 1}: garage subpanel", 72f, 96f, paint)
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun File.sha() = MessageDigest.getInstance("SHA-256").digest(readBytes()).joinToString("") { "%02x".format(it) }

    private fun renderPage(file: File, index: Int): Bitmap =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(index).use { p ->
                    Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }

    private fun isRed(px: Int) = Color.red(px) > 180 && Color.green(px) < 120 && Color.blue(px) < 120

    @Test
    @AcceptanceCriterion("AC-F21")
    fun writerDrawsMarksOnTheirPageIntoANewFileAndLeavesTheOriginal() = runBlocking<Unit> {
        val source = planPdf(2)
        val before = source.sha()
        val dest = File(dir, "v2.pdf")
        val marks = listOf(
            PdfMark.Rect(1, NormPoint(0.2f, 0.4f), NormPoint(0.8f, 0.6f)),
            PdfMark.Highlight(1, NormPoint(0.1f, 0.1f), NormPoint(0.5f, 0.15f)),
            PdfMark.Text(1, NormPoint(0.2f, 0.8f), "Move panel"),
        )
        assertThat(AndroidMarkedPdfWriter.write(source.path, marks, dest.path)).isTrue()
        assertThat(source.sha()).isEqualTo(before)
        val out = ParcelFileDescriptor.open(dest, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { r -> r.pageCount to r.openPage(0).use { it.width to it.height } } }
        assertThat(out.first).isEqualTo(2)
        assertThat(out.second).isEqualTo(612 to 792)
        val marked = renderPage(dest, 1)
        // The box's left edge at x = 0.2 runs from y 0.4 to 0.6; sample its middle.
        val edge = (marked.width * 0.2f).toInt()
        val mid = (marked.height * 0.5f).toInt()
        assertThat((edge - 2..edge + 2).any { isRed(marked.getPixel(it, mid)) }).isTrue()
        val untouched = renderPage(dest, 0)
        assertThat((edge - 2..edge + 2).none { isRed(untouched.getPixel(it, mid)) }).isTrue()
    }

    @Test
    @AcceptanceCriterion("AC-F21")
    fun writerFailsCleanlyOnANonPdf() = runBlocking<Unit> {
        val junk = File(dir, "not-a-pdf.pdf").apply { writeText("hello") }
        val dest = File(dir, "out.pdf")
        assertThat(AndroidMarkedPdfWriter.write(junk.path, listOf(PdfMark.Arrow(0, NormPoint(0f, 0f), NormPoint(1f, 1f))), dest.path)).isFalse()
        assertThat(dest.exists()).isFalse()
    }

    private val pages = object : PageSource {
        override val pageCount = 3
        override suspend fun render(index: Int, widthPx: Int): ImageBitmap? = null
    }

    @Test
    @AcceptanceCriterion("AC-F21")
    fun markupScreenShowsAllFiveToolsAndThePageSwitcher() {
        val events = mutableListOf<MarkupEvent>()
        show { MarkupScreen(MarkupUiState(loading = false, name = "Garage plan.pdf", pages = pages)) { events += it } }
        PdfTool.entries.forEach { rule.onNodeWithTag("fl_markup_tool_${it.name.lowercase()}").assertIsDisplayed() }
        rule.onNodeWithTag("fl_markup_page").assertTextEquals("Page 1 of 3")
        rule.onNodeWithTag("fl_markup_previous").assertIsNotEnabled()
        rule.onNodeWithTag("fl_markup_next").performClick()
        rule.onNodeWithTag("fl_markup_tool_highlight").performClick()
        rule.onNodeWithTag("fl_save").assertIsNotEnabled()
        assertThat(events).containsExactly(MarkupEvent.NextPage, MarkupEvent.Tool(PdfTool.Highlight)).inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-F21")
    fun fileViewerOffersMarkUpAndOpensEachVersionFromHistory() {
        val events = mutableListOf<FileViewerEvent>()
        val state = FileViewerUiState(
            loading = false, name = "Garage plan.pdf", path = "p", mime = "application/pdf", pages = pages, canMarkUp = true,
            versions = listOf(FileVersion("v2", 2, "Marcus Ortiz", 2_000L, shown = true), FileVersion("v1", 1, null, 1_000L, shown = false)),
            showHistory = true,
        )
        show { FileViewerScreen(state, onEvent = { events += it }) }
        rule.onNodeWithTag("fl_file_markup").performClick()
        rule.onNodeWithTag("fl_file_history").assertIsDisplayed()
        rule.onNodeWithTag("fl_file_version_1").performClick()
        assertThat(events).containsExactly(FileViewerEvent.MarkUp, FileViewerEvent.OpenVersion("v1")).inOrder()
    }
}
