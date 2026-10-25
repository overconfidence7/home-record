package com.kurtz.homerecord.fieldlog.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.kurtz.homerecord.core.punch.TimeExportFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes a finished export into cache/exports and opens the share sheet through FileProvider (AC-F15). */
class TimeExportSharer(private val context: Context) {
    suspend fun write(file: TimeExportFile): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        File(dir, file.name).apply { writeText(file.csv) }
    }

    fun share(file: File) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/csv")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, file.name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
