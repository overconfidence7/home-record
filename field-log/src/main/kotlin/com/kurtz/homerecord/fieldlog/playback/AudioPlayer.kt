package com.kurtz.homerecord.fieldlog.playback

import android.media.MediaPlayer
import java.io.File

/** Side-effect boundary for audio playback of voice entries. */
interface AudioPlayer {
    /** Plays [file]; [onDone] runs when it ends or fails. Replaces anything already playing. */
    fun play(file: File, onDone: () -> Unit): Boolean

    fun stop()
}

class MediaPlayerAudioPlayer : AudioPlayer {
    private var player: MediaPlayer? = null

    override fun play(file: File, onDone: () -> Unit): Boolean {
        stop()
        return try {
            val p = MediaPlayer()
            player = p
            p.setDataSource(file.path)
            p.setOnCompletionListener { stop(); onDone() }
            p.setOnErrorListener { _, _, _ -> stop(); onDone(); true }
            p.prepare()
            p.start()
            true
        } catch (e: java.io.IOException) {
            stop()
            false
        } catch (e: IllegalStateException) {
            stop()
            false
        }
    }

    override fun stop() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
    }
}
