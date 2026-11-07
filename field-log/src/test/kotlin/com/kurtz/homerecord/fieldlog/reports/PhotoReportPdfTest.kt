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
import com.kurtz.homerecord.core.log.PhotoReportPicker
import com.kurtz.homerecord.core.log.ReportPhotoPick
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.fieldlog.log.AndroidLogPdfWriter
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The photo report PDF's words (AC-F28); PdfDocument has no JVM backing, so the drawing is checked on device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhotoReportPdfTest {
    private val res = ApplicationProvider.getApplicationContext<Context>().resources
    private val zone = ZoneId.of("America/New_York")
    private val company = CompanyEntity(id = "c1", name = "Ortiz Electric", phone = "(610) 555-0142", createdAt = 1, updatedAt = 1)
    private val job = JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", address = "14 Maple Ave, Wayne, PA", createdAt = 1, updatedAt = 1)
    private val client = ClientEntity(id = "k1", companyId = "c1", name = "Dana Hale", createdAt = 1, updatedAt = 1)
    private val from = LocalDate.of(2026, 10, 26)
    private val to = LocalDate.of(2026, 11, 1)
    private fun at(day: Int, hour: Int) = LocalDate.of(2026, 10, day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val picked = PhotoReportPicker.Result(
        listOf(
            ReportPhotoPick("/m/1.jpg", at(27, 9), LocalDate.of(2026, 10, 27), "Kitchen", "Rough-in", "Box fill checked at the island"),
            ReportPhotoPick("/m/2.jpg", at(28, 15), LocalDate.of(2026, 10, 28), null, null, null),
        ),
        leftOut = 1,
    )

    @AcceptanceCriterion("AC-F28")
    @Test fun wordsTheHeaderAndEachCaption() {
        val c = PhotoReportPdfContent.build(res, company, job, client, picked, from, to, zone) { false }
        assertThat(c.company).isEqualTo("Ortiz Electric")
        assertThat(c.title).isEqualTo("Photo report, Oct 26, 2026 to Nov 1, 2026")
        assertThat(c.jobLine).isEqualTo("Dana Hale · 14 Maple Ave, Wayne, PA")
        assertThat(c.facts).containsExactly("2 photos", "1 photo still on another phone is not included.").inOrder()
        assertThat(c.photos[0].caption[0]).isEqualTo("Kitchen · Rough-in")
        assertThat(c.photos[0].caption[1]).startsWith("Oct 27, 2026")
        assertThat(c.photos[0].caption[2]).isEqualTo("Box fill checked at the island")
        // no room or phase still says so; no note adds no line
        assertThat(c.photos[1].caption).hasSize(2)
        assertThat(c.photos[1].caption[0]).isEqualTo("No room or phase")
    }

    @AcceptanceCriterion("AC-F28")
    @Test fun laysOutFourToAPageWithTheAppsMeasure() {
        val many = picked.copy(photos = List(6) { picked.photos[0] })
        val c = PhotoReportPdfContent.build(res, company, job, client, many, from, to, zone) { false }
        val writer = AndroidLogPdfWriter({ n, m -> res.getString(com.kurtz.homerecord.fieldlog.R.string.log_pdf_page, n, m) })
        val pages = PhotoReportLayout(writer.measurer, { n, m -> "Page $n of $m" }).layout(c)
        assertThat(pages.map { p -> p.count { it is LogPdfOp.Image } }).containsExactly(4, 2).inOrder()
    }

    @Test fun aPhotoNotDownloadedHasNoPath() {
        val p = PhotoEntity(id = "p1", homeId = "", jobId = "j1", path = "", takenAt = 1, createdAt = 1, updatedAt = 1)
        assertThat(p.toReportPhoto().path).isNull()
    }

    @Test fun fileNameDropsCharactersAFileCannotHold() {
        assertThat(PhotoReportPdfMaker.fileName("Hale: kitchen/bath", from, to)).isEqualTo("Photo report Hale kitchen bath 2026-10-26 to 2026-11-01.pdf")
    }
}
