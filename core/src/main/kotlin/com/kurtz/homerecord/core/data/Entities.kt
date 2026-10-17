package com.kurtz.homerecord.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.core.model.EntryKind
import com.kurtz.homerecord.core.model.Confidence
import com.kurtz.homerecord.core.model.EndpointKind
import com.kurtz.homerecord.core.model.ConsumableKind
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.model.MaintenanceRule
import com.kurtz.homerecord.core.model.PanelKind
import com.kurtz.homerecord.core.model.Season
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.RoomKind
import com.kurtz.homerecord.core.model.TandemPolicy
import com.kurtz.homerecord.core.model.PhotoStage
import com.kurtz.homerecord.core.model.ShutoffKind
import com.kurtz.homerecord.core.model.Source

private const val CASCADE = ForeignKey.CASCADE
private const val SET_NULL = ForeignKey.SET_NULL

@Entity(tableName = "home")
data class HomeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String?,
    val yearBuilt: Int?,
    val climateZone: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
    val squareFeet: Int? = null,
)

@Entity(
    tableName = "room",
    foreignKeys = [ForeignKey(HomeEntity::class, ["id"], ["homeId"], onDelete = CASCADE)],
    indices = [Index("homeId")],
)
data class RoomEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val name: String,
    val floor: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
    val kind: RoomKind = RoomKind.OTHER,
)

@Entity(
    tableName = "wall",
    foreignKeys = [ForeignKey(RoomEntity::class, ["id"], ["roomId"], onDelete = CASCADE)],
    indices = [Index("roomId")],
)
data class WallEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val label: String,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
)

@Entity(
    tableName = "panel",
    foreignKeys = [
        ForeignKey(HomeEntity::class, ["id"], ["homeId"], onDelete = CASCADE),
        ForeignKey(RoomEntity::class, ["id"], ["locationRoomId"], onDelete = SET_NULL),
    ],
    indices = [Index("homeId"), Index("locationRoomId")],
)
data class PanelEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val name: String,
    val locationRoomId: String?,
    val photoPath: String?,
    val slotCount: Int,
    val layout: PanelLayout,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
    val kind: PanelKind = PanelKind.MAIN,
    val ratingAmps: Int? = null,
    val brand: String? = null,
    val model: String? = null,
    val maxCircuits: Int? = null,
    val tandemPolicy: TandemPolicy = TandemPolicy.UNKNOWN,
    val hasMainBreaker: Boolean = true,
    val yearInstalled: Int? = null,
    val feedBreakerId: String? = null,
)

@Entity(
    tableName = "breaker",
    foreignKeys = [ForeignKey(PanelEntity::class, ["id"], ["panelId"], onDelete = CASCADE)],
    indices = [Index("panelId")],
)
data class BreakerEntity(
    @PrimaryKey val id: String,
    val panelId: String,
    /** Stored as a comma-separated list (see [Converters]). */
    val slots: List<Int>,
    val poles: Int,
    val amps: Int?,
    val type: BreakerType,
    val label: String,
    val confidence: Confidence,
    val tandemIndex: Int?,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
    val isMain: Boolean = false,
)

@Entity(
    tableName = "breaker_room",
    foreignKeys = [
        ForeignKey(BreakerEntity::class, ["id"], ["breakerId"], onDelete = CASCADE),
        ForeignKey(RoomEntity::class, ["id"], ["roomId"], onDelete = CASCADE),
    ],
    indices = [Index("breakerId", "roomId", unique = true), Index("roomId")],
)
data class BreakerRoomEntity(
    @PrimaryKey val id: String,
    val breakerId: String,
    val roomId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
)

@Entity(
    tableName = "endpoint",
    foreignKeys = [
        // Deleting a breaker detaches its endpoints (AC-12), it never deletes them.
        ForeignKey(BreakerEntity::class, ["id"], ["breakerId"], onDelete = SET_NULL),
        ForeignKey(RoomEntity::class, ["id"], ["roomId"], onDelete = CASCADE),
    ],
    indices = [Index("breakerId"), Index("roomId")],
)
data class EndpointEntity(
    @PrimaryKey val id: String,
    val breakerId: String?,
    val roomId: String,
    val kind: EndpointKind,
    val label: String,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
)

