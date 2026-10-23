package com.kurtz.homerecord.housemodel.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Plumbing
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/** Resolves the icon names kept in :core's ItemCategory (which has no Compose) to vectors. */
fun itemIcon(name: String): ImageVector = when (name) {
    "ElectricalServices" -> Icons.Rounded.ElectricalServices
    "FormatPaint" -> Icons.Rounded.FormatPaint
    "Garage" -> Icons.Rounded.Garage
    "HealthAndSafety" -> Icons.Rounded.HealthAndSafety
    "Home" -> Icons.Rounded.Home
    "Kitchen" -> Icons.Rounded.Kitchen
    "Plumbing" -> Icons.Rounded.Plumbing
    "PowerOff" -> Icons.Rounded.PowerOff
    "Router" -> Icons.Rounded.Router
    "Thermostat" -> Icons.Rounded.Thermostat
    "WaterDrop" -> Icons.Rounded.WaterDrop
    else -> Icons.Rounded.Category
}
