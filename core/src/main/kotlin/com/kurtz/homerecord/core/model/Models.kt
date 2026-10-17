package com.kurtz.homerecord.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Source(val wire: String) {
    @SerialName("house-manual") HOUSE_MANUAL("house-manual"),
    @SerialName("field-log") FIELD_LOG("field-log"),
    @SerialName("import") IMPORT("import"),
}

@Serializable
enum class PanelLayout { NA_NUMBERED, DIN }
@Serializable
enum class BreakerType { STANDARD, GFCI, AFCI, DUAL }
@Serializable
enum class Confidence { CONFIRMED, LIKELY, UNKNOWN }
@Serializable
enum class EndpointKind { OUTLET, LIGHT, SWITCH, APPLIANCE, HARDWIRED }
@Serializable
enum class ShutoffKind { WATER_MAIN, GAS_MAIN, ELECTRICAL_MAIN, HVAC_DISCONNECT, FIXTURE_VALVE, SUMP, CLEANOUT, OTHER }
@Serializable
enum class EquipmentCategory {
    WATER_HEATER, FURNACE, AC, HEAT_PUMP, RANGE, REFRIGERATOR, DISHWASHER,
    WASHER, DRYER, SUMP_PUMP, WATER_SOFTENER, OTHER,
}
@Serializable
enum class PhotoStage { BEFORE, ROUGH_IN, BEHIND_WALL, FINISHED, GENERAL }

/** Fields shared by every record (SPEC §6). */
interface Record {
    val id: String
    val createdAt: Long
    val updatedAt: Long
    val source: Source
}

@Serializable
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

@Serializable
data class Room(
    override val id: String,
    val homeId: String,
    val name: String,
    val floor: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

@Serializable
data class Wall(
    override val id: String,
    val roomId: String,
    val label: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

@Serializable
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

@Serializable
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

@Serializable
data class BreakerRoom(
    override val id: String,
    val breakerId: String,
    val roomId: String,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

@Serializable
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

@Serializable
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

@Serializable
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

@Serializable
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

@Serializable
enum class EntryKind { PHOTO, NOTE, VOICE }
@Serializable
enum class DelayReason { NONE, WEATHER, MATERIALS, CLIENT, INSPECTION, SUBCONTRACTOR, OTHER }

/** Field Log: one Job owns one Home (SPEC §6). */
@Serializable
data class Job(
    override val id: String,
    val homeId: String,
    val name: String,
    val address: String? = null,
    val clientName: String? = null,
    val clientContact: String? = null,
    val archived: Boolean = false,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

@Serializable
data class LogEntry(
    override val id: String,
    val jobId: String,
    /** ISO yyyy-MM-dd. */
    val day: String,
    val kind: EntryKind,
    val text: String? = null,
    /** Relative to the app files dir. */
    val mediaPath: String? = null,
    val durationMs: Long? = null,
    val roomId: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

@Serializable
data class DailyReport(
    override val id: String,
    val jobId: String,
    val day: String,
    val weather: String? = null,
    val crewCount: Int? = null,
    val hours: Double? = null,
    val workCompleted: String = "",
    val delays: String? = null,
    val delayReason: DelayReason? = null,
    val materials: String? = null,
    val nextSteps: String? = null,
    val pdfPath: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record

@Serializable
data class CompanyProfile(
    override val id: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val logoPath: String? = null,
    override val createdAt: Long,
    override val updatedAt: Long,
    override val source: Source,
) : Record
