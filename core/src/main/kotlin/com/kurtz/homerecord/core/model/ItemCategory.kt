package com.kurtz.homerecord.core.model

enum class ConsumableKind { FILTER, BULB, BATTERY, CARTRIDGE, BELT, SALT, UV_LAMP, OTHER }

/** A consumable seeded when an item of this subtype is created. */
data class ConsumableDefault(val kind: ConsumableKind, val spec: String)

/** A recurring reminder seeded when an item of this subtype is created. */
data class ReminderDefault(val title: String, val intervalMonths: Int)

/** [icon] is a Material icon name resolved in :core-ui, so :core stays free of Compose. */
data class ItemSubtype(
    val id: String,
    val label: String,
    val category: ItemCategory,
    val icon: String? = null,
    val consumables: List<ConsumableDefault> = emptyList(),
    val reminders: List<ReminderDefault> = emptyList(),
) {
    val iconName: String get() = icon ?: category.icon
}

enum class ItemCategory(val label: String, val icon: String, subtypes: List<SubtypeSpec>) {
    APPLIANCES(
        "Appliances", "Kitchen",
        listOf(
            s("refrigerator", "Refrigerator", filter = "Water filter" to 6),
            s("freezer", "Freezer"), s("range", "Range"), s("oven", "Oven"), s("cooktop", "Cooktop"),
            s("range_hood", "Range hood", filter = "Grease filter" to 3),
            s("dishwasher", "Dishwasher"), s("microwave", "Microwave"), s("disposal", "Disposal"),
            s("washer", "Washer"), s("dryer", "Dryer", reminder = "Clean dryer vent" to 12),
            s("wine_fridge", "Wine fridge"),
        ),
    ),
    HEATING_COOLING(
        "Heating and cooling", "Thermostat",
        listOf(
            s("furnace", "Furnace", filter = "Furnace filter" to 3, reminder = "HVAC service" to 12),
            s("boiler", "Boiler", reminder = "HVAC service" to 12),
            s("air_handler", "Air handler", filter = "Furnace filter" to 3),
            s("heat_pump", "Heat pump", reminder = "HVAC service" to 12),
            s("ac_condenser", "AC condenser", reminder = "HVAC service" to 12),
            s("mini_split", "Mini-split", filter = "Mini-split filter" to 3),
            s("thermostat", "Thermostat", battery = true),
            s("humidifier", "Humidifier", filter = "Humidifier pad" to 3),
            s("dehumidifier", "Dehumidifier"), s("erv_hrv", "ERV/HRV", filter = "ERV core filter" to 6),
            s("whole_house_fan", "Whole-house fan"), s("radiant", "Radiant system"),
        ),
    ),
    WATER(
        "Water", "WaterDrop",
        listOf(
            s("water_heater", "Water heater", reminder = "Flush water heater" to 12),
            s("tankless_heater", "Tankless heater", reminder = "Flush tankless heater" to 12),
            s("water_softener", "Water softener", reminder = "Add softener salt" to 2),
            s("water_filter", "Water filter/RO", filter = "Water filter" to 6),
            s("well_pump", "Well pump"), s("pressure_tank", "Pressure tank"),
            s("sump_pump", "Sump pump", reminder = "Test sump pump" to 3),
            s("ejector_pump", "Ejector pump"), s("sewage_pump", "Sewage pump"),
            s("backflow_preventer", "Backflow preventer"), s("prv", "PRV"),
            s("expansion_tank", "Expansion tank"), s("irrigation_controller", "Irrigation controller"),
        ),
    ),
    ELECTRICAL(
        "Electrical", "ElectricalServices",
        listOf(
            s("breaker_panel", "Breaker panel"), s("sub_panel", "Sub-panel"), s("generator", "Generator"),
            s("transfer_switch", "Transfer switch"), s("ev_charger", "EV charger"),
            s("solar_inverter", "Solar inverter"), s("surge_protector", "Surge protector"),
        ),
    ),
    SHUTOFFS(
        "Shutoffs", "PowerOff",
        listOf(
            s("water_main", "Water main"), s("gas_main", "Gas main"), s("electrical_main", "Electrical main"),
            s("hvac_disconnect", "HVAC disconnect"), s("fixture_shutoff", "Fixture shutoff"),
            s("sprinkler", "Sprinkler"), s("sump", "Sump"), s("cleanout", "Cleanout"),
            s("propane_tank", "Propane tank"),
        ),
    ),
    SAFETY(
        "Safety", "HealthAndSafety",
        listOf(
            s("smoke_detector", "Smoke detector", reminder = "Replace smoke detector battery" to 12, battery = true),
            s("co_detector", "CO detector", reminder = "Replace CO detector battery" to 12, battery = true),
            s("fire_extinguisher", "Fire extinguisher"), s("security_panel", "Security panel", battery = true),
            s("camera", "Camera"), s("doorbell", "Doorbell"), s("radon_fan", "Radon fan"),
        ),
    ),
    FIXTURES(
        "Fixtures", "Plumbing",
        listOf(
            s("faucet", "Faucet"), s("toilet", "Toilet"), s("shower_valve", "Shower valve"),
            s("tub", "Tub"), s("sink", "Sink"), s("garbage_disposal", "Garbage disposal"),
        ),
    ),
    DOORS_OPENERS(
        "Doors and openers", "Garage",
        listOf(
            s("garage_door_opener", "Garage door opener"), s("garage_door", "Garage door"),
            s("entry_door", "Entry door"), s("lock", "Lock", battery = true), s("gate", "Gate"),
        ),
    ),
    EXTERIOR(
        "Exterior", "Home",
        listOf(
            s("roof", "Roof"), s("gutters", "Gutters", reminder = "Clean gutters" to 6),
            s("siding", "Siding"), s("windows", "Windows"), s("deck", "Deck"), s("fence", "Fence"),
            s("driveway", "Driveway"), s("chimney", "Chimney"),
        ),
    ),
    FINISHES(
        "Finishes", "FormatPaint",
        listOf(
            s("paint", "Paint"), s("flooring", "Flooring"), s("tile", "Tile"),
            s("countertop", "Countertop"), s("cabinets", "Cabinets"), s("trim", "Trim"),
        ),
    ),
    NETWORKING(
        "Networking and smart home", "Router",
        listOf(
            s("router", "Router"), s("modem", "Modem"), s("mesh_node", "Mesh node"),
            s("network_switch", "Switch"), s("hub", "Hub"), s("nvr", "NVR"),
        ),
    ),
    OTHER("Other", "Category", listOf(s("other", "Other")));

