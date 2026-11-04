package com.kurtz.homerecord.fieldlog.reports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.CompanyEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.log.LogPdfOp
import com.kurtz.homerecord.core.log.PhotoReportLayout
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.fieldlog.log.AndroidLogPdfWriter
import com.kurtz.homerecord.fieldlog.reports.PhotoReportPdfMaker.Companion.toReportPhoto
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The photo report PDF's words and layout (AC-F28); PdfDocument has no JVM backing, so the drawing is checked on device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoReportPdfTest {
    private val res = ApplicationProvider.getApplicationContext<Context>().resources
    private val zone = ZoneId.of("America/New_York")
    private val company = CompanyEntity(id = "c1", name = "Ortiz Electric", phone = "(610) 555-0142", createdAt = 1, updatedAt = 1)
    private val job = JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", address = "14 Maple Ave, Wayne, PA", createdAt = 1, updatedAt = 1)
    private val client = ClientEntity(id = "k1", companyId = "c1", name = "Dana Hale", createdAt = 1, updatedAt = 1)
    private val from = LocalDate.of(2026, 10, 19)
    private val to = LocalDate.of(2026, 10, 25)
    private fun at(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 10, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private fun photo(id: String, takenAt: Long, room: String? = null, stage: String? = null, caption: String? = null) = PhotoEntity(
        id = id, homeId = "h1", jobId = "j1", roomId = room, stage = stage, caption = caption, path = "/media/$id.jpg",
        takenAt = takenAt, createdAt = takenAt, updatedAt = takenAt,
    )
    private val rooms = mapOf("r1" to "Kitchen")

    @AcceptanceCriterion("AC-F28")
    @Test fun eachPhotoIsCaptionedWithWhenWhereAndItsNote() {
        val photos = listOf(
            photo("a", at(19, 9, 30), room = "r1", stage = "Rough-in", caption = "Panel cover off") to "/media/a.jpg",
            photo("b", at(20, 14, 5), stage = "Inspection") to "/media/b.jpg",
            photo("c", at(21, 8)) to "/media/c.jpg",
        )
        val c = PhotoReportPdfContent.build(res, company, job, client, from, to, photos, { rooms[it] }, zone, fileExists = { true })
        assertThat(c.company).isEqualTo("Ortiz Electric")
        assertThat(c.title).isEqualTo("Photo report, Oct 19, 2026 – Oct 25, 2026")
        assertThat(c.jobLine).isEqualTo("Dana Hale · 14 Maple Ave, Wayne, PA")
        assertThat(c.note).isNull()
        assertThat(c.photos.map { it.caption }).containsExactly(
            listOf("Oct 19, 2026, 9:30 AM", "Kitchen · Rough-in", "Panel cover off"),
            listOf("Oct 20, 2026, 2:05 PM", "Inspection"),
            listOf("Oct 21, 2026, 8:00 AM"),
        ).inOrder()
    }

    @AcceptanceCriterion("AC-F28")
    @Test fun aPhotoStillOnAnotherPhoneIsLeftOutAndCounted() {
        val photos = listOf(photo("a", at(19, 9)) to "/media/a.jpg", photo("b", at(20, 9)) to "", photo("c", at(21, 9)) to "")
        val c = PhotoReportPdfContent.build(res, null, job.copy(address = null), null, from, to, photos, { null }, zone)
        assertThat(c.photos.map { it.path }).containsExactly("/media/a.jpg")
        assertThat(c.note).isEqualTo("2 photos are still on another phone and are not in this report.")
        assertThat(c.jobLine).isNull()
        // the thumbnail stands in when the full photo is not here; neither means another phone
        val p = photo("d", at(22, 9)).copy(path = "", thumbPath = "/media/d_thumb.jpg")
        assertThat(p.toReportPhoto(exists = { it == "/media/d_thumb.jpg" }).path).isEqualTo("/media/d_thumb.jpg")
        assertThat(p.toReportPhoto(exists = { false }).path).isEmpty()
    }

    @Test fun nineteenPhotosLayOutOnFivePagesWithTheAppsFooterAndAFileNameForTheRange() {
        val writer = AndroidLogPdfWriter({ n, m -> "Page $n of $m" })
        val photos = (1..19).map { photo("p$it", at(19, 7) + it * 60_000L, room = "r1", stage = "Finish") to "/media/p$it.jpg" }
        val c = PhotoReportPdfContent.build(res, company, job, client, from, to, photos, { rooms[it] }, zone, fileExists = { false })
        val pages = PhotoReportLayout(writer.measurer, { n, m -> res.getString(com.kurtz.homerecord.fieldlog.R.string.log_pdf_page, n, m) }).layout(c)
        assertThat(pages).hasSize(5)
        assertThat(pages.map { p -> p.filterIsInstance<LogPdfOp.Image>().size }).containsExactly(4, 4, 4, 4, 3).inOrder()
        assertThat(pages.last().filterIsInstance<LogPdfOp.Text>().last().text).contains("5")
        assertThat(PhotoReportPdfMaker.fileName("Hale kitchen / phase 2", from, to)).isEqualTo("Photos Hale kitchen phase 2 2026-10-19 to 2026-10-25.pdf")
    }
}
