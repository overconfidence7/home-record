package com.kurtz.homerecord.fieldlog.report

import android.content.Context
import com.kurtz.homerecord.core.logic.ReportHeader
import com.kurtz.homerecord.core.logic.ReportLabels
import com.kurtz.homerecord.core.logic.ReportSectionBuilder
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.pdf.ReportPdfWriter
import com.kurtz.homerecord.core.repo.JobRepository
import com.kurtz.homerecord.core.repo.ReportRepository
import com.kurtz.homerecord.fieldlog.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes `cache/shared/report-<job>-<day>.pdf`, replacing any earlier file for that day (AC-70). */
class AndroidReportPdfExporter(
    private val context: Context,
    private val jobs: JobRepository,
    private val reports: ReportRepository,
    private val media: MediaFileStore,
) : ReportPdfExporter {
    override suspend fun export(jobId: String, day: String): String? {
        val job = jobs.getJob(jobId) ?: return null
        val report = reports.draftFor(jobId, day)
        val company = reports.getProfile()
        val photos = reports.photosFor(jobId, day).mapNotNull { e -> e.mediaPath?.let(media::resolve) }
        val labels = ReportLabels(
            weather = context.getString(R.string.report_weather),
            crew = context.getString(R.string.report_crew),
            hours = context.getString(R.string.report_hours),
            work = context.getString(R.string.report_work),
            delays = context.getString(R.string.report_delays),
            materials = context.getString(R.string.report_materials),
            nextSteps = context.getString(R.string.report_next_steps),
        )
        val reason = report.delayReason?.takeIf { it != com.kurtz.homerecord.core.model.DelayReason.NONE }
            ?.let { context.getString(it.label()) }
        val header = ReportHeader(company?.name.orEmpty(), job.name, job.address.orEmpty(), day)
        val sections = ReportSectionBuilder.sections(report, labels, reason)
        val relative = "shared/" + ReportSectionBuilder.fileName(jobId, day)
        val file = File(context.cacheDir, relative)
        withContext(Dispatchers.IO) {
            file.delete()
            ReportPdfWriter.write(file, header, sections, photos)
        }
        reports.save(report.copy(pdfPath = relative))
        return relative
    }
}
