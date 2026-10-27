package com.kurtz.homerecord.coreui.pages

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** One document's pages, rendered on demand so a 40-page manual never sits in memory at once. */
interface PageSource {
    val pageCount: Int
    suspend fun render(index: Int, widthPx: Int): ImageBitmap?
    /** A page already in memory, shown at once while [render] runs; previews use it. */
    fun peek(index: Int): ImageBitmap? = null
    fun close() = Unit
}

/** Opens a stored PDF or image as pages; null when the file is missing or unreadable. */
object PageSources {
    fun open(path: String, mime: String): PageSource? {
        val file = File(path)
        if (!file.isFile) return null
        return try {
            if (mime == "application/pdf") PdfPages(file) else ImagePage(file)
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }
}

private class ImagePage(private val file: File) : PageSource {
    override val pageCount = 1

    override suspend fun render(index: Int, widthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0) return@withContext null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= widthPx) sample *= 2
        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }
}

private class PdfPages(file: File) : PageSource {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try {
        PdfRenderer(fd)
    } catch (e: IOException) {
        fd.close()
        throw e
    }
    private val gate = RenderGate() // PdfRenderer allows one open page at a time, and close must wait for it
    override val pageCount = renderer.pageCount

    override suspend fun render(index: Int, widthPx: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        gate.use {
            runCatching {
                renderer.openPage(index).use { page ->
                    val height = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp.asImageBitmap()
                }
            }.getOrNull()
        }
    }

    override fun close() = gate.close {
        renderer.close()
        fd.close()
    }
}
