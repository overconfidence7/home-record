package com.kurtz.homerecord.core.model

enum class Source(val wire: String) {
    HOUSE_MANUAL("house-manual"),
    FIELD_LOG("field-log"),
    IMPORT("import"),
}

enum class PanelLayout { NA_NUMBERED, DIN }
enum class BreakerType { STANDARD, GFCI, AFCI, DUAL }
enum class Confidence { CONFIRMED, LIKELY, UNKNOWN }
enum class EndpointKind { OUTLET, LIGHT, SWITCH, APPLIANCE, HARDWIRED }
enum class ShutoffKind { WATER_MAIN, GAS_MAIN, ELECTRICAL_MAIN, HVAC_DISCONNECT, FIXTURE_VALVE, SUMP, CLEANOUT, OTHER }
enum class EquipmentCategory {
    WATER_HEATER, FURNACE, AC, HEAT_PUMP, RANGE, REFRIGERATOR, DISHWASHER,
    WASHER, DRYER, SUMP_PUMP, WATER_SOFTENER, OTHER,
}
enum class PhotoStage { BEFORE, ROUGH_IN, BEHIND_WALL, FINISHED, GENERAL }

/** Fields shared by every record (SPEC §6). */
interface Record {
    val id: String
    val createdAt: Long
    val updatedAt: Long
    val source: Source
}

data class Home(
    override val id: String,
    val name: String,
    val address: String? = null,
    val yearBuilt: Int? = null,
    val climateZone: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

data class Room(
    override val id: String,
    val homeId: String,
    val name: String,
    val floor: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

data class Wall(
    override val id: String,
    val roomId: String,
    val label: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

data class Panel(
    override val id: String,
    val homeId: String,
    val name: String,
    val locationRoomId: String? = null,
    val photoPath: String? = null,
    val slotCount: Int,
    val layout: PanelLayout,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

data class Breaker(
    override val id: String,
    val panelId: String,
    val slots: List<Int>,
    val poles: Int = 1,
    val amps: Int? = null,
    val type: BreakerType = BreakerType.STANDARD,
    val label: String,
    val confidence: Confidence = Confidence.UNKNOWN,
    /** 0 or 1 when the breaker shares a slot as a tandem; null otherwise. */
    val tandemIndex: Int? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

data class BreakerRoom(
    override val id: String,
    val breakerId: String,
    val roomId: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

data class Endpoint(
    override val id: String,
    /** null = not yet mapped to a breaker. */
    val breakerId: String? = null,
    val roomId: String,
    val kind: EndpointKind,
    val label: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

data class Shutoff(
    override val id: String,
    val homeId: String,
    val kind: ShutoffKind,
    val roomId: String? = null,
    val photoPath: String? = null,
    val instructions: String = "",
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

data class Equipment(
    override val id: String,
    val homeId: String,
    val roomId: String? = null,
    val category: EquipmentCategory,
    val make: String? = null,
    val model: String? = null,
    val serial: String? = null,
    /** ISO yyyy-MM-dd. */
    val manufacturedOn: String? = null,
    val installedOn: String? = null,
    val warrantyEnds: String? = null,
    val labelPhotoPath: String? = null,
    val notes: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

data class Photo(
    override val id: String,
    val homeId: String,
    val roomId: String? = null,
    val wallId: String? = null,
    val stage: PhotoStage = PhotoStage.GENERAL,
    val takenAt: Long,
    val caption: String? = null,
    /** Relative to the app files dir. */
    val path: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record
