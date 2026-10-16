package com.kurtz.homerecord.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        HomeEntity::class, RoomEntity::class, WallEntity::class, PanelEntity::class,
        BreakerEntity::class, BreakerRoomEntity::class, EndpointEntity::class,
        ShutoffEntity::class, EquipmentEntity::class, PhotoEntity::class,
        JobEntity::class, LogEntryEntity::class, DailyReportEntity::class, CompanyProfileEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class HomeRecordDatabase : RoomDatabase() {
    abstract fun homeDao(): HomeDao
    abstract fun roomDao(): RoomDao
    abstract fun wallDao(): WallDao
    abstract fun panelDao(): PanelDao
    abstract fun breakerDao(): BreakerDao
    abstract fun breakerRoomDao(): BreakerRoomDao
    abstract fun endpointDao(): EndpointDao
    abstract fun shutoffDao(): ShutoffDao
    abstract fun equipmentDao(): EquipmentDao
    abstract fun photoDao(): PhotoDao
    abstract fun jobDao(): JobDao
    abstract fun logEntryDao(): LogEntryDao
    abstract fun dailyReportDao(): DailyReportDao
    abstract fun companyProfileDao(): CompanyProfileDao

    companion object {
        const val FILE_NAME = "homerecord.db"

        fun open(context: Context): HomeRecordDatabase =
            Room.databaseBuilder(context.applicationContext, HomeRecordDatabase::class.java, FILE_NAME)
                // Pre-release: v1 -> v2 only added Field Log tables; no shipped data to preserve.
                .fallbackToDestructiveMigration(true).build()

        fun inMemory(context: Context): HomeRecordDatabase =
            Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
    }
}
