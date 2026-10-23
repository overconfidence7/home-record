package com.kurtz.homerecord.housemodel.ui

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.kurtz.homerecord.core.Ids
import com.kurtz.homerecord.core.media.CaptureTarget
import com.kurtz.homerecord.core.media.MediaFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class AndroidMediaFiles(private val context: Context) : MediaFiles {
    private val dir get() = File(context.filesDir, "media").apply { mkdirs() }

    override fun newCaptureTarget(): CaptureTarget {
        val file = File(dir, "${Ids.newId()}.jpg")
        return CaptureTarget(FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString(), file.absolutePath)
    }

    override suspend fun import(uri: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(dir, "${Ids.newId()}.jpg")
            val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: return@withContext null
            input.use { src -> file.outputStream().use { src.copyTo(it) } }
            file.absolutePath
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    override fun exists(path: String) = File(path).let { it.isFile && it.length() > 0 }

    override fun discard(path: String) {
        File(path).delete()
    }
}
