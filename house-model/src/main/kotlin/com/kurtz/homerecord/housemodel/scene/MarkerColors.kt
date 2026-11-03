package com.kurtz.homerecord.housemodel.scene

import androidx.compose.ui.graphics.Color
import com.kurtz.homerecord.housemodel.blueprint.BlueprintLayer
import com.kurtz.homerecord.housemodel.blueprint.LayerPalette
import com.kurtz.homerecord.housemodel.overlays.color

/**
 * A 3D marker's color (AC-C23): its overlay kind's layer color, or the Items color for an installed item. Always the light
 * palette's deeper shades, in both themes: the markers sit on the scene's pale wall tints and floors, not on the app surface.
 */
object MarkerColors {
    fun of(marker: SceneMarker): Color =
        marker.overlayKind?.color(dark = false) ?: LayerPalette.color(marker.layer ?: BlueprintLayer.ITEMS, dark = false)
}
