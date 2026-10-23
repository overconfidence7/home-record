package com.kurtz.homerecord.core.ocr

/** On-device text recognition of a stored photo; the ML Kit implementation lives in the app layer so tests can fake it. */
interface TextRecognizer {
    /** Recognized text lines in reading order, or an empty list when nothing legible was found or recognition failed. */
    suspend fun recognizeLines(path: String): List<String>
}
