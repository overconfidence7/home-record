package com.kurtz.homerecord.fieldlog

import android.app.Application
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import com.kurtz.homerecord.fieldlog.capture.CameraChoice
import com.kurtz.homerecord.ui.DebugStrictMode

class FieldLogApp : Application(), CameraXConfig.Provider {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        DebugStrictMode.enableIfDebuggable(this)
        container = AppContainer(this)
    }

    /**
     * CameraX checks the lenses a device advertises against the cameras that really exist, and the check fails on
     * devices that declare a lens they lack. Limit CameraX to the lenses the camera service actually lists.
     */
    override fun getCameraXConfig(): CameraXConfig {
        val builder = CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
        val facings = runCatching {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            manager.cameraIdList.mapNotNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) }
        }.getOrNull()
        if (facings != null) {
            val hasBack = CameraCharacteristics.LENS_FACING_BACK in facings
            val hasFront = CameraCharacteristics.LENS_FACING_FRONT in facings
            when (CameraChoice.pick(hasBack, hasFront)) {
                CameraChoice.Lens.BACK -> builder.setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)
                CameraChoice.Lens.FRONT -> builder.setAvailableCamerasLimiter(CameraSelector.DEFAULT_FRONT_CAMERA)
                CameraChoice.Lens.NONE -> Unit
            }
        }
        return builder.build()
    }
}