    val subtypes: List<ItemSubtype> = subtypes.map { it.build(this) }

    companion object {
        fun subtype(id: String): ItemSubtype? = entries.firstNotNullOfOrNull { c -> c.subtypes.find { it.id == id } }
        fun fromId(name: String): ItemCategory? = entries.find { it.name == name }
        fun all(): List<ItemSubtype> = entries.flatMap { it.subtypes }
    }
}

internal class SubtypeSpec(
    val id: String,
    val label: String,
    val consumables: List<ConsumableDefault>,
    val reminders: List<ReminderDefault>,
) {
    fun build(category: ItemCategory) = ItemSubtype(id, label, category, null, consumables, reminders)
}

private fun s(
    id: String,
    label: String,
    filter: Pair<String, Int>? = null,
    reminder: Pair<String, Int>? = null,
    battery: Boolean = false,
): SubtypeSpec {
    val consumables = buildList {
        if (filter != null) add(ConsumableDefault(ConsumableKind.FILTER, filter.first))
        if (battery) add(ConsumableDefault(ConsumableKind.BATTERY, "Battery"))
    }
    val reminders = buildList {
        filter?.let { add(ReminderDefault("Replace ${it.first.lowercase()}", it.second)) }
        reminder?.let { add(ReminderDefault(it.first, it.second)) }
    }
    return SubtypeSpec(id, label, consumables, reminders)
}
