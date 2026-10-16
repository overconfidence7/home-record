package com.kurtz.homerecord.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Square thumbnail decoded off the main thread and down-sampled; blank placeholder when [file] is null or unreadable. */
@Composable
fun PhotoTile(file: File?, contentDescription: String?, modifier: Modifier = Modifier, size: Dp = 56.dp, targetPx: Int = 256) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = if (file == null) null else withContext(Dispatchers.IO) { decodeSampled(file, targetPx) }
    }
    val shape = RoundedCornerShape(8.dp)
    val base = modifier.size(size).clip(shape)
    val image = bitmap
    if (image == null) {
        Box(base.background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        Image(image, contentDescription, base, contentScale = ContentScale.Crop)
    }
}

private fun decodeSampled(file: File, targetPx: Int): ImageBitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= targetPx) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.path, opts)?.asImageBitmap()
}

/** Photo at its natural aspect ratio, decoded off the main thread; nothing is drawn when [file] is missing. */
@Composable
fun PhotoImage(file: File?, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.FillWidth, targetPx: Int = 1600) {
    val bitmap by produceState<ImageBitmap?>(null, file) {
        value = if (file == null) null else withContext(Dispatchers.IO) { decodeSampled(file, targetPx) }
    }
    bitmap?.let { Image(it, contentDescription, modifier, contentScale = contentScale) }
}
