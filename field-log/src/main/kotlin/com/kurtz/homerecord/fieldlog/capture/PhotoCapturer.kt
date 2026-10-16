package com.kurtz.homerecord.fieldlog.capture

import java.io.File

/** Side-effect boundary for the camera: writes one JPEG to [file]; true on success. */
interface PhotoCapturer {
    suspend fun capture(file: File): Boolean
}
