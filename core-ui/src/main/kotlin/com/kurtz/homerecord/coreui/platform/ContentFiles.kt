package com.kurtz.homerecord.coreui.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException

/** Reading a picked document: its display name, and a copy into app storage so it outlives the picker's grant. Call off the main thread. */
object ContentFiles {
    fun displayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()

    /** Copies [uri] into [dest]; false (and no partial file) when it can't be read. */
    fun copy(context: Context, uri: Uri, dest: File): Boolean {
        dest.parentFile?.mkdirs()
        return try {
            val input = context.contentResolver.openInputStream(uri) ?: return false
            input.use { src -> dest.outputStream().use { src.copyTo(it) } }
            true
        } catch (_: IOException) {
            dest.delete()
            false
        } catch (_: SecurityException) {
            dest.delete()
            false
        }
    }
}
