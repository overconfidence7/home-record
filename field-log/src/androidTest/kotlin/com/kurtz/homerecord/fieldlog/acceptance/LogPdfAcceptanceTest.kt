package com.kurtz.homerecord.fieldlog.acceptance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.CompanyEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.log.DailyLogRepository
import com.kurtz.homerecord.core.model.CrewLine
import com.kurtz.homerecord.core.model.LogSection
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.fieldlog.log.LogPdfMaker
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The daily log PDF on a device (AC-F33): the real PdfDocument writes a readable file with the photos drawn on it, the
 * email goes to the client and office addresses on the job, and Send marks the log Sent with time and recipients; a
 * second send replaces them. Rules: LogPdfLayoutTest, LogRecipientsTest, LogPdfTest, DailyLogRepositoryTest.
 */
@RunWith(AndroidJUnit4::class)
class LogPdfAcceptanceTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var db: HomeRecordDatabase
    private val dir by lazy { File(context.filesDir, "acceptance-log").apply { deleteRecursively(); mkdirs() } }
    private val day = LocalDate.of(2026, 10, 30)

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).build()
    }

    @After fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun sendWritesAReadablePdfToTheClientAndOfficeAndMarksTheLogSent() = runBlocking<Unit> {
        val photo = File(dir, "ph1.jpg")
        Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(210, 110, 30)) }
            .compress(Bitmap.CompressFormat.JPEG, 90, photo.outputStream())
        db.companyDao().upsert(CompanyEntity(id = "c1", name = "Ortiz Electric", phone = "(610) 555-0142", createdAt = 1, updatedAt = 1))
        db.clientDao().upsert(ClientEntity(id = "cl1", companyId = "c1", name = "Dana Hale", email = "dana@halehome.com", createdAt = 1, updatedAt = 1))
        db.jobDao().upsert(
            JobEntity(
                id = "j1", companyId = "c1", clientId = "cl1", name = "Hale kitchen", address = "14 Maple Ave, Wayne, PA",
                officeEmailsJson = "[\"office@ortizelectric.com\",\"DANA@halehome.com\"]", createdAt = 1, updatedAt = 1,
            ),
        )
        db.photoDao().upsert(PhotoEntity(id = "ph1", homeId = "h1", jobId = "j1", path = photo.path, takenAt = 10, createdAt = 10, updatedAt = 10))
        val repo = DailyLogRepository(db, weatherApi = { _, _, _ -> error("no lookup: the job has no location") }, now = { 1_000L })
        val id = checkNotNull(repo.assemble("j1", day)).log.id
        val log = checkNotNull(
            repo.update(id) {
                it.copy(
                    crew = LogSection(listOf(CrewLine("m1", "Maria Soto", 510)), edited = true),
                    photoIds = LogSection(listOf("ph1"), edited = true),
                    clientNotes = "Countertop template is Monday at 9.",
                )
            },
        )

        val made = checkNotNull(LogPdfMaker(context, db).make(log))
        assertThat(made.file.name).isEqualTo("Daily log Hale kitchen 2026-10-30.pdf")
        assertThat(made.recipients).containsExactly("dana@halehome.com", "office@ortizelectric.com").inOrder()
        assertThat(made.subject).isEqualTo("Hale kitchen: Daily log, Friday, October 30, 2026")
        ParcelFileDescriptor.open(made.file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { pdf ->
                assertThat(pdf.pageCount).isEqualTo(1)
                pdf.openPage(0).use { page ->
                    assertThat(page.width).isEqualTo(612)
                    val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    // the photo prints: some pixel of the page carries its orange
                    var orange = false
                    for (y in 0 until bmp.height step 4) for (x in 0 until bmp.width step 4) {
                        val p = bmp.getPixel(x, y)
                        if (Color.red(p) > 180 && Color.green(p) in 80..140 && Color.blue(p) < 70) orange = true
                    }
                    assertThat(orange).isTrue()
                }
            }
        }

        val sent = checkNotNull(repo.markSent(id, made.recipients, made.file.path))
        assertThat(sent.sent).isTrue()
        assertThat(sent.sentAt).isEqualTo(1_000L)
        assertThat(sent.recipients).containsExactly("dana@halehome.com", "office@ortizelectric.com").inOrder()
        val again = checkNotNull(repo.markSent(id, emptyList(), made.file.path))
        assertThat(again.recipients).isEmpty()
    }
}
