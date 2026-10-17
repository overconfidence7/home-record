package com.kurtz.homerecord.fieldlog.handoff

import android.content.Context
import com.kurtz.homerecord.core.bundle.BundleWriter
import com.kurtz.homerecord.core.logic.HandoffCounts
import com.kurtz.homerecord.core.logic.HandoffLabels
import com.kurtz.homerecord.core.logic.HandoffSummaryBuilder
import com.kurtz.homerecord.core.logic.ReportHeader
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.pdf.ReportPdfWriter
import com.kurtz.homerecord.core.repo.HandoffRepository
import com.kurtz.homerecord.core.repo.JobRepository
import com.kurtz.homerecord.core.repo.ReportRepository
import com.kurtz.homerecord.fieldlog.R
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes `cache/shared/handoff-<job>.homerecord|pdf`, replacing any earlier export (AC-72). */
class AndroidHandoffExporter(
    private val context: Context,
    private val jobs: JobRepository,
    private val handoff: HandoffRepository,
    private val reports: ReportRepository,
    private val media: MediaFileStore,
) : HandoffExporter {
    override suspend fun exportBundle(jobId: String): String? {
        val job = jobs.getJob(jobId) ?: return null
        val bundle = handoff.bundleFor(job.homeId) ?: return null
        val relative = "shared/" + HandoffSummaryBuilder.bundleFileName(jobId)
        val file = File(context.cacheDir, relative)
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.delete()
            file.outputStream().use { BundleWriter(media).write(bundle, it) }
        }
        return relative
    }

    override suspend fun exportPdf(jobId: String): String? {
        val job = jobs.getJob(jobId) ?: return null
        val bundle = handoff.bundleFor(job.homeId) ?: return null
        val labels = HandoffLabels(
            rooms = context.getString(R.string.handoff_rooms),
            walls = context.getString(R.string.handoff_walls),
            behindWallPhotos = context.getString(R.string.handoff_behind_wall),
            equipment = context.getString(R.string.handoff_equipment),
            finishes = context.getString(R.string.handoff_finishes),
            shutoffs = context.getString(R.string.handoff_shutoffs),
            panels = context.getString(R.string.handoff_panels),
        )
        val header = ReportHeader(reports.getProfile()?.name.orEmpty(), job.name, job.address.orEmpty(), LocalDate.now().toString())
        val sections = HandoffSummaryBuilder.sections(HandoffCounts.of(bundle), labels)
        val relative = "shared/" + HandoffSummaryBuilder.pdfFileName(jobId)
        val file = File(context.cacheDir, relative)
        withContext(Dispatchers.IO) {
            file.delete()
            ReportPdfWriter.write(file, header, sections, emptyList())
        }
        return relative
    }
}
