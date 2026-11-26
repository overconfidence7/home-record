package com.kurtz.homerecord.housemodel.scene

import kotlin.math.pow

/**
 * The colour the opaque 3D scene clears to: the screen's surface colour (an sRGB ARGB int) as the linear RGBA Filament
 * expects in [com.google.android.filament.Renderer.ClearOptions.clearColor], so the house sits on the same surface in
 * light and dark as the screen around it. Always fully opaque.
 */
object SceneClearColor {
    fun linear(argb: Int): DoubleArray = doubleArrayOf(
        toLinear((argb shr 16) and 0xFF),
        toLinear((argb shr 8) and 0xFF),
        toLinear(argb and 0xFF),
        1.0,
    )

    private fun toLinear(channel: Int): Double {
        val c = channel / 255.0
        val l = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return l
    }
}
