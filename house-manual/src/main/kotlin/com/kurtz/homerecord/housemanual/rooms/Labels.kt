package com.kurtz.homerecord.housemanual.rooms

import androidx.annotation.StringRes
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.model.ShutoffKind
import com.kurtz.homerecord.housemanual.R

@StringRes
fun ShutoffKind.labelRes(): Int = when (this) {
    ShutoffKind.WATER_MAIN -> R.string.shutoff_water_main
    ShutoffKind.GAS_MAIN -> R.string.shutoff_gas_main
    ShutoffKind.ELECTRICAL_MAIN -> R.string.shutoff_electrical_main
    ShutoffKind.HVAC_DISCONNECT -> R.string.shutoff_hvac_disconnect
    ShutoffKind.FIXTURE_VALVE -> R.string.shutoff_fixture_valve
    ShutoffKind.SUMP -> R.string.shutoff_sump
    ShutoffKind.CLEANOUT -> R.string.shutoff_cleanout
    ShutoffKind.OTHER -> R.string.shutoff_other
}

@StringRes
fun EquipmentCategory.labelRes(): Int = when (this) {
    EquipmentCategory.WATER_HEATER -> R.string.equip_water_heater
    EquipmentCategory.FURNACE -> R.string.equip_furnace
    EquipmentCategory.AC -> R.string.equip_ac
    EquipmentCategory.HEAT_PUMP -> R.string.equip_heat_pump
    EquipmentCategory.RANGE -> R.string.equip_range
    EquipmentCategory.REFRIGERATOR -> R.string.equip_refrigerator
    EquipmentCategory.DISHWASHER -> R.string.equip_dishwasher
    EquipmentCategory.WASHER -> R.string.equip_washer
    EquipmentCategory.DRYER -> R.string.equip_dryer
    EquipmentCategory.SUMP_PUMP -> R.string.equip_sump_pump
    EquipmentCategory.WATER_SOFTENER -> R.string.equip_water_softener
    EquipmentCategory.OTHER -> R.string.equip_other
}
