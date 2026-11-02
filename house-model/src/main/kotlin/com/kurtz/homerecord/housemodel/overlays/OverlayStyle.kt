package com.kurtz.homerecord.housemodel.overlays

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cable
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Doorbell
import androidx.compose.material.icons.rounded.GasMeter
import androidx.compose.material.icons.rounded.Grid4x4
import androidx.compose.material.icons.rounded.HotTub
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Outlet
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Plumbing
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VerticalAlignTop
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.blueprint.LayerPalette

/** Each kind's icon (AC-C25, ui-standards rule 26: every kind carries an icon as well as its color). */
val OverlayKind.icon: ImageVector
    get() = when (this) {
        OverlayKind.WATER_HOT -> Icons.Rounded.HotTub
        OverlayKind.WATER_COLD -> Icons.Rounded.WaterDrop
        OverlayKind.DRAIN -> Icons.Rounded.Plumbing
        OverlayKind.VENT -> Icons.Rounded.VerticalAlignTop
        OverlayKind.GAS -> Icons.Rounded.GasMeter
        OverlayKind.ELECTRICAL_WIRE -> Icons.Rounded.Cable
        OverlayKind.LV_DATA -> Icons.Rounded.SettingsEthernet
        OverlayKind.LV_COAX -> Icons.Rounded.Tv
        OverlayKind.LV_SPEAKER -> Icons.Rounded.Speaker
        OverlayKind.LV_SECURITY -> Icons.Rounded.Security
        OverlayKind.LV_DOORBELL -> Icons.Rounded.Doorbell
        OverlayKind.DUCT -> Icons.Rounded.Air
        OverlayKind.STUD -> Icons.Rounded.ViewColumn
        OverlayKind.BLOCKING -> Icons.Rounded.ViewStream
        OverlayKind.JUNCTION_BOX -> Icons.Rounded.CropSquare
        OverlayKind.OUTLET -> Icons.Rounded.Outlet
        OverlayKind.SWITCH -> Icons.Rounded.ToggleOn
        OverlayKind.LIGHT -> Icons.Rounded.Lightbulb
        OverlayKind.REGISTER -> Icons.Rounded.Grid4x4
        OverlayKind.VALVE -> Icons.Rounded.Tune
        OverlayKind.CLEANOUT -> Icons.Rounded.Adjust
        OverlayKind.OTHER -> Icons.Rounded.Place
    }

/** The kind's layer color (AC-C25), the structure slate for kinds on no layer. */
fun OverlayKind.color(dark: Boolean): Color = layer?.let { LayerPalette.color(it, dark) } ?: LayerPalette.structure(dark)

@get:StringRes
val OverlayKind.nameRes: Int
    get() = when (this) {
        OverlayKind.WATER_HOT -> R.string.overlay_kind_water_hot
        OverlayKind.WATER_COLD -> R.string.overlay_kind_water_cold
        OverlayKind.DRAIN -> R.string.overlay_kind_drain
        OverlayKind.VENT -> R.string.overlay_kind_vent
        OverlayKind.GAS -> R.string.overlay_kind_gas
        OverlayKind.ELECTRICAL_WIRE -> R.string.overlay_kind_electrical_wire
        OverlayKind.LV_DATA -> R.string.overlay_kind_lv_data
        OverlayKind.LV_COAX -> R.string.overlay_kind_lv_coax
        OverlayKind.LV_SPEAKER -> R.string.overlay_kind_lv_speaker
        OverlayKind.LV_SECURITY -> R.string.overlay_kind_lv_security
        OverlayKind.LV_DOORBELL -> R.string.overlay_kind_lv_doorbell
        OverlayKind.DUCT -> R.string.overlay_kind_duct
        OverlayKind.STUD -> R.string.overlay_kind_stud
        OverlayKind.BLOCKING -> R.string.overlay_kind_blocking
        OverlayKind.JUNCTION_BOX -> R.string.overlay_kind_junction_box
        OverlayKind.OUTLET -> R.string.overlay_kind_outlet
        OverlayKind.SWITCH -> R.string.overlay_kind_switch
        OverlayKind.LIGHT -> R.string.overlay_kind_light
        OverlayKind.REGISTER -> R.string.overlay_kind_register
        OverlayKind.VALVE -> R.string.overlay_kind_valve
        OverlayKind.CLEANOUT -> R.string.overlay_kind_cleanout
        OverlayKind.OTHER -> R.string.overlay_kind_other
    }

@get:StringRes
val OverlayDepth.nameRes: Int
    get() = when (this) {
        OverlayDepth.BEHIND_DRYWALL -> R.string.overlay_depth_behind_drywall
        OverlayDepth.IN_SLAB -> R.string.overlay_depth_in_slab
        OverlayDepth.IN_CEILING -> R.string.overlay_depth_in_ceiling
        OverlayDepth.IN_ATTIC -> R.string.overlay_depth_in_attic
        OverlayDepth.UNDERGROUND -> R.string.overlay_depth_underground
        OverlayDepth.EXPOSED -> R.string.overlay_depth_exposed
    }
