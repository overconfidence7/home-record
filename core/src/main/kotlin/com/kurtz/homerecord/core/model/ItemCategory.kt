package com.kurtz.homerecord.core.model

enum class ConsumableKind { FILTER, BULB, BATTERY, CARTRIDGE, BELT, SALT, UV_LAMP, OTHER }

/** A consumable seeded when an item of this subtype is created; [specKey] names its text in house-model's resources. */
data class ConsumableDefault(val kind: ConsumableKind, val specKey: String)

/** A recurring reminder seeded when an item of this subtype is created; [titleKey] names its text in house-model's resources. */
data class ReminderDefault(val titleKey: String, val intervalMonths: Int)

/**
 * [icon] is a Material icon name resolved in :core-ui, so :core stays free of Compose. The display name is a string
 * resource keyed on [id] (house-model `ItemSubtypeLabels`), never English in code.
 */
data class ItemSubtype(
    val id: String,
    val category: ItemCategory,
    val icon: String? = null,
    val consumables: List<ConsumableDefault> = emptyList(),
    val reminders: List<ReminderDefault> = emptyList(),
) {
    val iconName: String get() = icon ?: category.icon
}

/** Display names live in string resources (house-model `ItemCategoryLabels`); code keys on the enum, never on a label. */
enum class ItemCategory(val icon: String, subtypes: List<SubtypeSpec>) {
    APPLIANCES(
        "Kitchen",
        listOf(
            s("refrigerator", filter = "water_filter" to 6),
            s("freezer"), s("range"), s("oven"), s("cooktop"),
            s("range_hood", filter = "grease_filter" to 3),
            s("dishwasher"), s("microwave"), s("disposal"),
            s("washer"), s("dryer", reminder = "clean_dryer_vent" to 12),
            s("wine_fridge"),
        ),
    ),
    HEATING_COOLING(
        "Thermostat",
        listOf(
            s("furnace", filter = "furnace_filter" to 3, reminder = "hvac_service" to 12),
            s("boiler", reminder = "hvac_service" to 12),
            s("air_handler", filter = "furnace_filter" to 3),
            s("heat_pump", reminder = "hvac_service" to 12),
            s("ac_condenser", reminder = "hvac_service" to 12),
            s("mini_split", filter = "mini_split_filter" to 3),
            s("thermostat", battery = true),
            s("humidifier", filter = "humidifier_pad" to 3),
            s("dehumidifier"), s("erv_hrv", filter = "erv_core_filter" to 6),
            s("whole_house_fan"), s("radiant"),
        ),
    ),
    WATER(
        "WaterDrop",
        listOf(
            s("water_heater", reminder = "flush_water_heater" to 12),
            s("tankless_heater", reminder = "flush_tankless_heater" to 12),
            s("water_softener", reminder = "add_softener_salt" to 2),
            s("water_filter", filter = "water_filter" to 6),
            s("well_pump"), s("pressure_tank"),
            s("sump_pump", reminder = "test_sump_pump" to 3),
            s("ejector_pump"), s("sewage_pump"),
            s("backflow_preventer"), s("prv"),
            s("expansion_tank"), s("irrigation_controller"),
        ),
    ),
    ELECTRICAL(
        "ElectricalServices",
        listOf(
            s("breaker_panel"), s("sub_panel"), s("generator"),
            s("transfer_switch"), s("ev_charger"),
            s("solar_inverter"), s("surge_protector"),
        ),
    ),
    SHUTOFFS(
        "PowerOff",
        listOf(
            s("water_main"), s("gas_main"), s("electrical_main"),
            s("hvac_disconnect"), s("fixture_shutoff"),
            s("sprinkler"), s("sump"), s("cleanout"),
            s("propane_tank"),
        ),
    ),
    SAFETY(
        "HealthAndSafety",
        listOf(
            s("smoke_detector", reminder = "replace_smoke_detector_battery" to 12, battery = true),
            s("co_detector", reminder = "replace_co_detector_battery" to 12, battery = true),
            s("fire_extinguisher"), s("security_panel", battery = true),
            s("camera"), s("doorbell"), s("radon_fan"),
        ),
    ),
    FIXTURES(
        "Plumbing",
        listOf(
            s("faucet"), s("toilet"), s("shower_valve"),
            s("tub"), s("sink"), s("garbage_disposal"),
        ),
    ),
    DOORS_OPENERS(
        "Garage",
        listOf(
            s("garage_door_opener"), s("garage_door"),
            s("entry_door"), s("lock", battery = true), s("gate"),
        ),
    ),
    EXTERIOR(
        "Home",
        listOf(
            s("roof"), s("gutters", reminder = "clean_gutters" to 6),
            s("siding"), s("windows"), s("deck"), s("fence"),
            s("driveway"), s("chimney"),
        ),
    ),
    FINISHES(
        "FormatPaint",
        listOf(
            s("paint"), s("flooring"), s("tile"),
            s("countertop"), s("cabinets"), s("trim"),
        ),
    ),
    NETWORKING(
        "Router",
        listOf(
            s("router"), s("modem"), s("mesh_node"),
            s("network_switch"), s("hub"), s("nvr"),
        ),
    ),
    OTHER("Category", listOf(s("other")));

    val subtypes: List<ItemSubtype> = subtypes.map { it.build(this) }

    companion object {
        fun subtype(id: String): ItemSubtype? = entries.firstNotNullOfOrNull { c -> c.subtypes.find { it.id == id } }
        fun fromId(name: String): ItemCategory? = entries.find { it.name == name }
        fun all(): List<ItemSubtype> = entries.flatMap { it.subtypes }
    }
}

internal class SubtypeSpec(
    val id: String,
    val consumables: List<ConsumableDefault>,
    val reminders: List<ReminderDefault>,
) {
    fun build(category: ItemCategory) = ItemSubtype(id, category, null, consumables, reminders)
}

/** [filter] and [reminder] are text keys with their interval in months; a filter also seeds "replace_<key>". */
private fun s(
    id: String,
    filter: Pair<String, Int>? = null,
    reminder: Pair<String, Int>? = null,
    battery: Boolean = false,
): SubtypeSpec {
    val consumables = buildList {
        if (filter != null) add(ConsumableDefault(ConsumableKind.FILTER, filter.first))
        if (battery) add(ConsumableDefault(ConsumableKind.BATTERY, "battery"))
    }
    val reminders = buildList {
        filter?.let { add(ReminderDefault("replace_${it.first}", it.second)) }
        reminder?.let { add(ReminderDefault(it.first, it.second)) }
    }
    return SubtypeSpec(id, consumables, reminders)
}
