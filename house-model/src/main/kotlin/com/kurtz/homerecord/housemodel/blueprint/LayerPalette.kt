package com.kurtz.homerecord.housemodel.blueprint

import androidx.compose.ui.graphics.Color

/** One color per layer; shared by the blueprint, 3D and the legend chips. Chosen to stay apart in light and dark. */
object LayerPalette {
    fun color(layer: BlueprintLayer, dark: Boolean): Color = when (layer) {
        BlueprintLayer.ITEMS -> if (dark) Color(0xFFB39DDB) else Color(0xFF5E35B1)
        BlueprintLayer.ELECTRICAL -> if (dark) Color(0xFFFFCA28) else Color(0xFFB07800)
        BlueprintLayer.PLUMBING -> if (dark) Color(0xFF4FC3F7) else Color(0xFF0277BD)
        BlueprintLayer.HVAC -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        BlueprintLayer.LOW_VOLTAGE -> if (dark) Color(0xFFF48FB1) else Color(0xFFC2185B)
        BlueprintLayer.GAS -> if (dark) Color(0xFFFF8A65) else Color(0xFFD84315)
        BlueprintLayer.DRAIN_VENT -> if (dark) Color(0xFFA1887F) else Color(0xFF6D4C41)
    }
}

/**
 * Paint-like tints for the 3D view's untextured walls (AC-C20), one per [com.kurtz.homerecord.housemodel.scene.RoomTint]
 * group, plus the neutral floor and the lighter window glass. Light enough that the lit material still shows wall edges.
 */
object RoomPalette {
    private val tints = listOf(
        Color(0xFFF3E3C3), // kitchen: warm cream
        Color(0xFFDCE6D2), // living and dining: sage
        Color(0xFFD9E1EF), // bedrooms: soft blue
        Color(0xFFD3ECEA), // baths and laundry: aqua
        Color(0xFFEADBEA), // office: lilac
        Color(0xFFDDDAD5), // garage, basement, utility: concrete
        Color(0xFFEFE6DA), // halls, closets, mudroom: linen
        Color(0xFFE8DCCB), // porch, deck, attic, other: sand
    )

    fun tint(index: Int): Color = tints[Math.floorMod(index, tints.size)]

    val floor = Color(0xFFBDB6AC)
    val glass = Color(0x66B3D9F2)

    /** The static picture's stand-in for a wall photo, which it cannot draw. */
    val photo = Color(0xFFC9B79C)
}
