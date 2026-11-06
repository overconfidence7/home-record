package com.kurtz.homerecord.housemodel.pdf

import android.content.Context
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * Writes the home report for [homeId] into cache/exports and returns a FileProvider URI for the share sheet, or null
 * when the home is gone or the file could not be written (AC-H13).
 */
class HomeReportFile(
    private val context: Context,
    private val source: HomeReportSource,
    private val legendWords: LegendWords,
    private val today: () -> LocalDate = LocalDate::now,
) {
    suspend fun export(homeId: String): String? = withContext(Dispatchers.IO) {
        val data = source.load(homeId) ?: return@withContext null
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safe = data.homeName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "home" }
            val file = File(dir, "$safe-home-report.pdf")
            file.outputStream().use { HomeReportPdfWriter(reportWords(context), legendWords).write(data, today().toString(), it) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
        } catch (e: java.io.IOException) {
            null
        }
    }
}
