package com.kurtz.homerecord.core.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeDao {
    @Upsert suspend fun upsert(row: HomeEntity)
    @Query("SELECT * FROM home WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): HomeEntity?
    @Query("SELECT * FROM home WHERE deletedAt IS NULL ORDER BY createdAt") fun observeAll(): Flow<List<HomeEntity>>
    @Query("UPDATE home SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface FloorDao {
    @Upsert suspend fun upsert(row: FloorEntity)
    @Query("SELECT * FROM floor WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): FloorEntity?
    @Query("SELECT * FROM floor WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY sortOrder") fun observeFor(parentId: String): Flow<List<FloorEntity>>
    @Query("SELECT * FROM floor WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY sortOrder") suspend fun listFor(parentId: String): List<FloorEntity>
    @Query("UPDATE floor SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface RoomDao {
    @Upsert suspend fun upsert(row: RoomEntity)
    @Query("SELECT * FROM room WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): RoomEntity?
    @Query("SELECT * FROM room WHERE floorId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<RoomEntity>>
    @Query("SELECT * FROM room WHERE floorId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<RoomEntity>
    @Query("UPDATE room SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
    @Query("SELECT room.* FROM room JOIN floor ON room.floorId = floor.id WHERE floor.homeId = :homeId AND room.deletedAt IS NULL AND floor.deletedAt IS NULL ORDER BY room.createdAt") fun observeForHome(homeId: String): Flow<List<RoomEntity>>
    @Query("SELECT room.* FROM room JOIN floor ON room.floorId = floor.id WHERE floor.homeId = :homeId AND room.deletedAt IS NULL AND floor.deletedAt IS NULL ORDER BY room.createdAt") suspend fun listForHome(homeId: String): List<RoomEntity>
    @Query("SELECT floorId, COUNT(*) AS rooms FROM room WHERE deletedAt IS NULL GROUP BY floorId") fun observeCounts(): Flow<List<FloorRoomCount>>
}

data class FloorRoomCount(val floorId: String, val rooms: Int)

@Dao
interface WallDao {
    @Upsert suspend fun upsert(row: WallEntity)
    @Query("SELECT * FROM wall WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): WallEntity?
    @Query("SELECT * FROM wall WHERE roomId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<WallEntity>>
    @Query("SELECT * FROM wall WHERE roomId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<WallEntity>
    @Query("UPDATE wall SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface WallPhotoDao {
    @Upsert suspend fun upsert(row: WallPhotoEntity)
    @Query("SELECT * FROM wall_photo WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): WallPhotoEntity?
    @Query("SELECT * FROM wall_photo WHERE wallId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<WallPhotoEntity>>
    @Query("SELECT * FROM wall_photo WHERE wallId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<WallPhotoEntity>
    @Query("UPDATE wall_photo SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface OpeningDao {
    @Upsert suspend fun upsert(row: OpeningEntity)
    @Query("SELECT * FROM opening WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): OpeningEntity?
    @Query("SELECT * FROM opening WHERE wallId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<OpeningEntity>>
    @Query("SELECT * FROM opening WHERE wallId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<OpeningEntity>
    @Query("UPDATE opening SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface StairDao {
    @Upsert suspend fun upsert(row: StairEntity)
    @Query("SELECT * FROM stair WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): StairEntity?
    @Query("SELECT * FROM stair WHERE roomId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<StairEntity>>
    @Query("SELECT * FROM stair WHERE roomId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<StairEntity>
    @Query("UPDATE stair SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface OverlayDao {
    @Upsert suspend fun upsert(row: OverlayEntity)
    @Query("SELECT * FROM overlay WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): OverlayEntity?
    @Query("SELECT * FROM overlay WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<OverlayEntity>>
    @Query("SELECT * FROM overlay WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<OverlayEntity>
    @Query("UPDATE overlay SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface ItemDao {
    @Upsert suspend fun upsert(row: ItemEntity)
    @Query("SELECT * FROM item WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): ItemEntity?
    @Query("SELECT * FROM item WHERE id = :id AND deletedAt IS NULL") fun observeOne(id: String): Flow<ItemEntity?>
    @Query("SELECT * FROM item WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<ItemEntity>>
    @Query("SELECT * FROM item WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<ItemEntity>
    @Query("UPDATE item SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface WarrantyDao {
    @Upsert suspend fun upsert(row: WarrantyEntity)
    @Query("SELECT * FROM warranty WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): WarrantyEntity?
    @Query("SELECT * FROM warranty WHERE itemId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<WarrantyEntity>>
    @Query("SELECT * FROM warranty WHERE itemId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<WarrantyEntity>
    @Query("SELECT * FROM warranty WHERE deletedAt IS NULL ORDER BY createdAt") fun observeAll(): Flow<List<WarrantyEntity>>
    @Query("SELECT * FROM warranty WHERE confidence = 'PENDING' AND deletedAt IS NULL ORDER BY createdAt") suspend fun listPending(): List<WarrantyEntity>
    @Query("UPDATE warranty SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface ConsumableDao {
    @Upsert suspend fun upsert(row: ConsumableEntity)
    @Query("SELECT * FROM consumable WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): ConsumableEntity?
    @Query("SELECT * FROM consumable WHERE itemId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<ConsumableEntity>>
    @Query("SELECT consumable.* FROM consumable JOIN item ON consumable.itemId = item.id WHERE item.homeId = :homeId AND item.deletedAt IS NULL AND consumable.deletedAt IS NULL ORDER BY consumable.createdAt") fun observeForHome(homeId: String): Flow<List<ConsumableEntity>>
    @Query("SELECT * FROM consumable WHERE itemId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<ConsumableEntity>
    @Query("UPDATE consumable SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface ServiceRecordDao {
    @Upsert suspend fun upsert(row: ServiceRecordEntity)
    @Query("SELECT * FROM service_record WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): ServiceRecordEntity?
    @Query("SELECT * FROM service_record WHERE itemId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<ServiceRecordEntity>>
    @Query("SELECT * FROM service_record WHERE deletedAt IS NULL ORDER BY createdAt") fun observeAll(): Flow<List<ServiceRecordEntity>>
    @Query("SELECT * FROM service_record WHERE contactId = :contactId AND deletedAt IS NULL") suspend fun listForContact(contactId: String): List<ServiceRecordEntity>
    @Query("SELECT * FROM service_record WHERE itemId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<ServiceRecordEntity>
    @Query("UPDATE service_record SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface DocumentDao {
    @Upsert suspend fun upsert(row: DocumentEntity)
    @Query("SELECT * FROM document WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): DocumentEntity?
    @Query("SELECT * FROM document WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<DocumentEntity>>
    @Query("SELECT * FROM document WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<DocumentEntity>
    @Query("UPDATE document SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface PhotoDao {
    @Upsert suspend fun upsert(row: PhotoEntity)
    @Query("SELECT * FROM photo WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): PhotoEntity?
    @Query("SELECT * FROM photo WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<PhotoEntity>>
    @Query("SELECT * FROM photo WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<PhotoEntity>
    @Query("SELECT * FROM photo WHERE itemId = :itemId AND deletedAt IS NULL ORDER BY createdAt") fun observeForItem(itemId: String): Flow<List<PhotoEntity>>
    @Query("SELECT * FROM photo WHERE itemId = :itemId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listForItem(itemId: String): List<PhotoEntity>
    @Query("SELECT * FROM photo WHERE jobId = :jobId AND deletedAt IS NULL ORDER BY takenAt DESC") fun observeForJob(jobId: String): Flow<List<PhotoEntity>>
    @Query("SELECT COUNT(*) FROM photo WHERE jobId = :jobId AND deletedAt IS NULL") fun observeCountForJob(jobId: String): Flow<Int>
    /** Sets a late GPS fix in one statement, so a tag or Before edited meanwhile is not overwritten; 0 when already set or deleted. */
    @Query("UPDATE photo SET lat = :lat, lng = :lng, updatedAt = :at WHERE id = :id AND lat IS NULL AND deletedAt IS NULL")
    suspend fun setFixIfMissing(id: String, lat: Double, lng: Double, at: Long): Int
    @Query("UPDATE photo SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface ContactDao {
    @Upsert suspend fun upsert(row: ContactEntity)
    @Query("SELECT * FROM contact WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): ContactEntity?
    @Query("SELECT * FROM contact WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<ContactEntity>>
    @Query("SELECT * FROM contact WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<ContactEntity>
    @Query("UPDATE contact SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface MaintenanceTaskDao {
    @Upsert suspend fun upsert(row: MaintenanceTaskEntity)
    @Query("SELECT * FROM maintenance_task WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): MaintenanceTaskEntity?
    @Query("SELECT * FROM maintenance_task WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<MaintenanceTaskEntity>>
    @Query("SELECT * FROM maintenance_task WHERE homeId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<MaintenanceTaskEntity>
    @Query("UPDATE maintenance_task SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface PanelDao {
    @Upsert suspend fun upsert(row: PanelEntity)
    @Query("SELECT * FROM panel WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): PanelEntity?
    @Query("SELECT * FROM panel WHERE itemId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<PanelEntity>>
    @Query("SELECT * FROM panel WHERE itemId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<PanelEntity>
    @Query("UPDATE panel SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface BreakerDao {
    @Upsert suspend fun upsert(row: BreakerEntity)
    @Query("SELECT * FROM breaker WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): BreakerEntity?
    @Query("SELECT * FROM breaker WHERE panelId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<BreakerEntity>>
    @Query("SELECT * FROM breaker WHERE panelId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<BreakerEntity>
    @Query("UPDATE breaker SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface BreakerLinkDao {
    @Upsert suspend fun upsert(row: BreakerLinkEntity)
    @Query("SELECT * FROM breaker_link WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): BreakerLinkEntity?
    @Query("SELECT * FROM breaker_link WHERE breakerId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<BreakerLinkEntity>>
    @Query("SELECT * FROM breaker_link WHERE breakerId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<BreakerLinkEntity>
    @Query("UPDATE breaker_link SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface CompanyDao {
    @Upsert suspend fun upsert(row: CompanyEntity)
    @Query("SELECT * FROM company WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): CompanyEntity?
    @Query("SELECT * FROM company WHERE deletedAt IS NULL ORDER BY createdAt") fun observeAll(): Flow<List<CompanyEntity>>
    @Query("UPDATE company SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface MemberDao {
    @Upsert suspend fun upsert(row: MemberEntity)
    @Query("SELECT * FROM member WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): MemberEntity?
    @Query("SELECT * FROM member WHERE companyId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<MemberEntity>>
    @Query("SELECT * FROM member WHERE companyId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<MemberEntity>
    @Query("UPDATE member SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface ClientDao {
    @Upsert suspend fun upsert(row: ClientEntity)
    @Query("SELECT * FROM client WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): ClientEntity?
    @Query("SELECT * FROM client WHERE companyId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<ClientEntity>>
    @Query("SELECT * FROM client WHERE companyId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<ClientEntity>
    @Query("UPDATE client SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface JobDao {
    @Upsert suspend fun upsert(row: JobEntity)
    @Query("SELECT * FROM job WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): JobEntity?
    @Query("SELECT * FROM job WHERE companyId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<JobEntity>>
    @Query("SELECT * FROM job WHERE companyId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<JobEntity>
    @Query("UPDATE job SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface JobCrewDao {
    @Upsert suspend fun upsert(row: JobCrewEntity)
    @Query("SELECT * FROM job_crew WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): JobCrewEntity?
    @Query("SELECT * FROM job_crew WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<JobCrewEntity>>
    @Query("SELECT * FROM job_crew WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<JobCrewEntity>
    @Query("UPDATE job_crew SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface PunchDao {
    @Upsert suspend fun upsert(row: PunchEntity)
    @Query("SELECT * FROM punch WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): PunchEntity?
    @Query("SELECT * FROM punch WHERE companyId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<PunchEntity>>
    @Query("SELECT * FROM punch WHERE companyId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<PunchEntity>
    @Query("UPDATE punch SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface ShiftDao {
    @Upsert suspend fun upsert(row: ShiftEntity)
    @Query("SELECT * FROM shift WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): ShiftEntity?
    @Query("SELECT * FROM shift WHERE memberId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<ShiftEntity>>
    @Query("SELECT * FROM shift WHERE memberId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<ShiftEntity>
    @Query("UPDATE shift SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface TimesheetApprovalDao {
    @Upsert suspend fun upsert(row: TimesheetApprovalEntity)
    @Query("SELECT * FROM timesheet_approval WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): TimesheetApprovalEntity?
    @Query("SELECT * FROM timesheet_approval WHERE memberId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<TimesheetApprovalEntity>>
    @Query("SELECT * FROM timesheet_approval WHERE memberId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<TimesheetApprovalEntity>
    @Query("SELECT * FROM timesheet_approval WHERE deletedAt IS NULL ORDER BY createdAt") fun observeAll(): Flow<List<TimesheetApprovalEntity>>
    @Query("UPDATE timesheet_approval SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface JobFileDao {
    @Upsert suspend fun upsert(row: JobFileEntity)
    @Query("SELECT * FROM job_file WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): JobFileEntity?
    @Query("SELECT * FROM job_file WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<JobFileEntity>>
    @Query("SELECT * FROM job_file WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<JobFileEntity>
    @Query("UPDATE job_file SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
    @Query("SELECT * FROM job_file WHERE deletedAt IS NULL AND path IS NOT NULL") suspend fun withLocalPath(): List<JobFileEntity>
}

@Dao
interface NoteDao {
    @Upsert suspend fun upsert(row: NoteEntity)
    @Query("SELECT * FROM note WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): NoteEntity?
    @Query("SELECT * FROM note WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<NoteEntity>>
    @Query("SELECT * FROM note WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<NoteEntity>
    @Query("UPDATE note SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface DailyLogDao {
    @Upsert suspend fun upsert(row: DailyLogEntity)
    @Query("SELECT * FROM daily_log WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): DailyLogEntity?
    @Query("SELECT * FROM daily_log WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<DailyLogEntity>>
    @Query("SELECT * FROM daily_log WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<DailyLogEntity>
    @Query("UPDATE daily_log SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface TaskDao {
    @Upsert suspend fun upsert(row: TaskEntity)
    @Query("SELECT * FROM task WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): TaskEntity?
    @Query("SELECT * FROM task WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<TaskEntity>>
    @Query("SELECT * FROM task WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<TaskEntity>
    @Query("UPDATE task SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface ChangeOrderDao {
    @Upsert suspend fun upsert(row: ChangeOrderEntity)
    @Query("SELECT * FROM change_order WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): ChangeOrderEntity?
    @Query("SELECT * FROM change_order WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<ChangeOrderEntity>>
    @Query("SELECT * FROM change_order WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<ChangeOrderEntity>
    @Query("UPDATE change_order SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface AssignmentDao {
    @Upsert suspend fun upsert(row: AssignmentEntity)
    @Query("SELECT * FROM assignment WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): AssignmentEntity?
    @Query("SELECT * FROM assignment WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<AssignmentEntity>>
    @Query("SELECT * FROM assignment WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<AssignmentEntity>
    @Query("SELECT a.* FROM assignment a JOIN job j ON a.jobId = j.id WHERE j.companyId = :companyId AND j.deletedAt IS NULL AND a.deletedAt IS NULL")
    fun observeForCompany(companyId: String): Flow<List<AssignmentEntity>>
    @Query("UPDATE assignment SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface MaterialDao {
    @Upsert suspend fun upsert(row: MaterialEntity)
    @Query("SELECT * FROM material WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): MaterialEntity?
    @Query("SELECT * FROM material WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<MaterialEntity>>
    @Query("SELECT * FROM material WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<MaterialEntity>
    @Query("UPDATE material SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}

@Dao
interface ShareLinkDao {
    @Upsert suspend fun upsert(row: ShareLinkEntity)
    @Query("SELECT * FROM share_link WHERE id = :id AND deletedAt IS NULL") suspend fun get(id: String): ShareLinkEntity?
    @Query("SELECT * FROM share_link WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") fun observeFor(parentId: String): Flow<List<ShareLinkEntity>>
    @Query("SELECT * FROM share_link WHERE jobId = :parentId AND deletedAt IS NULL ORDER BY createdAt") suspend fun listFor(parentId: String): List<ShareLinkEntity>
    @Query("UPDATE share_link SET deletedAt = :at, updatedAt = :at WHERE id = :id") suspend fun softDelete(id: String, at: Long)
}
@Dao
interface OutboxDao {
    @androidx.room.Insert suspend fun add(row: OutboxEntity): Long
    @Query("SELECT * FROM outbox WHERE sent = 0 ORDER BY seq") suspend fun pending(): List<OutboxEntity>
    @Query("SELECT * FROM outbox WHERE sent = 0 AND tenantKind = :kind AND tenantId = :tenantId ORDER BY seq LIMIT :limit")
    suspend fun pendingFor(kind: String, tenantId: String, limit: Int): List<OutboxEntity>
    @Query("SELECT COUNT(*) FROM outbox WHERE sent = 0") fun observePendingCount(): kotlinx.coroutines.flow.Flow<Int>
    @Query("SELECT payload FROM outbox WHERE sent = 0 AND entity = :entity AND entityId = :entityId ORDER BY seq") suspend fun pendingPayloadsFor(entity: String, entityId: String): List<String>
    @Query("UPDATE outbox SET sent = 1 WHERE seq IN (:seqs)") suspend fun markSent(seqs: List<Long>)
    @Query("UPDATE outbox SET sent = 1, serverSeq = :serverSeq WHERE seq = :seq") suspend fun markSent(seq: Long, serverSeq: Long)
    @Query("SELECT COUNT(*) FROM outbox WHERE sent = 1 AND serverSeq = :refused") fun observeRefusedCount(refused: Long): kotlinx.coroutines.flow.Flow<Int>
    /** Drops rows the server accepted and this device has pulled back (serverSeq ≤ the tenant's cursor); refused rows (serverSeq < 0) stay for the log. */
    @Query("DELETE FROM outbox WHERE sent = 1 AND tenantKind = :kind AND tenantId = :tenantId AND serverSeq >= 0 AND serverSeq <= :upTo")
    suspend fun pruneConfirmed(kind: String, tenantId: String, upTo: Long): Int
}

@Dao
interface SyncCursorDao {
    @Upsert suspend fun set(cursor: SyncCursorEntity)
    @Query("SELECT lastServerSeq FROM sync_cursor WHERE tenantKind = :kind AND tenantId = :tenantId") suspend fun get(kind: String, tenantId: String): Long?
    @Query("DELETE FROM sync_cursor WHERE tenantKind = :kind AND tenantId = :tenantId") suspend fun delete(kind: String, tenantId: String)
}

@Dao
interface SyncBaseDao {
    @Upsert suspend fun set(row: SyncBaseEntity)
    @Query("SELECT rowJson FROM sync_base WHERE `key` = :key") suspend fun get(key: String): String?
    @Query("DELETE FROM sync_base WHERE tenant = :tenant") suspend fun deleteForTenant(tenant: String)
}

@Dao
interface MediaUploadDao {
    @androidx.room.Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE) suspend fun add(row: MediaUploadEntity): Long
    @Query("SELECT * FROM media_upload WHERE id = :id") suspend fun get(id: String): MediaUploadEntity?
    @Query("SELECT * FROM media_upload WHERE entity = :entity") fun observeAllFor(entity: String): Flow<List<MediaUploadEntity>>
    @Query("SELECT * FROM media_upload WHERE entity = :entity AND entityId = :entityId ORDER BY createdAt DESC LIMIT 1")
    fun observeFor(entity: String, entityId: String): Flow<MediaUploadEntity?>
    @Query("SELECT * FROM media_upload WHERE entity = :entity AND entityId = :entityId AND filename = :filename")
    suspend fun find(entity: String, entityId: String, filename: String): MediaUploadEntity?
    @Query("SELECT * FROM media_upload WHERE state IN ('queued', 'uploading') AND nextAttemptAt <= :now ORDER BY createdAt")
    suspend fun due(now: Long): List<MediaUploadEntity>
    @Query("SELECT COUNT(*) FROM media_upload WHERE state IN ('queued', 'uploading')") fun observePendingCount(): Flow<Int>
    @Query("SELECT MIN(nextAttemptAt) FROM media_upload WHERE state = 'queued'") suspend fun nextAttemptAt(): Long?
    @Query("UPDATE media_upload SET state = 'queued' WHERE state = 'uploading'") suspend fun requeueInterrupted()
    @Upsert suspend fun save(row: MediaUploadEntity)
    @Query("UPDATE media_upload SET state = 'queued', attempts = 0, nextAttemptAt = 0 WHERE state = 'failed'") suspend fun retryFailed()
}
