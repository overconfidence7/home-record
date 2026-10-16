package com.kurtz.homerecord.fieldlog.capture

import android.content.Context
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** CameraX-backed [PhotoCapturer]; the screen supplies the bound [ImageCapture] use case. */
class CameraXPhotoCapturer(private val context: Context) : PhotoCapturer {
    @Volatile var imageCapture: ImageCapture? = null

    override suspend fun capture(file: File): Boolean {
        val use = imageCapture ?: return false
        return suspendCancellableCoroutine { cont ->
            use.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
            )
        }
    }
}
