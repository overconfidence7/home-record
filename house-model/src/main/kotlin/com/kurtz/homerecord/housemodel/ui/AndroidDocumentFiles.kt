package com.kurtz.homerecord.housemodel.ui

import android.content.Context
import android.net.Uri
import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.media.DocumentFiles
import com.kurtz.homerecord.core.media.StoredDocument
import com.kurtz.homerecord.coreui.platform.ContentFiles
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        if (!ContentFiles.copy(context, parsed, file)) return@withContext null
        StoredDocument(file.absolutePath, ContentFiles.displayName(context, parsed) ?: "Document.$ext", mime, file.length())
    }

    override fun discard(path: String) {
        File(path).delete()
    }
}
