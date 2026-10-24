package com.kurtz.homerecord.housemodel.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.media.DocumentFiles
import com.kurtz.homerecord.core.media.StoredDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Copies picked PDFs and images into `filesDir/documents/<uuid>.<ext>` (AC-C37). */
class AndroidDocumentFiles(private val context: Context) : DocumentFiles {
    private val dir get() = File(context.filesDir, "documents").apply { mkdirs() }

    override suspend fun import(uri: String): StoredDocument? = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        val mime = context.contentResolver.getType(parsed) ?: return@withContext null
        val ext = when {
            mime == "application/pdf" -> "pdf"
            mime == "image/png" -> "png"
            mime.startsWith("image/") -> "jpg"
            else -> return@withContext null
        }
        val file = File(dir, "${Ids.newId()}.$ext")
        try {
            val input = context.contentResolver.openInputStream(parsed) ?: return@withContext null
            input.use { src -> file.outputStream().use { src.copyTo(it) } }
        } catch (e: IOException) {
            file.delete()
            return@withContext null
        } catch (e: SecurityException) {
            file.delete()
            return@withContext null
        }
        StoredDocument(file.absolutePath, displayName(parsed) ?: "Document.$ext", mime, file.length())
    }

    override fun discard(path: String) {
        File(path).delete()
    }

    private fun displayName(uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
}
