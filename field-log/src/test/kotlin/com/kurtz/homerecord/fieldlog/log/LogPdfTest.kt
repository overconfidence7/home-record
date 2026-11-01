package com.kurtz.homerecord.fieldlog.log

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.CompanyEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.log.DailyLog
import com.kurtz.homerecord.core.log.LogPdfOp
import com.kurtz.homerecord.core.model.ChangeOrderLine
import com.kurtz.homerecord.core.model.CrewLine
import com.kurtz.homerecord.core.model.DailyLogSections
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.core.model.LogDelay
import com.kurtz.homerecord.core.model.LogSection
import com.kurtz.homerecord.core.model.LogWeather
import com.kurtz.homerecord.core.model.MaterialLine
import com.kurtz.homerecord.core.model.TaskLine
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.fieldlog.R
import java.io.File
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The daily log PDF's words (AC-F33) and its drawing, checked on a bitmap canvas (PdfDocument has no JVM backing). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LogPdfTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val res = context.resources
    private val company = CompanyEntity(
        id = "c1", name = "Ortiz Electric", phone = "(610) 555-0142", email = "office@ortizelectric.com", logoPath = "/files/media/logo.png",
        createdAt = 1, updatedAt = 1,
    )
    private val job = JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", address = "14 Maple Ave, Wayne, PA", createdAt = 1, updatedAt = 1)
    private val client = ClientEntity(id = "cl1", companyId = "c1", name = "Dana Hale", email = "dana@halehome.com", createdAt = 1, updatedAt = 1)
    private val sections = DailyLogSections(
        crew = LogSection(listOf(CrewLine("m1", "Maria Soto", 510), CrewLine("m2", "Luis Ortiz", 360))),
        workDone = LogSection(listOf("Set the base cabinets", "Ran the 20 A circuit for the dishwasher")),
        photoIds = LogSection(listOf("ph1", "ph2", "ph3")),
        tasksDone = LogSection(listOf(TaskLine("t1", "Level the sink base"))),
        changeOrders = LogSection(listOf(ChangeOrderLine("co1", "Add under-cabinet lights", 48_000))),
        materials = LogSection(listOf(MaterialLine("ma1", "Home Depot", "Electrical", 12_350, "Wire and boxes"))),
        weather = LogSection(LogWeather(highF = 58, lowF = 41, precipIn = 0.12, conditions = "rain")),
        delays = listOf(LogDelay(DelayReason.INSPECTION, "Inspector moved to Monday"), LogDelay(DelayReason.WEATHER)),
        safety = "Toolbox talk: ladders",
        clientNotes = "Countertop template is Monday at 9.",
    )
    private val log = DailyLog("l1", "j1", LocalDate.of(2026, 10, 30), sections, "DRAFT", null, 1)

    private fun build(c: CompanyEntity? = company, s: DailyLogSections = sections) = LogPdfContentBuilder.build(
        res, c, job, client, log.copy(sections = s), mapOf("ph1" to "/m/ph1.jpg", "ph2" to "", "ph3" to "/m/ph3.jpg"), logoExists = { true },
    )

    @AcceptanceCriterion("AC-F33")
    @Test fun headerCarriesCompanyLogoJobClientAndDate() {
        val c = build()
        assertThat(c.company).isEqualTo("Ortiz Electric")
        assertThat(c.companyLine).isEqualTo("(610) 555-0142 · office@ortizelectric.com")
        assertThat(c.logoPath).isEqualTo("/files/media/logo.png")
        assertThat(c.title).isEqualTo("Daily log, Friday, October 30, 2026")
        assertThat(c.job).isEqualTo("Hale kitchen")
        assertThat(c.jobLine).isEqualTo("Dana Hale · 14 Maple Ave, Wayne, PA")
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun sectionsComeInTheLogTabsOrderWithTheirLines() {
        val c = build()
        assertThat(c.sections.map { it.title }).containsExactly(
            "Crew on site", "Work done", "Tasks completed", "Change orders signed", "Materials delivered", "Delays", "Weather", "Safety notes", "Notes for client",
        ).inOrder()
        assertThat(c.sections[0].lines).containsExactly("Maria Soto, 8h 30m", "Luis Ortiz, 6h", "Total: 2 people, 14h 30m").inOrder()
        assertThat(c.sections[3].lines).containsExactly("Add under-cabinet lights, $480.00")
        assertThat(c.sections[4].lines).containsExactly("Home Depot, Electrical, $123.50, Wire and boxes")
        assertThat(c.sections[5].lines).containsExactly("Inspection: Inspector moved to Monday", "Weather").inOrder()
        assertThat(c.sections[6].lines).containsExactly("Rain, high 58°, low 41°, 0.12 in of rain or snow")
        // a photo still on another phone has no file here and is left out
        assertThat(c.photos).containsExactly("/m/ph1.jpg", "/m/ph3.jpg").inOrder()
        assertThat(c.photosTitle).isEqualTo("Photos")
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun emptySectionsAreLeftOutAndAMissingCompanyFallsBackToTheAppName() {
        val c = build(c = null, s = DailyLogSections(visitors = "Building inspector, 10 am"))
        assertThat(c.sections.map { it.title }).containsExactly("Visitors")
        assertThat(c.company).isEqualTo("Field Log")
        assertThat(c.companyLine).isNull()
        assertThat(c.logoPath).isNull()
        assertThat(c.photos).isEmpty()
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun writerDrawsEveryPageWithPhotosAndAMissingLogoWithoutThrowing() {
        val dir = File(context.cacheDir, "pdf-test").apply { mkdirs() }
        val photo = File(dir, "ph1.jpg")
        Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 120, 40)) }
            .compress(Bitmap.CompressFormat.JPEG, 90, photo.outputStream())
        val writer = AndroidLogPdfWriter({ n, m -> res.getString(R.string.log_pdf_page, n, m) })
        val content = build().copy(photos = List(9) { photo.path }, logoPath = File(dir, "gone.png").path)
        val pages = writer.pages(content)
        assertThat(pages).isNotEmpty()
        assertThat(pages.last().filterIsInstance<LogPdfOp.Text>().last().text).isEqualTo("Page ${pages.size} of ${pages.size}")
        pages.forEach { ops ->
            val bitmap = Bitmap.createBitmap(612, 792, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
            writer.draw(Canvas(bitmap), ops, content.logoPath)
            ops.filterIsInstance<LogPdfOp.Image>().filter { it.path == photo.path }.forEach { img ->
                // the photo's colour fills the middle of its cell
                val px = bitmap.getPixel(((img.left + img.right) / 2).toInt(), ((img.top + img.bottom) / 2).toInt())
                assertThat(Color.red(px)).isGreaterThan(150)
                assertThat(Color.blue(px)).isLessThan(100)
            }
        }
    }

    @AcceptanceCriterion("AC-F33")
    @Test fun fileNameKeepsTheJobAndDayAndDropsCharactersAFileCannotHold() {
        assertThat(LogPdfMaker.fileName("Hale kitchen", "2026-10-30")).isEqualTo("Daily log Hale kitchen 2026-10-30.pdf")
        assertThat(LogPdfMaker.fileName("Unit 4/5: \"Smith\"", "2026-10-30")).isEqualTo("Daily log Unit 4 5 Smith 2026-10-30.pdf")
        assertThat(LogPdfMaker.fileName("  ", "2026-10-30")).isEqualTo("Daily log job 2026-10-30.pdf")
    }
}
