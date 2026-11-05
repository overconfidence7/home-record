package com.kurtz.homerecord.housemodel.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.kurtz.homerecord.core.units.UnitSystem

/**
 * The units the shared house screens show and read lengths in (AC-C4). Field Log provides the phone's choice from its
 * `UnitsStore` around the whole app; without a provider (House Manual today) lengths are feet and inches.
 */
val LocalUnitSystem = staticCompositionLocalOf { UnitSystem.IMPERIAL }
