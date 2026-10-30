package com.kurtz.homerecord.housemodel.items

import androidx.annotation.StringRes
import com.kurtz.homerecord.core.model.ConsumableDefault
import com.kurtz.homerecord.core.model.ItemSubtype
import com.kurtz.homerecord.core.model.ReminderDefault
import com.kurtz.homerecord.housemodel.R

/** The subtype's display name, also the default name of a new item of that type (cr:seed-strings). */
@get:StringRes
val ItemSubtype.labelRes: Int
    get() = subtypeLabels[id] ?: R.string.item_subtype_other

/** The seeded consumable's spec ("Furnace filter"). */
@get:StringRes
val ConsumableDefault.specRes: Int
    get() = consumableSpecs[specKey] ?: R.string.consumable_default_battery

/** The seeded reminder's title ("HVAC service"). */
@get:StringRes
val ReminderDefault.titleRes: Int
    get() = reminderTitles[titleKey] ?: R.string.reminder_default_hvac_service

/** Every key in :core's catalog with its resource; ItemSubtypeLabelsTest checks none falls through to the fallback. */
internal val subtypeLabelKeys: Set<String> get() = subtypeLabels.keys
internal val consumableSpecKeys: Set<String> get() = consumableSpecs.keys
internal val reminderTitleKeys: Set<String> get() = reminderTitles.keys

