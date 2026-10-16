package com.kurtz.homerecord.housemanual.equipment

import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kurtz.homerecord.core.ocr.TextRecognizer
import android.content.Context
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** Bundled (offline) ML Kit Latin text recognition (AC-49). */
class MlKitTextRecognizer(private val context: Context) : TextRecognizer {
    override suspend fun recognize(image: File): List<String> {
        val input = InputImage.fromFilePath(context, Uri.fromFile(image))
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            suspendCancellableCoroutine { cont ->
                client.process(input)
                    .addOnSuccessListener { text ->
                        cont.resume(text.textBlocks.flatMap { block -> block.lines.map { it.text } })
                    }
                    .addOnFailureListener { cont.resumeWithException(it) }
            }
        } finally {
            client.close()
        }
    }
}
