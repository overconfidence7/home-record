package com.kurtz.homerecord.housemodel.scene

import org.junit.Assert.assertArrayEquals
import org.junit.Test

// AC-C20: the live 3D floor clears to the screen's surface colour (opaque, linear for Filament).
class SceneClearColorTest {
    @Test
    fun whiteAndBlackStayAtTheEnds() {
        assertArrayEquals(doubleArrayOf(1.0, 1.0, 1.0, 1.0), SceneClearColor.linear(0xFFFFFFFF.toInt()), 1e-6)
        assertArrayEquals(doubleArrayOf(0.0, 0.0, 0.0, 1.0), SceneClearColor.linear(0xFF000000.toInt()), 1e-6)
    }

    @Test
    fun midGreyIsLinearised() {
        // sRGB 0x80 (128) is about 0.2158 linear.
        val c = SceneClearColor.linear(0xFF808080.toInt())
        assertArrayEquals(doubleArrayOf(0.2158, 0.2158, 0.2158, 1.0), c, 1e-3)
    }

    @Test
    fun channelsKeepTheirOrderAndAlphaIsAlwaysOpaque() {
        val c = SceneClearColor.linear(0x00FF0000)
        assertArrayEquals(doubleArrayOf(1.0, 0.0, 0.0, 1.0), c, 1e-6)
    }
}
