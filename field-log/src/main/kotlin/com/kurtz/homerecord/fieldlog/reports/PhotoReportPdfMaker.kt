package com.kurtz.homerecord.fieldlog.reports

import android.content.Context
import android.content.res.Resources
import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.CompanyEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.log.PhotoReportCell
import com.kurtz.homerecord.core.log.PhotoReportContent
import com.kurtz.homerecord.core.log.PhotoReportLayout
import com.kurtz.homerecord.core.log.PhotoReportPicker
import com.kurtz.homerecord.core.log.ReportPhoto
import com.kurtz.homerecord.core.log.ReportPhotoPick
import com.kurtz.homerecord.coreui.platform.Intents
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.fieldlog.log.AndroidLogPdfWriter
import com.kurtz.homerecord.fieldlog.log.LogPdfCompany
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What came of asking for a photo report. */
enum class PhotoReportOutcome { Shared, NoneHere, Failed }

fun PhotoEntity.toReportPhoto() = ReportPhoto(id, jobId, takenAt, path.takeIf { it.isNotBlank() }, roomId, stage, caption, deletedAt != null)

/**
 * Writes a job's photo report PDF for a date range into `cache/reports/` and opens the share sheet (AC-F28): the
 * photos taken on the job in those dates, oldest first, four to a page with room, phase, date and note under each.
 */
class PhotoReportPdfMaker(
    private val context: Context,
    private val db: HomeRecordDatabase,
    private val membership: () -> CompanyMembership? = { null },
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    suspend fun share(jobId: String, from: LocalDate, to: LocalDate): PhotoReportOutcome {
        val pdf = when (val made = make(jobId, from, to)) {
            null -> return PhotoReportOutcome.Failed
            Made.NoneHere -> return PhotoReportOutcome.NoneHere
            is Made.Pdf -> made.file
        }
        if (!pdf.exists()) return PhotoReportOutcome.Failed
        Intents.shareFile(context, pdf, "application/pdf", context.getString(R.string.rep_photo_share_title))
        return PhotoReportOutcome.Shared
    }

    sealed interface Made {
        data object NoneHere : Made
        data class Pdf(val file: File) : Made
    }

    /** The PDF, [Made.NoneHere] when no photo from those dates is on this phone, or null when it could not be written. */
    suspend fun make(jobId: String, from: LocalDate, to: LocalDate): Made? = withContext(Dispatchers.IO) {
        val job = db.jobDao().get(jobId) ?: return@withContext null
        val photos = db.photoDao().listForJob(jobId)
        val rooms = photos.mapNotNull { it.roomId }.distinct().mapNotNull { id -> db.roomDao().get(id)?.let { id to it.name } }.toMap()
        val picked = PhotoReportPicker.pick(photos.map { it.toReportPhoto() }, jobId, from, to, zone(), rooms) { File(it).exists() }
        if (picked.photos.isEmpty()) return@withContext Made.NoneHere
        val company = LogPdfCompany.of(job.companyId, db.companyDao().get(job.companyId), membership())
        val client = job.clientId?.let { db.clientDao().get(it) }
        val res = context.resources
        val content = PhotoReportPdfContent.build(res, company, job, client, picked, from, to, zone())
        val writer = AndroidLogPdfWriter({ n, m -> res.getString(R.string.log_pdf_page, n, m) })
        val pages = PhotoReportLayout(writer.measurer, { n, m -> res.getString(R.string.log_pdf_page, n, m) }).layout(content)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, fileName(job.name, from, to))
        val ok = runCatching { file.outputStream().use { writer.write(pages, it, content.logoPath) } }.isSuccess
        if (!ok) {
            file.delete()
            return@withContext null
        }
        Made.Pdf(file)
    }

    companion object {
        /** "Photo report Hale kitchen 2026-10-26 to 2026-11-01.pdf", with characters a file name cannot hold dropped. */
        fun fileName(job: String, from: LocalDate, to: LocalDate): String {
            val clean = job.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").replace(Regex("\\s+"), " ").trim().take(60)
            return "Photo report ${clean.ifEmpty { "job" }} $from to $to.pdf"
        }
    }
}

/** Words a photo report for the PDF (AC-F28). */
object PhotoReportPdfContent {
    fun build(
        res: Resources,
        company: CompanyEntity?,
        job: JobEntity,
        client: ClientEntity?,
        picked: PhotoReportPicker.Result,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId,
        fileExists: (String) -> Boolean = { File(it).exists() },
    ): PhotoReportContent {
        val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US)
        val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(Locale.US)
        val n = picked.photos.size
        val facts = buildList {
            add(res.getQuantityString(R.plurals.rep_photo_pdf_count, n, n))
            if (picked.leftOut > 0) add(res.getQuantityString(R.plurals.rep_photo_pdf_left_out, picked.leftOut, picked.leftOut))
        }
        return PhotoReportContent(
            company = company?.name?.takeIf { it.isNotBlank() } ?: res.getString(R.string.app_name),
            companyLine = listOfNotNull(company?.phone, company?.email).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null },
            logoPath = company?.logoPath?.takeIf { it.isNotBlank() && fileExists(it) },
            title = res.getString(R.string.rep_photo_title, from.format(date), to.format(date)),
            job = job.name,
            jobLine = listOfNotNull(client?.name, job.address).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null },
            facts = facts,
            photos = picked.photos.map { PhotoReportCell(it.path, caption(res, it, dateTime, zone)) },
        )
    }

    /** Room · phase (or "No room" when neither is set), then the date and time, then the note. */
    fun caption(res: Resources, p: ReportPhotoPick, dateTime: DateTimeFormatter, zone: ZoneId): List<String> = listOfNotNull(
        listOfNotNull(p.room, p.phase).joinToString(" · ").ifEmpty { res.getString(R.string.rep_photo_no_room) },
        Instant.ofEpochMilli(p.takenAt).atZone(zone).format(dateTime),
        p.note,
    )
}
