package com.kurtz.homerecord.core.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteTable
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

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
        SyncCursorEntity::class,
        SyncBaseEntity::class,
        MediaUploadEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = HomeRecordDatabase.V1ToV2::class),
        // v3 (CR2b2): r2Key on photo and note.
        AutoMigration(from = 2, to = 3),
        // v4 (CR2o1): pairRole on photo, so Before no longer sits in the phase column.
        AutoMigration(from = 3, to = 4, spec = HomeRecordDatabase.V3ToV4::class),
    ],
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
    abstract fun syncCursorDao(): SyncCursorDao
    abstract fun syncBaseDao(): SyncBaseDao
    abstract fun mediaUploadDao(): MediaUploadDao

    /**
     * v2 (CR2b): the single pull cursor becomes one per tenant. The old cursor is dropped, so the next sync pulls each
     * tenant from the start; pulled rows merge, so nothing is duplicated.
     */
    @DeleteTable(tableName = "sync_state")
    class V1ToV2 : AutoMigrationSpec

    /** v4 (cr:fl-before-phase): a photo whose phase was "Before" becomes a Before shot with no phase. */
    class V3ToV4 : AutoMigrationSpec {
        override fun onPostMigrate(connection: SQLiteConnection) {
            connection.execSQL("UPDATE photo SET pairRole = 'Before', stage = NULL WHERE stage = 'Before'")
        }
    }

    companion object {
        const val NAME = "homerecord.db"
    }
}
