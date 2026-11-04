package com.kurtz.homerecord.fieldlog.reports

import android.content.Context
import android.content.res.Resources
import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.CompanyEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.log.LogPdfContent
import com.kurtz.homerecord.core.log.LogPdfSection
import com.kurtz.homerecord.core.model.ChangeOrderStatus
import com.kurtz.homerecord.core.model.DelayDay
import com.kurtz.homerecord.core.model.JobSummaryTotals
import com.kurtz.homerecord.core.model.JobSummaryReport
import com.kurtz.homerecord.core.model.PunchPoint
import com.kurtz.homerecord.core.model.SummaryChangeOrder
import com.kurtz.homerecord.coreui.platform.Intents
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.fieldlog.changes.dollars
import com.kurtz.homerecord.fieldlog.clock.WeekSheetBuilder
import com.kurtz.homerecord.fieldlog.log.AndroidLogPdfWriter
import com.kurtz.homerecord.fieldlog.log.LogPdfCompany
import com.kurtz.homerecord.fieldlog.log.label
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Writes a job's summary PDF into `cache/reports/` and opens the share sheet (AC-F47): hours against the budget,
 * change orders, tasks, days on site and delays, from the job's first punch to now.
 */
class JobSummaryPdfMaker(
    private val context: Context,
    private val db: HomeRecordDatabase,
    /** The job's saved daily logs' delays (one entry per logged day). */
    private val delays: (jobId: String) -> Flow<List<DelayDay>>,
    private val membership: () -> CompanyMembership? = { null },
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** The PDF, or null when the job is gone or the file could not be written. */
    suspend fun make(jobId: String): File? = withContext(Dispatchers.IO) {
        val job = db.jobDao().get(jobId) ?: return@withContext null
        val punches = db.punchDao().listFor(job.companyId).map { PunchPoint(it.memberId, it.jobId, it.kind, it.at) }
        val orders = db.changeOrderDao().observeFor(jobId).first().map { SummaryChangeOrder(ChangeOrderStatus.of(it.status), it.totalCents) }
        val tasks = db.taskDao().observeFor(jobId).first().map { it.done }
        val at = now()
        val summary = JobSummaryReport.build(jobId, job.budgetHours, punches, orders, tasks, delays(jobId).first(), zone(), at)
        val company = LogPdfCompany.of(job.companyId, db.companyDao().get(job.companyId), membership())
        val client = job.clientId?.let { db.clientDao().get(it) }
        val res = context.resources
        val content = JobSummaryPdfContent.build(res, company, job, client, summary, Instant.ofEpochMilli(at).atZone(zone()).toLocalDate())
        val writer = AndroidLogPdfWriter({ n, m -> res.getString(R.string.log_pdf_page, n, m) })
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, fileName(job.name))
        val ok = runCatching { file.outputStream().use { writer.write(content, it) } }.isSuccess
        if (!ok) {
            file.delete()
            return@withContext null
        }
        file
    }

    fun share(pdf: File): Boolean {
        if (!pdf.exists()) return false
        Intents.shareFile(context, pdf, "application/pdf", context.getString(R.string.rep_summary_share_title))
        return true
    }

    companion object {
        /** "Job summary Hale kitchen.pdf", with characters a file name cannot hold dropped. */
        fun fileName(job: String): String {
            val clean = job.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").replace(Regex("\\s+"), " ").trim().take(60)
            return "Job summary ${clean.ifEmpty { "job" }}.pdf"
        }
    }
}

/** Words a [JobSummaryTotals] for the PDF (AC-F47); the daily log's layout draws it. */
object JobSummaryPdfContent {
    fun build(
        res: Resources,
        company: CompanyEntity?,
        job: JobEntity,
        client: ClientEntity?,
        s: JobSummaryTotals,
        today: LocalDate,
        fileExists: (String) -> Boolean = { File(it).exists() },
    ): LogPdfContent {
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US)
        val long = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(Locale.US)
        fun hm(ms: Long) = WeekSheetBuilder.hm(ms)
        val budget = s.budgetMillis
        val hours = buildList {
            add(res.getString(R.string.rep_summary_worked, hm(s.workedMillis)))
            if (budget == null) {
                add(res.getString(R.string.rep_summary_no_budget))
            } else {
                add(res.getString(R.string.rep_summary_budget, hm(budget)))
                add(
                    if (s.overBudgetMillis > 0) res.getString(R.string.rep_summary_over, hm(s.overBudgetMillis))
                    else res.getString(R.string.rep_summary_left, hm(s.remainingMillis ?: 0)),
                )
            }
        }
        val orders = buildList {
            if (s.signedCount + s.draftCount + s.declinedCount == 0) add(res.getString(R.string.rep_summary_no_changes))
            if (s.signedCount > 0) add(res.getQuantityString(R.plurals.rep_summary_signed, s.signedCount, s.signedCount, dollars(s.signedCents)))
            if (s.draftCount > 0) add(res.getQuantityString(R.plurals.rep_summary_drafts, s.draftCount, s.draftCount, dollars(s.draftCents)))
            if (s.declinedCount > 0) add(res.getQuantityString(R.plurals.rep_summary_declined, s.declinedCount, s.declinedCount))
        }
        val tasks = if (s.tasksDone + s.tasksOpen == 0) {
            listOf(res.getString(R.string.rep_summary_no_tasks))
        } else {
            listOf(res.getString(R.string.rep_summary_tasks_done, s.tasksDone), res.getString(R.string.rep_summary_tasks_open, s.tasksOpen))
        }
        val first = s.firstDay
        val last = s.lastDay
        val days = if (first == null || last == null) {
            listOf(res.getString(R.string.rep_summary_no_days))
        } else {
            listOf(
                res.getQuantityString(R.plurals.rep_summary_days, s.daysOnSite, s.daysOnSite),
                if (first == last) res.getString(R.string.rep_summary_one_day, first.format(date))
                else res.getString(R.string.rep_summary_span, first.format(date), last.format(date)),
            )
        }
        val delays = if (s.delayDays == 0) {
            listOf(res.getString(R.string.rep_summary_no_delays))
        } else {
            listOf(res.getQuantityString(R.plurals.rep_summary_delay_days, s.delayDays, s.delayDays)) +
                s.delaysByReason.map { (reason, n) -> res.getString(R.string.rep_summary_reason, res.getString(reason.label()), n) }
        }
        return LogPdfContent(
            company = company?.name?.takeIf { it.isNotBlank() } ?: res.getString(R.string.app_name),
            companyLine = listOfNotNull(company?.phone, company?.email).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null },
            logoPath = company?.logoPath?.takeIf { it.isNotBlank() && fileExists(it) },
            title = res.getString(R.string.rep_summary_title, today.format(long)),
            job = job.name,
            jobLine = listOfNotNull(client?.name, job.address).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null },
            sections = listOf(
                LogPdfSection(res.getString(R.string.rep_summary_hours), hours),
                LogPdfSection(res.getString(R.string.rep_summary_changes), orders),
                LogPdfSection(res.getString(R.string.rep_summary_tasks), tasks),
                LogPdfSection(res.getString(R.string.rep_summary_days_title), days),
                LogPdfSection(res.getString(R.string.rep_delays), delays),
            ),
        )
    }
}
