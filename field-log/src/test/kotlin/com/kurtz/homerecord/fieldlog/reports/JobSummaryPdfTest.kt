package com.kurtz.homerecord.fieldlog.reports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.CompanyEntity
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.log.LogPdfLayout
import com.kurtz.homerecord.core.log.LogPdfOp
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.core.model.JobSummaryTotals
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.fieldlog.log.AndroidLogPdfWriter
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The job summary PDF's words and layout (AC-F47); PdfDocument has no JVM backing, so the drawing is checked on device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JobSummaryPdfTest {
    private val res = ApplicationProvider.getApplicationContext<Context>().resources
    private val h = 3_600_000L
    private val company = CompanyEntity(id = "c1", name = "Ortiz Electric", phone = "(610) 555-0142", createdAt = 1, updatedAt = 1)
    private val job = JobEntity(id = "j1", companyId = "c1", name = "Hale kitchen", address = "14 Maple Ave, Wayne, PA", budgetHours = 40, createdAt = 1, updatedAt = 1)
    private val client = ClientEntity(id = "k1", companyId = "c1", name = "Dana Hale", createdAt = 1, updatedAt = 1)
    private val today = LocalDate.of(2026, 11, 4)
    private val busy = JobSummaryTotals(
        workedMillis = 43 * h + h / 2, budgetMillis = 40 * h,
        signedCount = 2, signedCents = 60_550, draftCount = 1, draftCents = 9_900, declinedCount = 0,
        tasksDone = 7, tasksOpen = 2, daysOnSite = 6, firstDay = LocalDate.of(2026, 10, 19), lastDay = LocalDate.of(2026, 11, 3),
        delayDays = 2, delaysByReason = listOf(DelayReason.WEATHER to 2, DelayReason.INSPECTION to 1),
    )

    @AcceptanceCriterion("AC-F47")
    @Test fun summaryCarriesHoursVsBudgetChangeOrdersTasksDaysAndDelays() {
        val c = JobSummaryPdfContent.build(res, company, job, client, busy, today, fileExists = { true })
        assertThat(c.company).isEqualTo("Ortiz Electric")
        assertThat(c.title).isEqualTo("Job summary, November 4, 2026")
        assertThat(c.job).isEqualTo("Hale kitchen")
        assertThat(c.jobLine).isEqualTo("Dana Hale · 14 Maple Ave, Wayne, PA")
        assertThat(c.sections.map { it.title }).containsExactly("Hours", "Change orders", "Tasks", "Days on site", "Delays").inOrder()
        assertThat(c.sections[0].lines).containsExactly("Worked: 43h 30m", "Budget: 40h", "Over budget by 3h 30m").inOrder()
        assertThat(c.sections[1].lines).containsExactly("2 signed, $605.50 total", "1 draft not signed yet, $99.00").inOrder()
        assertThat(c.sections[2].lines).containsExactly("Done: 7", "Open: 2").inOrder()
        assertThat(c.sections[3].lines).containsExactly("6 days on site", "From Oct 19, 2026 to Nov 3, 2026").inOrder()
        assertThat(c.sections[4].lines).containsExactly("2 days with a delay", "Weather: 2", "Inspection: 1").inOrder()
    }

    @AcceptanceCriterion("AC-F47")
    @Test fun aNewJobSaysWhatIsMissingInsteadOfZeros() {
        val fresh = JobSummaryTotals(0, null, 0, 0, 0, 0, 0, 0, 0, 0, null, null, 0, emptyList())
        val c = JobSummaryPdfContent.build(res, null, job.copy(budgetHours = null, address = null), null, fresh, today)
        assertThat(c.jobLine).isNull()
        assertThat(c.sections.map { it.lines }).containsExactly(
            listOf("Worked: 0h", "No budget set for this job."),
            listOf("No change orders."),
            listOf("No tasks on this job."),
            listOf("No one has clocked in on this job yet."),
            listOf("No delays logged."),
        ).inOrder()
        val under = JobSummaryPdfContent.build(res, company, job, client, busy.copy(workedMillis = 12 * h), today)
        assertThat(under.sections[0].lines.last()).isEqualTo("Left in the budget: 28h")
    }

    @Test fun laysOutOnOnePageWithTheFooterAndAFileNameForTheJob() {
        val writer = AndroidLogPdfWriter({ n, m -> "Page $n of $m" })
        val c = JobSummaryPdfContent.build(res, company, job, client, busy, today, fileExists = { false })
        val pages = LogPdfLayout(writer.measurer, { n, m -> "Page $n of $m" }).layout(c)
        assertThat(pages).hasSize(1)
        val words = pages.single().filterIsInstance<LogPdfOp.Text>().map { it.text }
        assertThat(words).containsAtLeast("Job summary, November 4, 2026", "Over budget by 3h 30m", "Weather: 2", "Page 1 of 1")
        assertThat(JobSummaryPdfMaker.fileName("Hale kitchen / phase 2")).isEqualTo("Job summary Hale kitchen phase 2.pdf")
    }
}
