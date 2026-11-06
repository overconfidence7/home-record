package com.kurtz.homerecord.core.mapping

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Whether the phone is charging from external power, as it changes (AC-C42). Behind an interface for the JVM tests. */
interface PowerMonitor {
    /** Emits the current state first, then every change. Collect only while the mapping screen is showing. */
    fun plugged(): Flow<Boolean>
}

/** The mapping alarm: a loud, looping sound on the alarm stream plus vibration (AC-C42). */
interface MappingAlarm {
    fun start()
    fun stop()
}

/** [PowerMonitor] over the sticky `ACTION_BATTERY_CHANGED` broadcast, which also fires on plug and unplug. */
class BatteryPowerMonitor(private val context: Context) : PowerMonitor {
    override fun plugged(): Flow<Boolean> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(isPlugged(intent))
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val sticky = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        if (sticky != null) trySend(isPlugged(sticky))
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private fun isPlugged(intent: Intent): Boolean = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
}

/** [MappingAlarm] playing the default alarm sound (looping) and a repeating vibration. */
class SystemMappingAlarm(private val context: Context) : MappingAlarm {
    private var ringtone: Ringtone? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
    }

    override fun start() {
        if (ringtone?.isPlaying == true) return
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = uri?.let { RingtoneManager.getRingtone(context, it) }?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
        try {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 400), 0))
        } catch (_: SecurityException) {
            // No VIBRATE permission in this build: the sound alone still works.
        }
    }

    override fun stop() {
        ringtone?.stop()
        ringtone = null
        try {
            vibrator?.cancel()
        } catch (_: SecurityException) {
            // See start().
        }
    }
}
