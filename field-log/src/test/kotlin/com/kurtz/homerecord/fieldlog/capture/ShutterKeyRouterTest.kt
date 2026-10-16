package com.kurtz.homerecord.fieldlog.capture

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShutterKeyRouterTest {
    @Test fun volumeUpAndDownBothFireShutterWhenVisible() {
        val router = ShutterKeyRouter()
        var count = 0
        router.onShutter = { count++ }
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP))
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals(2, count)
    }

    @Test fun keysPassThroughWhenCaptureNotVisible() {
        val router = ShutterKeyRouter()
        assertFalse(router.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP))
    }

    @Test fun otherKeysAndHeldRepeatsDoNotFire() {
        val router = ShutterKeyRouter()
        var count = 0
        router.onShutter = { count++ }
        assertFalse(router.onKeyDown(KeyEvent.KEYCODE_BACK))
        assertTrue(router.onKeyDown(KeyEvent.KEYCODE_VOLUME_UP, repeatCount = 3))
        assertEquals(0, count)
    }
}
