package com.kurtz.homerecord.fieldlog.reports

import android.content.Context
import com.kurtz.homerecord.core.punch.TimeExportFile
import com.kurtz.homerecord.coreui.platform.Intents
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Writes a finished export into cache/exports and opens the share sheet through FileProvider (AC-F15). */
class TimeExportSharer(private val context: Context) {
    suspend fun write(file: TimeExportFile): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        File(dir, file.name).apply { writeText(file.csv) }
    }

    fun share(file: File) = Intents.shareFile(context, file, "text/csv")
}
