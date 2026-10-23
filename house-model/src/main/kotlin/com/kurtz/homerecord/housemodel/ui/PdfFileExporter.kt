package com.kurtz.homerecord.housemodel.ui

import android.content.Context
import androidx.core.content.FileProvider
import com.kurtz.homerecord.housemodel.pdf.BlueprintPdfWriter
import com.kurtz.homerecord.housemodel.pdf.PdfFloor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes the PDF into cache/exports and hands back a FileProvider URI for the share sheet. */
class PdfFileExporter(private val context: Context) : BlueprintExporter {
    override suspend fun export(homeName: String, floors: List<PdfFloor>): String? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val safe = homeName.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "home" }
            val file = File(dir, "$safe-blueprint.pdf")
            file.outputStream().use { BlueprintPdfWriter().write(homeName, floors, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
        } catch (e: java.io.IOException) {
            null
        }
    }
}
