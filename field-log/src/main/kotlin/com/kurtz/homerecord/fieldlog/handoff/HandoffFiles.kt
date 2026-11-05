package com.kurtz.homerecord.fieldlog.handoff

import android.content.Context
import com.kurtz.homerecord.core.bundle.BundleWriter
import com.kurtz.homerecord.core.bundle.ExportedHome
import com.kurtz.homerecord.core.bundle.HandoffApi
import com.kurtz.homerecord.core.bundle.HandoffSendFailure
import com.kurtz.homerecord.core.bundle.HandoffSendResult
import com.kurtz.homerecord.core.bundle.HandoffSummary
import com.kurtz.homerecord.core.bundle.HomeBundleExporter
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.sync.RoomRowStore
import com.kurtz.homerecord.coreui.platform.Intents
import com.kurtz.homerecord.fieldlog.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/**
 * Reads a job's house as a `.homerecord`, writes it into `cache/handoff/` for the share sheet (AC-F51, AC-C46), and
 * sends it to the homeowner through `POST /handoff` (AC-B40).
 */
class HandoffFiles(
    private val context: Context,
    private val db: HomeRecordDatabase,
    private val api: HandoffApi,
    private val companyId: () -> String?,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val rows = RoomRowStore(db)
    private val exporter = HomeBundleExporter({ table, column, values -> rows.readWhere(table, column, values) })

    suspend fun load(homeId: String): ExportedHome? = withContext(Dispatchers.IO) { exporter.export(homeId, Sources.FIELD_LOG) }

    /** The written file, or null when it could not be written (a partial file is removed). */
    suspend fun write(home: ExportedHome, jobName: String): File? = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        val file = File(dir, HandoffSummary.fileName(jobName, LocalDate.now(zone()).toString()))
        val ok = runCatching { file.outputStream().buffered().use { BundleWriter.write(home.bundle, it, home::mediaFile) } }.isSuccess
        if (ok) file else null.also { file.delete() }
    }

    /** The live job on this house (the active one first) and its client's email, for the send. */
    suspend fun recipient(homeId: String): Recipient? = withContext(Dispatchers.IO) {
        val company = companyId() ?: return@withContext null
        val job = db.jobDao().listFor(company).filter { it.homeId == homeId }.sortedBy { if (it.status == "ACTIVE") 0 else 1 }.firstOrNull()
            ?: return@withContext null
        Recipient(job.id, job.address?.trim()?.ifEmpty { null }, job.clientId?.let { db.clientDao().get(it) }?.email?.trim()?.ifEmpty { null })
    }

    /** Uploads [file]; the home is named by the job's address, or the job's name without one. */
    suspend fun send(homeId: String, jobName: String, email: String, file: File): HandoffSendResult {
        val company = companyId() ?: return HandoffSendResult.Failed(HandoffSendFailure.Failed)
        val to = recipient(homeId)
        return api.send(company, to?.jobId, email, to?.address ?: jobName, file)
    }

    data class Recipient(val jobId: String, val address: String?, val email: String?)

    fun share(file: File) {
        Intents.shareFile(context, file, MIME, context.getString(R.string.handoff_share_title))
    }

    companion object {
        const val DIR = "handoff"
        /** No registered type for `.homerecord`; House Manual's import (T063d) matches the extension. */
        const val MIME = "application/octet-stream"
    }
}
