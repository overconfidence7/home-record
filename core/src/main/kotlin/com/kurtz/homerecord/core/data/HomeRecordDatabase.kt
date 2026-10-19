package com.kurtz.homerecord.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        HomeEntity::class,
        FloorEntity::class,
        RoomEntity::class,
        WallEntity::class,
        WallPhotoEntity::class,
        OpeningEntity::class,
        StairEntity::class,
        OverlayEntity::class,
        ItemEntity::class,
        WarrantyEntity::class,
        ConsumableEntity::class,
        ServiceRecordEntity::class,
        DocumentEntity::class,
        PhotoEntity::class,
        ContactEntity::class,
        MaintenanceTaskEntity::class,
        PanelEntity::class,
        BreakerEntity::class,
        BreakerLinkEntity::class,
        CompanyEntity::class,
        MemberEntity::class,
        ClientEntity::class,
        JobEntity::class,
        JobCrewEntity::class,
        PunchEntity::class,
        ShiftEntity::class,
        TimesheetApprovalEntity::class,
        JobFileEntity::class,
        NoteEntity::class,
        DailyLogEntity::class,
        TaskEntity::class,
        ChangeOrderEntity::class,
        AssignmentEntity::class,
        MaterialEntity::class,
        ShareLinkEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HomeRecordDatabase : RoomDatabase() {
    abstract fun homeDao(): HomeDao
    abstract fun floorDao(): FloorDao
    abstract fun roomDao(): RoomDao
    abstract fun wallDao(): WallDao
    abstract fun wallPhotoDao(): WallPhotoDao
    abstract fun openingDao(): OpeningDao
    abstract fun stairDao(): StairDao
    abstract fun overlayDao(): OverlayDao
    abstract fun itemDao(): ItemDao
    abstract fun warrantyDao(): WarrantyDao
    abstract fun consumableDao(): ConsumableDao
    abstract fun serviceRecordDao(): ServiceRecordDao
    abstract fun documentDao(): DocumentDao
    abstract fun photoDao(): PhotoDao
    abstract fun contactDao(): ContactDao
    abstract fun maintenanceTaskDao(): MaintenanceTaskDao
    abstract fun panelDao(): PanelDao
    abstract fun breakerDao(): BreakerDao
    abstract fun breakerLinkDao(): BreakerLinkDao
    abstract fun companyDao(): CompanyDao
    abstract fun memberDao(): MemberDao
    abstract fun clientDao(): ClientDao
    abstract fun jobDao(): JobDao
    abstract fun jobCrewDao(): JobCrewDao
    abstract fun punchDao(): PunchDao
    abstract fun shiftDao(): ShiftDao
    abstract fun timesheetApprovalDao(): TimesheetApprovalDao
    abstract fun jobFileDao(): JobFileDao
    abstract fun noteDao(): NoteDao
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun taskDao(): TaskDao
    abstract fun changeOrderDao(): ChangeOrderDao
    abstract fun assignmentDao(): AssignmentDao
    abstract fun materialDao(): MaterialDao
    abstract fun shareLinkDao(): ShareLinkDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        const val NAME = "homerecord.db"
    }
}
