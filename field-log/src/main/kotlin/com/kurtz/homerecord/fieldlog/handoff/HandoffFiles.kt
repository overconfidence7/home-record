package com.kurtz.homerecord.fieldlog.handoff

import android.content.Context
import com.kurtz.homerecord.core.bundle.BundleWriter
import com.kurtz.homerecord.core.bundle.ExportedHome
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

/** Reads a job's house as a `.homerecord` and writes it into `cache/handoff/` for the share sheet (AC-F51, AC-C46). */
class HandoffFiles(
    private val context: Context,
    db: HomeRecordDatabase,
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

    fun share(file: File) {
        Intents.shareFile(context, file, MIME, context.getString(R.string.handoff_share_title))
    }

    companion object {
        const val DIR = "handoff"
        /** No registered type for `.homerecord`; House Manual's import (T063d) matches the extension. */
        const val MIME = "application/octet-stream"
    }
}
