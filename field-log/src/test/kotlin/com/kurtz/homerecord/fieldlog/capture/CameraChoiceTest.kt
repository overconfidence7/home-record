package com.kurtz.homerecord.fieldlog.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraChoiceTest {
    @Test fun prefersBack() = assertEquals(CameraChoice.Lens.BACK, CameraChoice.pick(true, true))
    @Test fun fallsBackToFront() = assertEquals(CameraChoice.Lens.FRONT, CameraChoice.pick(false, true))
    @Test fun noneWhenNoCamera() = assertEquals(CameraChoice.Lens.NONE, CameraChoice.pick(false, false))
}
