package com.kurtz.homerecord.fieldlog.acceptance

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.photo.JobFiles
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.files.FilesDeps
import com.kurtz.homerecord.fieldlog.files.FilesScreen
import com.kurtz.homerecord.fieldlog.files.FilesUiState
import com.kurtz.homerecord.fieldlog.files.FilesViewModel
import com.kurtz.homerecord.fieldlog.files.importScan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Document scanner into Files on a device (AC-F22): the Files FAB menu offers Scan a document, and the multi-page PDF
 * the ML Kit scanner hands back is copied into app storage and listed in the open folder as one PDF with every page.
 * The scanner UI itself is Google Play services' and is not driven here. Rules: FilesViewModelTest.
 */
@RunWith(AndroidJUnit4::class)
class DocumentScanAcceptanceTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dir by lazy { File(context.cacheDir, "acceptance-scan").apply { deleteRecursively(); mkdirs() } }
    private val copies = mutableListOf<File>()

    @After fun tearDown() {
        dir.deleteRecursively()
        copies.forEach { it.delete() }
    }

    /** Stands in for the scanner's result: a PDF with one page per scanned sheet, in a cache the app doesn't own long. */
    private fun scannerPdf(pages: Int): File {
        val file = File(dir, "DOC-20261031.pdf")
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 24f }
        repeat(pages) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(612, 792, i + 1).create())
            page.canvas.drawText("Permit E-2026-${i + 1}: garage subpanel", 72f, 96f, paint)
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    private fun pageCount(file: File) =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }

    @AcceptanceCriterion("AC-F22")
    @Test fun theFilesMenuOffersScanADocument() {
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "fl") {
                HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { FilesScreen(FilesUiState(loading = false, addMenuOpen = true), {}) }
            }
        }
        rule.onNodeWithTag("fl_files_scan").assertIsDisplayed()
    }

    @AcceptanceCriterion("AC-F22")
    @Test fun aThreePageScanLandsInFilesAsOnePdfWithEveryPage() = runBlocking<Unit> {
        val scanned = scannerPdf(pages = 3)
        val imported = importScan(context, Uri.fromFile(scanned))
        assertThat(imported).isNotNull()
        val file = imported ?: return@runBlocking
        val copy = File(file.path).also { copies += it }
        scanned.delete()
        assertThat(copy.exists()).isTrue()
        assertThat(pageCount(copy)).isEqualTo(3)

        val db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).build()
        try {
            val vm = withContext(Dispatchers.Main) { FilesViewModel(FilesDeps("j-garage", db.jobFileDao(), JobFiles(db), "m-owner")) }
            withContext(Dispatchers.Main) { vm.scanned(file, "Scan Oct 31, 2026") }
            val row = withTimeout(10_000) { vm.state.first { it.files.isNotEmpty() } }.files.single()
            assertThat(row.name).isEqualTo("Scan Oct 31, 2026.pdf")
            assertThat(row.mime).isEqualTo("application/pdf")
            assertThat(db.jobFileDao().listFor("j-garage").single().path).isEqualTo(copy.absolutePath)
        } finally {
            db.close()
        }
    }
}
