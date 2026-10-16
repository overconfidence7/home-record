package com.kurtz.homerecord.fieldlog.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCapTest {
    @Test fun capIsThreeMinutes() {
        assertFalse(VoiceCap.reached(179_999))
        assertTrue(VoiceCap.reached(180_000))
    }

    @Test fun clampKeepsDurationInRange() {
        assertEquals(180_000L, VoiceCap.clamp(200_000))
        assertEquals(0L, VoiceCap.clamp(-5))
    }

    @Test fun formatsMinutesAndSeconds() {
        assertEquals("0:00", VoiceCap.format(0))
        assertEquals("1:05", VoiceCap.format(65_400))
        assertEquals("3:00", VoiceCap.format(999_999))
    }
}