@Entity(
    tableName = "shutoff",
    foreignKeys = [
        ForeignKey(HomeEntity::class, ["id"], ["homeId"], onDelete = CASCADE),
        ForeignKey(RoomEntity::class, ["id"], ["roomId"], onDelete = SET_NULL),
    ],
    indices = [Index("homeId"), Index("roomId")],
)
data class ShutoffEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val kind: ShutoffKind,
    val roomId: String?,
    val photoPath: String?,
    val instructions: String,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
)

@Entity(
    tableName = "equipment",
    foreignKeys = [
        ForeignKey(HomeEntity::class, ["id"], ["homeId"], onDelete = CASCADE),
        ForeignKey(RoomEntity::class, ["id"], ["roomId"], onDelete = SET_NULL),
    ],
    indices = [Index("homeId"), Index("roomId")],
)
data class EquipmentEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val roomId: String?,
    val category: EquipmentCategory,
    val make: String?,
    val model: String?,
    val serial: String?,
    val manufacturedOn: String?,
    val installedOn: String?,
    val warrantyEnds: String?,
    val labelPhotoPath: String?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
)

@Entity(
    tableName = "photo",
    foreignKeys = [
        ForeignKey(HomeEntity::class, ["id"], ["homeId"], onDelete = CASCADE),
        ForeignKey(RoomEntity::class, ["id"], ["roomId"], onDelete = SET_NULL),
        ForeignKey(WallEntity::class, ["id"], ["wallId"], onDelete = SET_NULL),
    ],
    indices = [Index("homeId"), Index("roomId"), Index("wallId")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val roomId: String?,
    val wallId: String?,
    val stage: PhotoStage,
    val takenAt: Long,
    val caption: String?,
    val path: String,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
)

@Entity(
    tableName = "job",
    foreignKeys = [ForeignKey(HomeEntity::class, ["id"], ["homeId"], onDelete = CASCADE)],
    indices = [Index("homeId")],
)
data class JobEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val name: String,
    val address: String?,
    val clientName: String?,
    val clientContact: String?,
    val archived: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
)

@Entity(
    tableName = "log_entry",
    foreignKeys = [
        ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = CASCADE),
        ForeignKey(RoomEntity::class, ["id"], ["roomId"], onDelete = SET_NULL),
    ],
    indices = [Index("jobId"), Index("roomId")],
)
data class LogEntryEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val day: String,
    val kind: EntryKind,
    val text: String?,
    val mediaPath: String?,
    val durationMs: Long?,
    val roomId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
)

@Entity(
    tableName = "daily_report",
    foreignKeys = [ForeignKey(JobEntity::class, ["id"], ["jobId"], onDelete = CASCADE)],
    indices = [Index(value = ["jobId", "day"], unique = true)],
)
data class DailyReportEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val day: String,
    val weather: String?,
    val crewCount: Int?,
    val hours: Double?,
    val workCompleted: String,
    val delays: String?,
    val delayReason: DelayReason?,
    val materials: String?,
    val nextSteps: String?,
    val pdfPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
)

@Entity(tableName = "company_profile")
data class CompanyProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val logoPath: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
)

@Entity(
    tableName = "consumable",
    foreignKeys = [ForeignKey(EquipmentEntity::class, ["id"], ["equipmentId"], onDelete = CASCADE)],
    indices = [Index("equipmentId")],
)
data class ConsumableEntity(
    @PrimaryKey val id: String,
    val equipmentId: String,
    val kind: ConsumableKind,
    val spec: String,
    val reorderUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
)

@Entity(
    tableName = "load_profile",
    foreignKeys = [ForeignKey(HomeEntity::class, ["id"], ["homeId"], onDelete = CASCADE)],
    indices = [Index("homeId", unique = true)],
)
data class LoadProfileEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    /** "RANGE:12000,DRYER:5000" (enabled appliances only). */
    val appliances: String,
    val hvac: String,
    val hvacVa: Int,
    val stripVa: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
)

@Entity(
    tableName = "maintenance_task",
    foreignKeys = [
        ForeignKey(HomeEntity::class, ["id"], ["homeId"], onDelete = CASCADE),
        ForeignKey(EquipmentEntity::class, ["id"], ["equipmentId"], onDelete = CASCADE),
    ],
    indices = [Index("homeId"), Index("equipmentId")],
)
data class MaintenanceTaskEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val equipmentId: String?,
    val title: String,
    val rule: MaintenanceRule,
    val intervalMonths: Int?,
    val season: Season?,
    val lastDone: String?,
    val nextDue: String,
    val createdAt: Long,
    val updatedAt: Long,
    val source: Source,
)
