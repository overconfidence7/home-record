package com.kurtz.homerecord.fieldlog.capture

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import com.kurtz.homerecord.core.photo.JobNotes
import com.kurtz.homerecord.core.photo.SpeechTranscriber

/** Records a voice memo as AAC in an MP4 container, capped at [JobNotes.MAX_VOICE_MS] (AC-F27). */
class AndroidVoiceRecorder(private val context: Context) : com.kurtz.homerecord.core.photo.VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var startedAt = 0L

    override fun start(path: String): Boolean {
        stop()
        val next = MediaRecorder(context)
        return runCatching {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioEncodingBitRate(64_000)
            next.setAudioSamplingRate(44_100)
            next.setMaxDuration(JobNotes.MAX_VOICE_MS.toInt())
            next.setOutputFile(path)
            next.prepare()
            next.start()
            recorder = next
            startedAt = SystemClock.elapsedRealtime()
        }.onFailure { next.release() }.isSuccess
    }

    override fun stop(): Long? {
        val active = recorder ?: return null
        recorder = null
        val length = SystemClock.elapsedRealtime() - startedAt
        val stopped = runCatching { active.stop() }.isSuccess
        active.release()
        return length.takeIf { stopped && it > 0 }
    }
}

/**
 * Android's SpeechRecognizer listens to the microphone, not to a file, so a finished memo cannot be transcribed
 * on every device. Memos are kept without a transcript and the person can type one (AC-F27, best effort).
 */
object NoTranscriber : SpeechTranscriber {
    override suspend fun transcribe(path: String): String? = null
}
