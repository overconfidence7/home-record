package com.kurtz.homerecord.core.ocr

import java.io.File

/** On-device text recognition (AC-45, AC-49). Returns recognized lines in reading order; empty when no text was found. */
interface TextRecognizer {
    suspend fun recognize(image: File): List<String>
}
