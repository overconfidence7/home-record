package com.kurtz.homerecord.fieldlog.capture

import java.io.File

/** Side-effect boundary for the microphone: records AAC/.m4a into a file. */
interface VoiceRecorder {
    /** Starts recording into [file]; false if the recorder could not start. */
    fun start(file: File): Boolean

    /** Stops recording; false if the file is unusable. */
    fun stop(): Boolean
}

/** AC-59 cap logic: recordings stop automatically at 3:00. */
object VoiceCap {
    const val MAX_MS = 180_000L
    const val TICK_MS = 250L

    fun reached(elapsedMs: Long): Boolean = elapsedMs >= MAX_MS

    fun clamp(elapsedMs: Long): Long = elapsedMs.coerceIn(0L, MAX_MS)

    /** `m:ss` for the elapsed-time readout. */
    fun format(elapsedMs: Long): String {
        val seconds = clamp(elapsedMs) / 1000
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }
}
