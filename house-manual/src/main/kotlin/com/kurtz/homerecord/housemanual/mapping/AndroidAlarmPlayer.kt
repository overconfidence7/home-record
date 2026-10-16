package com.kurtz.homerecord.housemanual.mapping

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.kurtz.homerecord.core.mapping.AlarmPlayer

/** Repeating beep at maximum alarm-stream volume plus a repeating vibration pattern (AC-37). */
class AndroidAlarmPlayer(context: Context) : AlarmPlayer {
    private val appContext = context.applicationContext
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Vibrator::class.java)
    }
    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null
    private var previousVolume = 0

    override var isPlaying: Boolean = false
        private set

    private val beep = object : Runnable {
        override fun run() {
            if (!isPlaying) return
            tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, BEEP_MS)
            handler.postDelayed(this, BEEP_PERIOD_MS)
        }
    }

    override fun start() {
        if (isPlaying) return
        isPlaying = true
        previousVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        tone = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
        handler.post(beep)
        vibrator?.vibrate(VibrationEffect.createWaveform(VIBRATION_PATTERN, 0))
    }

    override fun stop() {
        if (!isPlaying) return
        isPlaying = false
        handler.removeCallbacks(beep)
        tone?.release()
        tone = null
        vibrator?.cancel()
        audio.setStreamVolume(AudioManager.STREAM_ALARM, previousVolume, 0)
    }

    private companion object {
        const val BEEP_MS = 600
        const val BEEP_PERIOD_MS = 900L
        val VIBRATION_PATTERN = longArrayOf(0, 500, 300)
    }
}
