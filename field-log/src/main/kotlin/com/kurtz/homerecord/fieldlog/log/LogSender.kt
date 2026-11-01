package com.kurtz.homerecord.fieldlog.log

import android.content.Context
import com.kurtz.homerecord.core.log.DailyLog
import com.kurtz.homerecord.coreui.platform.Intents
import com.kurtz.homerecord.fieldlog.R

/** Makes a daily log's PDF and hands it to an email app or the share sheet (AC-F33). Tests use a fake. */
interface LogSender {
    /** Who an emailed log for the job goes to: client first, then the office addresses. */
    suspend fun recipients(jobId: String): List<String>

    /** The PDF for a saved log, or null when the log's job is gone or the file could not be written. */
    suspend fun make(log: DailyLog): LogPdfFile?

    /** Opens an email app addressed to the PDF's recipients ([email]) or the share sheet; false when nothing took it. */
    fun open(pdf: LogPdfFile, email: Boolean): Boolean
}

class AndroidLogSender(private val context: Context, private val maker: LogPdfMaker) : LogSender {
    override suspend fun recipients(jobId: String): List<String> = maker.recipients(jobId)

    override suspend fun make(log: DailyLog): LogPdfFile? = maker.make(log)

    override fun open(pdf: LogPdfFile, email: Boolean): Boolean {
        val title = context.getString(R.string.log_send_title)
        if (email && pdf.recipients.isNotEmpty()) return Intents.emailFile(context, pdf.file, PDF, pdf.recipients, pdf.subject, title)
        if (!pdf.file.exists()) return false
        Intents.shareFile(context, pdf.file, PDF, pdf.file.name)
        return true
    }

    private companion object {
        const val PDF = "application/pdf"
    }
}
