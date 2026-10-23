package com.kurtz.homerecord.core.media

/** Where the camera should write: [uri] goes to the camera app, [path] is the file it fills in app-private storage. */
data class CaptureTarget(val uri: String, val path: String)

/** App-private photo storage (`filesDir/media/<uuid>.jpg`). The camera and gallery sit behind this so tests can fake it. */
interface MediaFiles {
    fun newCaptureTarget(): CaptureTarget

    /** Copies a picked image into app-private storage; returns its path, or null when it can't be read. */
    suspend fun import(uri: String): String?

    /** Whether a finished capture actually wrote a file (a cancelled camera leaves none). */
    fun exists(path: String): Boolean

    fun discard(path: String)
}
