package com.kurtz.homerecord.fieldlog.acceptance

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.SystemClock
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaCache
import com.kurtz.homerecord.core.media.MediaCacheSettings
import com.kurtz.homerecord.core.media.MediaTransferApi
import com.kurtz.homerecord.core.media.MediaUploader
import com.kurtz.homerecord.core.media.Presigned
import com.kurtz.homerecord.core.media.PutOutcome
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.pages.PageSources
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.files.FileViewerProblem
import com.kurtz.homerecord.fieldlog.files.FileViewerScreen
import com.kurtz.homerecord.fieldlog.files.FileViewerUiState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Field Log files and media on a device: the PDF viewer over a real PdfRenderer, the download prompt for a file that
 * is not on the phone, and the upload queue and download cache over an on-disk Room database and the real file
 * system. The network is the one fake: R2 is not reachable from the emulator suite. Rules: MediaUploaderTest.
 */
@RunWith(AndroidJUnit4::class)
class FilesAndMediaAcceptanceTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dir by lazy { File(context.cacheDir, "acceptance-files").apply { deleteRecursively(); mkdirs() } }
    private val dbName = "acceptance-media.db"

    @After fun tearDown() {
        dir.deleteRecursively()
        context.deleteDatabase(dbName)
    }

    private fun show(state: FileViewerUiState) = rule.setContent {
        CompositionLocalProvider(LocalTagPrefix provides "fl") {
            HomeRecordTheme(Brand.FieldLog, dynamicColor = false) { FileViewerScreen(state, onEvent = {}) }
        }
    }

    private fun planPdf(pages: Int): File {
        val file = File(dir, "garage-subpanel-plan.pdf")
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 24f }
        repeat(pages) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(612, 792, i + 1).create())
            page.canvas.drawText("Sheet E-${i + 1}: garage subpanel", 72f, 96f, paint)
            page.canvas.drawRect(72f, 140f, 540f, 700f, paint.apply { style = Paint.Style.STROKE })
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    @AcceptanceCriterion("AC-F19")
    @Test fun aPdfPlanOpensWithPagesAndThumbnailsAndTheFirstPageRendersUnderTwoSeconds() {
        val pdf = planPdf(pages = 3)
        val pages = PageSources.open(pdf.path, "application/pdf")
        assertThat(pages).isNotNull()
        val source = pages ?: return
        assertThat(source.pageCount).isEqualTo(3)
        val started = SystemClock.elapsedRealtime()
        val first = runBlocking { source.render(0, 1080) }
        assertThat(first).isNotNull()
        assertThat(SystemClock.elapsedRealtime() - started).isLessThan(2_000L)

        show(FileViewerUiState(loading = false, name = pdf.name, path = pdf.path, mime = "application/pdf", pages = source))
        rule.onNodeWithTag("fl_file_pages").assertIsDisplayed()
        rule.onNodeWithTag("fl_file_thumbnails").assertIsDisplayed()
        rule.onNodeWithTag("fl_file_share").assertIsDisplayed()
    }

    @AcceptanceCriterion("AC-F20")
    @Test fun aFileNotOnThePhoneOffersTheDownloadInsteadOfAViewer() {
        show(FileViewerUiState(loading = false, name = "Panel schedule.pdf", mime = "application/pdf", problem = FileViewerProblem.NotOnPhone))
        rule.onNodeWithTag("fl_file_retry").assertIsDisplayed()
        rule.onNodeWithTag("fl_file_pages").assertDoesNotExist()
    }

    private class Transfer : MediaTransferApi {
        val puts = mutableListOf<String>()
        val outcomes = ArrayDeque<PutOutcome>()
        val downloads = mutableListOf<String>()

        override suspend fun presign(tenant: TenantRef, entity: String, entityId: String, filename: String, size: Long, mime: String) =
            ApiResult.Ok(Presigned("${tenant.id}/$entity/$entityId/$filename", "https://r2.test/put/${puts.size}", Long.MAX_VALUE))

        override suspend fun presignGet(tenant: TenantRef, key: String) = ApiResult.Ok("https://r2.test/get/$key")

        override suspend fun put(url: String, file: File, mime: String): PutOutcome {
            puts += url
            return outcomes.removeFirstOrNull() ?: PutOutcome.Ok
        }

        override suspend fun download(url: String, dest: File): Boolean {
            downloads += url
            dest.writeBytes(ByteArray(100))
            return true
        }
    }

    private fun openDb() = Room.databaseBuilder(context, HomeRecordDatabase::class.java, dbName).build()

    @AcceptanceCriterion("AC-F54")
    @Test fun anUploadCutOffByTheNetworkSurvivesARestartAndGoesUpOnce() = runBlocking<Unit> {
        val photo = File(dir, "panel.jpg").apply { writeBytes(ByteArray(2_048)) }
        val tenant = TenantRef(TenantKind.Company, "c-qa-electric")
        val net = Transfer().apply { outcomes += PutOutcome.Offline }
        var clock = 1_000_000L

        val first = openDb()
        val id = MediaUploader(first.mediaUploadDao(), net, { clock }).run {
            val queued = enqueue(tenant, "photo", "p-panel", photo.name, photo.path, "image/jpeg")
            assertThat(runPending().uploaded).isEqualTo(0)
            queued
        }
        assertThat(first.mediaUploadDao().get(id)?.state).isEqualTo("queued")
        first.close()

        // A new process: the queue comes back from disk, the backoff passes, the file goes up, and queuing it again does nothing.
        clock += 60 * 60_000L
        val second = openDb()
        val restarted = MediaUploader(second.mediaUploadDao(), net, { clock })
        restarted.recover()
        assertThat(restarted.runPending().uploaded).isEqualTo(1)
        assertThat(restarted.enqueue(tenant, "photo", "p-panel", photo.name, photo.path, "image/jpeg")).isEqualTo(id)
        assertThat(restarted.runPending().uploaded).isEqualTo(0)
        assertThat(net.puts).hasSize(2)
        second.close()
    }

    @AcceptanceCriterion("AC-F54")
    @Test fun downloadsAreCachedOnceAndTheCacheKeepsToItsCap() = runBlocking<Unit> {
        val settings = object : MediaCacheSettings { override var capBytes = 250L }
        val net = Transfer()
        val cache = MediaCache(File(dir, "media-cache"), settings, net)
        val a = cache.open("c1/file/a.pdf") { "https://r2.test/get/a" }
        assertThat(a).isNotNull()
        a?.setLastModified(1_000)
        assertThat(cache.open("c1/file/a.pdf") { error("already cached") }).isEqualTo(a)
        cache.open("c1/file/b.pdf") { "https://r2.test/get/b" }
        cache.open("c1/file/c.pdf") { "https://r2.test/get/c" }
        assertThat(net.downloads).hasSize(3)
        assertThat(a?.exists()).isFalse()
        assertThat(cache.sizeBytes()).isAtMost(250L)
    }
}