private val subtypeLabels: Map<String, Int> = mapOf(
    "refrigerator" to R.string.item_subtype_refrigerator,
    "freezer" to R.string.item_subtype_freezer,
    "range" to R.string.item_subtype_range,
    "oven" to R.string.item_subtype_oven,
    "cooktop" to R.string.item_subtype_cooktop,
    "range_hood" to R.string.item_subtype_range_hood,
    "dishwasher" to R.string.item_subtype_dishwasher,
    "microwave" to R.string.item_subtype_microwave,
    "disposal" to R.string.item_subtype_disposal,
    "washer" to R.string.item_subtype_washer,
    "dryer" to R.string.item_subtype_dryer,
    "wine_fridge" to R.string.item_subtype_wine_fridge,
    "furnace" to R.string.item_subtype_furnace,
    "boiler" to R.string.item_subtype_boiler,
    "air_handler" to R.string.item_subtype_air_handler,
    "heat_pump" to R.string.item_subtype_heat_pump,
    "ac_condenser" to R.string.item_subtype_ac_condenser,
    "mini_split" to R.string.item_subtype_mini_split,
    "thermostat" to R.string.item_subtype_thermostat,
    "humidifier" to R.string.item_subtype_humidifier,
    "dehumidifier" to R.string.item_subtype_dehumidifier,
    "erv_hrv" to R.string.item_subtype_erv_hrv,
    "whole_house_fan" to R.string.item_subtype_whole_house_fan,
    "radiant" to R.string.item_subtype_radiant,
    "water_heater" to R.string.item_subtype_water_heater,
    "tankless_heater" to R.string.item_subtype_tankless_heater,
    "water_softener" to R.string.item_subtype_water_softener,
    "water_filter" to R.string.item_subtype_water_filter,
    "well_pump" to R.string.item_subtype_well_pump,
    "pressure_tank" to R.string.item_subtype_pressure_tank,
    "sump_pump" to R.string.item_subtype_sump_pump,
    "ejector_pump" to R.string.item_subtype_ejector_pump,
    "sewage_pump" to R.string.item_subtype_sewage_pump,
    "backflow_preventer" to R.string.item_subtype_backflow_preventer,
    "prv" to R.string.item_subtype_prv,
    "expansion_tank" to R.string.item_subtype_expansion_tank,
    "irrigation_controller" to R.string.item_subtype_irrigation_controller,
    "breaker_panel" to R.string.item_subtype_breaker_panel,
    "sub_panel" to R.string.item_subtype_sub_panel,
    "generator" to R.string.item_subtype_generator,
    "transfer_switch" to R.string.item_subtype_transfer_switch,
    "ev_charger" to R.string.item_subtype_ev_charger,
    "solar_inverter" to R.string.item_subtype_solar_inverter,
    "surge_protector" to R.string.item_subtype_surge_protector,
    "water_main" to R.string.item_subtype_water_main,
    "gas_main" to R.string.item_subtype_gas_main,
    "electrical_main" to R.string.item_subtype_electrical_main,
    "hvac_disconnect" to R.string.item_subtype_hvac_disconnect,
    "fixture_shutoff" to R.string.item_subtype_fixture_shutoff,
    "sprinkler" to R.string.item_subtype_sprinkler,
    "sump" to R.string.item_subtype_sump,
    "cleanout" to R.string.item_subtype_cleanout,
    "propane_tank" to R.string.item_subtype_propane_tank,
    "smoke_detector" to R.string.item_subtype_smoke_detector,
    "co_detector" to R.string.item_subtype_co_detector,
    "fire_extinguisher" to R.string.item_subtype_fire_extinguisher,
    "security_panel" to R.string.item_subtype_security_panel,
    "camera" to R.string.item_subtype_camera,
    "doorbell" to R.string.item_subtype_doorbell,
    "radon_fan" to R.string.item_subtype_radon_fan,
    "faucet" to R.string.item_subtype_faucet,
    "toilet" to R.string.item_subtype_toilet,
    "shower_valve" to R.string.item_subtype_shower_valve,
    "tub" to R.string.item_subtype_tub,
    "sink" to R.string.item_subtype_sink,
    "garbage_disposal" to R.string.item_subtype_garbage_disposal,
    "garage_door_opener" to R.string.item_subtype_garage_door_opener,
    "garage_door" to R.string.item_subtype_garage_door,
    "entry_door" to R.string.item_subtype_entry_door,
    "lock" to R.string.item_subtype_lock,
    "gate" to R.string.item_subtype_gate,
    "roof" to R.string.item_subtype_roof,
    "gutters" to R.string.item_subtype_gutters,
    "siding" to R.string.item_subtype_siding,
    "windows" to R.string.item_subtype_windows,
    "deck" to R.string.item_subtype_deck,
    "fence" to R.string.item_subtype_fence,
    "driveway" to R.string.item_subtype_driveway,
    "chimney" to R.string.item_subtype_chimney,
    "paint" to R.string.item_subtype_paint,
    "flooring" to R.string.item_subtype_flooring,
    "tile" to R.string.item_subtype_tile,
    "countertop" to R.string.item_subtype_countertop,
    "cabinets" to R.string.item_subtype_cabinets,
    "trim" to R.string.item_subtype_trim,
    "router" to R.string.item_subtype_router,
    "modem" to R.string.item_subtype_modem,
    "mesh_node" to R.string.item_subtype_mesh_node,
    "network_switch" to R.string.item_subtype_network_switch,
    "hub" to R.string.item_subtype_hub,
    "nvr" to R.string.item_subtype_nvr,
    "other" to R.string.item_subtype_other,
)
private val consumableSpecs: Map<String, Int> = mapOf(
    "battery" to R.string.consumable_default_battery,
    "erv_core_filter" to R.string.consumable_default_erv_core_filter,
    "furnace_filter" to R.string.consumable_default_furnace_filter,
    "grease_filter" to R.string.consumable_default_grease_filter,
    "humidifier_pad" to R.string.consumable_default_humidifier_pad,
    "mini_split_filter" to R.string.consumable_default_mini_split_filter,
    "water_filter" to R.string.consumable_default_water_filter,
)
private val reminderTitles: Map<String, Int> = mapOf(
    "add_softener_salt" to R.string.reminder_default_add_softener_salt,
    "clean_dryer_vent" to R.string.reminder_default_clean_dryer_vent,
    "clean_gutters" to R.string.reminder_default_clean_gutters,
    "flush_tankless_heater" to R.string.reminder_default_flush_tankless_heater,
    "flush_water_heater" to R.string.reminder_default_flush_water_heater,
    "hvac_service" to R.string.reminder_default_hvac_service,
    "replace_co_detector_battery" to R.string.reminder_default_replace_co_detector_battery,
    "replace_erv_core_filter" to R.string.reminder_default_replace_erv_core_filter,
    "replace_furnace_filter" to R.string.reminder_default_replace_furnace_filter,
    "replace_grease_filter" to R.string.reminder_default_replace_grease_filter,
    "replace_humidifier_pad" to R.string.reminder_default_replace_humidifier_pad,
    "replace_mini_split_filter" to R.string.reminder_default_replace_mini_split_filter,
    "replace_smoke_detector_battery" to R.string.reminder_default_replace_smoke_detector_battery,
    "replace_water_filter" to R.string.reminder_default_replace_water_filter,
    "test_sump_pump" to R.string.reminder_default_test_sump_pump,
)
