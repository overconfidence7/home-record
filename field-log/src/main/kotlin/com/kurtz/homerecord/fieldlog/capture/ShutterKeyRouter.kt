package com.kurtz.homerecord.fieldlog.capture

import android.view.KeyEvent

/**
 * AC-57: routes volume-up/down to the shutter while FL-Capture is visible.
 * The activity forwards key presses here; the capture screen sets [onShutter] only while shown.
 */
class ShutterKeyRouter {
    @Volatile var onShutter: (() -> Unit)? = null

    /** True when the key was consumed (a shutter key with a visible capture screen). */
    fun onKeyDown(keyCode: Int, repeatCount: Int = 0): Boolean {
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) return false
        val handler = onShutter ?: return false
        if (repeatCount == 0) handler()
        return true
    }
}
