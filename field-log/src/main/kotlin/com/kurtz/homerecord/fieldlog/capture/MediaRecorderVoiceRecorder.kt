package com.kurtz.homerecord.fieldlog.capture

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class MediaRecorderVoiceRecorder(private val context: Context) : VoiceRecorder {
    private var recorder: MediaRecorder? = null

    @Suppress("DEPRECATION")
    override fun start(file: File): Boolean {
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            true
        } catch (e: Exception) {
            r.release()
            false
        }
    }

    override fun stop(): Boolean {
        val r = recorder ?: return false
        recorder = null
        return try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            false
        } finally {
            r.release()
        }
    }
}
