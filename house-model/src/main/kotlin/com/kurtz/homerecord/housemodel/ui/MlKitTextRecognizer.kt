package com.kurtz.homerecord.housemodel.ui

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.kurtz.homerecord.core.ocr.TextRecognizer
import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Bundled ML Kit Latin text recognition: no network, so data plates scan offline (AC-C31). */
class MlKitTextRecognizer(private val context: Context) : TextRecognizer {
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognizeLines(path: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val image = InputImage.fromFilePath(context, Uri.fromFile(File(path)))
            val text = Tasks.await(client.process(image))
            text.textBlocks.flatMap { block -> block.lines.map { it.text } }
        } catch (e: IOException) {
            emptyList()
        } catch (e: java.util.concurrent.ExecutionException) {
            emptyList()
        }
    }
}
