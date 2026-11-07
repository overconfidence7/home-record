package com.kurtz.homerecord.housemanual.more

import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.reminders.ClimateZone

/** The picker More has open over its list, if any. */
enum class MoreDialog { UNITS, ZONE }

/**
 * HM-More (T077): the rows' current values. [units] is this phone's display choice (AC-C4); [zone] is the home's
 * climate zone (AC-H12), which only someone who can edit the home changes ([canEdit] false: the row shows the zone
 * and opens nothing).
 */
data class MoreUiState(
    val units: UnitSystem = UnitSystem.IMPERIAL,
    val zone: ClimateZone? = null,
    val canEdit: Boolean = true,
    val dialog: MoreDialog? = null,
)
