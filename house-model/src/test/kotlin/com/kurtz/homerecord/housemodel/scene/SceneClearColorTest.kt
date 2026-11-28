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

    // [auto:3d:house-manual:house-manual]: with post-processing off nothing encodes the clear, so it is given as stored.
    @Test
    fun directOutputKeepsTheStoredSurfaceColour() {
        val c = SceneClearColor.forDirectOutput(0xFFF4F3FA.toInt())
        assertArrayEquals(doubleArrayOf(0xF4 / 255.0, 0xF3 / 255.0, 0xFA / 255.0, 1.0), c, 1e-9)
    }

    @Test
    fun directOutputIsAlwaysOpaque() {
        assertArrayEquals(doubleArrayOf(0.0, 0.0, 0.0, 1.0), SceneClearColor.forDirectOutput(0x00000000), 1e-9)
    }

    // AC-C20: SceneView's effect sets an opaque view's clear to black; the 56adcda2 run showed black around the house.
    @Test
    fun sceneViewsBlackClearIsReapplied() {
        val want = SceneClearColor.forDirectOutput(0xFFF4F3FA.toInt())
        org.junit.Assert.assertTrue(SceneClearColor.needsReapply(true, doubleArrayOf(0.0, 0.0, 0.0, 1.0), want))
        org.junit.Assert.assertTrue(SceneClearColor.needsReapply(false, want.copyOf(), want))
        org.junit.Assert.assertTrue(SceneClearColor.needsReapply(true, null, want))
    }

    @Test
    fun theSurfaceClearIsLeftAlone() {
        val want = SceneClearColor.forDirectOutput(0xFF1D1B20.toInt())
        org.junit.Assert.assertFalse(SceneClearColor.needsReapply(true, want.copyOf(), want))
        // Filament hands the colour back as floats.
        val roundTrip = want.map { it.toFloat().toDouble() }.toDoubleArray()
        org.junit.Assert.assertFalse(SceneClearColor.needsReapply(true, roundTrip, want))
    }
}
