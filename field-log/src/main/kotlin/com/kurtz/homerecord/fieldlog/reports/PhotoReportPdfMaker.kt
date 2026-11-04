package com.kurtz.homerecord.fieldlog.reports

import android.content.Context
import android.content.res.Resources
import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.CompanyEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.log.PhotoReportContent
import com.kurtz.homerecord.core.log.PhotoReportEntry
import com.kurtz.homerecord.core.log.PhotoReportLayout
import com.kurtz.homerecord.core.log.PhotoReportPicker
import com.kurtz.homerecord.core.log.ReportPhoto
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Writes a job's photo report PDF for a date range into `cache/reports/` and opens the share sheet (AC-F28): the
 * photos taken in the range oldest first, four to a page, each captioned with when it was taken, its room and phase,
 * and its note. A photo still on another phone (no full file and no thumbnail here) is left out and counted on page one.
 */
class PhotoReportPdfMaker(
    private val context: Context,
    private val db: HomeRecordDatabase,
    private val membership: () -> CompanyMembership? = { null },
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    /** The PDF, or null when the job is gone or the file could not be written. */
    suspend fun make(jobId: String, from: LocalDate, to: LocalDate): File? = withContext(Dispatchers.IO) {
        val job = db.jobDao().get(jobId) ?: return@withContext null
        val rows = db.photoDao().observeForJob(jobId).first().associateBy { it.id }
        val picked = PhotoReportPicker.inRange(rows.values.map { it.toReportPhoto() }, jobId, from, to, zone())
        val rooms = picked.mapNotNull { rows[it.id]?.roomId }.distinct().associateWith { db.roomDao().get(it)?.name }
        val photos = picked.mapNotNull { p -> rows[p.id]?.let { it to p.path } }
        val company = LogPdfCompany.of(job.companyId, db.companyDao().get(job.companyId), membership())
        val client = job.clientId?.let { db.clientDao().get(it) }
        val res = context.resources
        val content = PhotoReportPdfContent.build(res, company, job, client, from, to, photos, { rooms[it] }, zone())
        val writer = AndroidLogPdfWriter({ n, m -> res.getString(R.string.log_pdf_page, n, m) })
        val pages = PhotoReportLayout(writer.measurer, { n, m -> res.getString(R.string.log_pdf_page, n, m) }).layout(content)
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val file = File(dir, fileName(job.name, from, to))
        val ok = runCatching { file.outputStream().use { writer.write(pages, it, content.logoPath) } }.isSuccess
        if (!ok) {
            file.delete()
            return@withContext null
        }
        file
    }

    fun share(pdf: File): Boolean {
        if (!pdf.exists()) return false
        Intents.shareFile(context, pdf, "application/pdf", context.getString(R.string.rep_photos_share_title))
        return true
    }

    companion object {
        /** "Photos Hale kitchen 2026-10-19 to 2026-10-25.pdf", with characters a file name cannot hold dropped. */
        fun fileName(job: String, from: LocalDate, to: LocalDate): String {
            val clean = job.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").replace(Regex("\\s+"), " ").trim().take(60)
            return "Photos ${clean.ifEmpty { "job" }} $from to $to.pdf"
        }

        /** The full photo when it is on this phone, else its thumbnail; blank means it is still on another phone. */
        fun PhotoEntity.toReportPhoto(exists: (String) -> Boolean = { File(it).exists() }): ReportPhoto {
            val local = path.takeIf { it.isNotBlank() && exists(it) } ?: thumbPath?.takeIf { it.isNotBlank() && exists(it) }.orEmpty()
            return ReportPhoto(id, jobId.orEmpty(), takenAt, local)
        }
    }
}

/** Words a job's photos for the photo report (AC-F28); [PhotoReportLayout] draws it. */
object PhotoReportPdfContent {
    fun build(
        res: Resources,
        company: CompanyEntity?,
        job: JobEntity,
        client: ClientEntity?,
        from: LocalDate,
        to: LocalDate,
        /** The picked photos, oldest first, each with its local file ("" when it is on another phone). */
        photos: List<Pair<PhotoEntity, String>>,
        roomName: (String) -> String?,
        zone: ZoneId,
        fileExists: (String) -> Boolean = { File(it).exists() },
    ): PhotoReportContent {
        val day = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US)
        val stamp = DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a", Locale.US)
        val here = photos.filter { it.second.isNotBlank() }
        val missing = photos.size - here.size
        return PhotoReportContent(
            company = company?.name?.takeIf { it.isNotBlank() } ?: res.getString(R.string.app_name),
            companyLine = listOfNotNull(company?.phone, company?.email).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null },
            logoPath = company?.logoPath?.takeIf { it.isNotBlank() && fileExists(it) },
            title = res.getString(R.string.rep_photos_title, from.format(day), to.format(day)),
            job = job.name,
            jobLine = listOfNotNull(client?.name, job.address).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { null },
            note = if (missing > 0) res.getQuantityString(R.plurals.rep_photos_missing, missing, missing) else null,
            photos = here.map { (p, path) ->
                val room = p.roomId?.let(roomName)?.trim()?.takeIf { it.isNotEmpty() }
                val phase = p.stage?.trim()?.takeIf { it.isNotEmpty() }
                val place = when {
                    room != null && phase != null -> res.getString(R.string.rep_photos_place, room, phase)
                    else -> room ?: phase
                }
                PhotoReportEntry(
                    path,
                    listOfNotNull(Instant.ofEpochMilli(p.takenAt).atZone(zone).format(stamp), place, p.caption?.trim()?.takeIf { it.isNotEmpty() }),
                )
            },
        )
    }
}
