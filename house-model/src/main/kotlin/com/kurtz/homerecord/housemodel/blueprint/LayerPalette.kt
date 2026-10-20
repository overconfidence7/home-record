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
