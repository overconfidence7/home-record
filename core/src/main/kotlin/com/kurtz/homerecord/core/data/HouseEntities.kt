package com.kurtz.homerecord.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Where a record came from. Stored as the enum name. */
object Sources {
    const val HOUSE_MANUAL = "house-manual"
    const val FIELD_LOG = "field-log"
    const val IMPORT = "import"
}

@Entity(tableName = "home")
data class HomeEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val houseType: String = "OTHER",
    val foundation: String = "BASEMENT",
    val attachedGarage: Boolean = false,
    val yearBuilt: Int? = null,
    val climateZone: String? = null,
    val units: String = "IMPERIAL",
    val squareFeet: Int? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "floor", indices = [Index("homeId")])
data class FloorEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val sortOrder: Int,
    val name: String,
    val ceilingHeightIn: Int = 96,
    val halfLevel: Boolean = false,
    val elevationIn: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "room", indices = [Index("floorId")])
data class RoomEntity(
    @PrimaryKey val id: String,
    val floorId: String,
    val name: String,
    val kind: String = "OTHER",
    /** JSON array of {x,y} points in inches. */
    val polygonJson: String = "[]",
    val color: Int? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "wall", indices = [Index("roomId")])
data class WallEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val edgeIndex: Int,
    val orientation: String,
    val lengthIn: Int,
    val heightIn: Int,
    val exterior: Boolean = false,
    val sharedWithWallId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "wall_photo", indices = [Index("wallId")])
data class WallPhotoEntity(
    @PrimaryKey val id: String,
    val wallId: String,
    val stage: String = "FINISHED",
    val originalPath: String,
    val texturePath: String? = null,
    /** JSON array of four {x,y} points in photo space (0..1). */
    val cornersJson: String = "[]",
    val takenAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "opening", indices = [Index("wallId")])
data class OpeningEntity(
    @PrimaryKey val id: String,
    val wallId: String,
    val kind: String,
    val offsetIn: Int,
    val widthIn: Int,
    val heightIn: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "stair", indices = [Index("roomId")])
data class StairEntity(
    @PrimaryKey val id: String,
    val roomId: String,
    val toFloorId: String? = null,
    val polygonJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "overlay", indices = [Index("homeId"), Index("roomId"), Index("wallId")])
data class OverlayEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val roomId: String? = null,
    val wallId: String? = null,
    val kind: String,
    val label: String = "",
    val note: String? = null,
    val depth: String = "BEHIND_DRYWALL",
    /** JSON array of {x,y}: wall space 0..1 when wallId is set, plan inches otherwise. */
    val pointsJson: String = "[]",
    val stage: String = "FINISHED",
    val photoPath: String? = null,
    val itemId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "item", indices = [Index("homeId"), Index("roomId")])
data class ItemEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val roomId: String? = null,
    val category: String,
    val subtype: String,
    val name: String,
    val make: String? = null,
    val model: String? = null,
    val serial: String? = null,
    val manufacturedOn: String? = null,
    val purchasedOn: String? = null,
    val inServiceOn: String? = null,
    val priceCents: Long? = null,
    val vendor: String? = null,
    val manualUrl: String? = null,
    val notes: String? = null,
    val planX: Int? = null,
    val planY: Int? = null,
    val wallId: String? = null,
    val poweredByBreakerId: String? = null,
    val shutoffItemId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "warranty", indices = [Index("itemId")])
data class WarrantyEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val partsMonths: Int? = null,
    val laborMonths: Int? = null,
    val specialTermsJson: String = "[]",
    val endsOn: String? = null,
    val registrationRequired: Boolean = false,
    val registered: Boolean = false,
    val registrationUrl: String? = null,
    val sourceUrl: String? = null,
    val lookedUpAt: Long? = null,
    val confidence: String = "MANUAL",
    val summary: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "consumable", indices = [Index("itemId")])
data class ConsumableEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val kind: String,
    val spec: String,
    val quantity: Int = 1,
    val reorderUrl: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "service_record", indices = [Index("itemId")])
data class ServiceRecordEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val date: String,
    val contactId: String? = null,
    val description: String,
    val costCents: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "document", indices = [Index("homeId")])
data class DocumentEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val roomId: String? = null,
    val itemId: String? = null,
    val folder: String = "",
    val name: String,
    val path: String,
    val mime: String,
    val size: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "photo", indices = [Index("homeId"), Index("itemId"), Index("jobId")])
data class PhotoEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val roomId: String? = null,
    val itemId: String? = null,
    val jobId: String? = null,
    val stage: String? = null,
    /** Where the bytes sit on this phone; "" when the photo came from another phone and is not downloaded. Local only. */
    val path: String,
    val thumbPath: String? = null,
    /** The R2 object key once uploaded, so every phone can fetch the photo (cr:sync-local-columns). */
    val r2Key: String? = null,
    val takenAt: Long,
    val lat: Double? = null,
    val lng: Double? = null,
    val caption: String? = null,
    val tagsJson: String = "[]",
    val beforeOfPhotoId: String? = null,
    val annotationsJson: String = "[]",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "contact", indices = [Index("homeId")])
data class ContactEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val name: String,
    val company: String? = null,
    val trade: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "maintenance_task", indices = [Index("homeId"), Index("itemId")])
data class MaintenanceTaskEntity(
    @PrimaryKey val id: String,
    val homeId: String,
    val itemId: String? = null,
    val title: String,
    val intervalMonths: Int? = null,
    val dueOn: String,
    val lastDoneOn: String? = null,
    val snoozedUntil: String? = null,
    val seasonal: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "panel", indices = [Index("itemId")])
data class PanelEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val slotCount: Int = 40,
    val layout: String = "NORTH_AMERICAN",
    val mainAmps: Int? = null,
    val brand: String? = null,
    val photoPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "breaker", indices = [Index("panelId")])
data class BreakerEntity(
    @PrimaryKey val id: String,
    val panelId: String,
    /** JSON array of 1-based slot numbers occupied. */
    val slotsJson: String = "[]",
    val poles: Int = 1,
    val amps: Int? = null,
    val type: String = "STANDARD",
    val label: String = "",
    val confidence: String = "UNKNOWN",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)

@Entity(tableName = "breaker_link", indices = [Index("breakerId")])
data class BreakerLinkEntity(
    @PrimaryKey val id: String,
    val breakerId: String,
    val roomId: String? = null,
    val itemId: String? = null,
    val endpointLabel: String? = null,
    val confidence: String = "UNKNOWN",
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val source: String = Sources.HOUSE_MANUAL,
)
