package com.kurtz.homerecord.fieldlog.log

import android.content.Context
import com.kurtz.homerecord.core.company.CompanyMembership
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.log.DailyLog
import com.kurtz.homerecord.core.log.LogRecipients
import com.kurtz.homerecord.fieldlog.R
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** A daily log written to a PDF, with who it goes to by email (AC-F33). */
data class LogPdfFile(val file: File, val recipients: List<String>, val subject: String)

/**
 * Builds the PDF for a saved daily log into `cache/logs/` (shared through the app's FileProvider) and works out its
 * email recipients from the job's client and office addresses (AC-F33). Null when the log or its job is gone, or the
 * file could not be written.
 */
class LogPdfMaker(
    private val context: Context,
    private val db: HomeRecordDatabase,
    /** The signed-in company membership; its name and contact line head the PDF when there is no local company row. */
    private val membership: () -> CompanyMembership? = { null },
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun make(log: DailyLog): LogPdfFile? = withContext(Dispatchers.IO) {
        val job = db.jobDao().get(log.jobId) ?: return@withContext null
        val company = LogPdfCompany.of(job.companyId, db.companyDao().get(job.companyId), membership())
        val client = job.clientId?.let { db.clientDao().get(it) }
        // the full photo when it is on this phone, else its thumbnail; neither means it is still on another phone
        val photos = db.photoDao().observeForJob(job.id).first().associate { p ->
            p.id to (p.path.takeIf { it.isNotBlank() && File(it).exists() } ?: p.thumbPath?.takeIf { File(it).exists() }.orEmpty())
        }
        val res = context.resources
        val content = LogPdfContentBuilder.build(res, company, job, client, log, photos)
        val writer = AndroidLogPdfWriter({ n, m -> res.getString(R.string.log_pdf_page, n, m) })
        val dir = File(context.cacheDir, "logs").apply { mkdirs() }
        val file = File(dir, fileName(job.name, log.day.toString()))
        val ok = runCatching { file.outputStream().use { writer.write(content, it) } }.isSuccess
        if (!ok) {
            file.delete()
            return@withContext null
        }
        LogPdfFile(file, recipientsOf(job.officeEmailsJson, client?.email), res.getString(R.string.log_pdf_subject, job.name, content.title))
    }

    /** Who an emailed log for the job goes to: the client, then the office addresses on the job (AC-F33). */
    suspend fun recipients(jobId: String): List<String> = withContext(Dispatchers.IO) {
        val job = db.jobDao().get(jobId) ?: return@withContext emptyList()
        recipientsOf(job.officeEmailsJson, job.clientId?.let { db.clientDao().get(it) }?.email)
    }

    private fun recipientsOf(officeJson: String, clientEmail: String?): List<String> =
        LogRecipients.of(clientEmail, runCatching { json.decodeFromString<List<String>>(officeJson) }.getOrDefault(emptyList()))

    companion object {
        /** "Daily log Hale kitchen 2026-10-30.pdf", with characters a file name cannot hold dropped. */
        fun fileName(job: String, day: String): String {
            val safe = job.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").replace(Regex("\\s+"), " ").trim().take(60)
            return "Daily log ${safe.ifEmpty { "job" }} $day.pdf"
        }
    }
}
