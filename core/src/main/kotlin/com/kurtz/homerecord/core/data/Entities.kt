package com.kurtz.homerecord.core.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kurtz.homerecord.core.model.BreakerType
import com.kurtz.homerecord.core.model.Confidence
import com.kurtz.homerecord.core.model.EndpointKind
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.model.PanelLayout
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
