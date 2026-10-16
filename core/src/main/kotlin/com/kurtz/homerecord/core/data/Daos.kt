package com.kurtz.homerecord.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HomeDao {
    @Upsert
    suspend fun upsert(entity: HomeEntity)

    @Delete
    suspend fun delete(entity: HomeEntity)

    @Query("SELECT * FROM home WHERE id = :id")
    suspend fun get(id: String): HomeEntity?

    @Query("SELECT * FROM home ORDER BY createdAt")
    suspend fun getAll(): List<HomeEntity>

    @Query("SELECT * FROM home ORDER BY createdAt LIMIT 1")
    fun observeFirst(): Flow<HomeEntity?>
}

@Dao
interface RoomDao {
    @Upsert
    suspend fun upsert(entity: RoomEntity)

    @Delete
    suspend fun delete(entity: RoomEntity)

    @Query("SELECT * FROM room WHERE id = :id")
    suspend fun get(id: String): RoomEntity?

    @Query("SELECT * FROM room ORDER BY createdAt")
    suspend fun getAll(): List<RoomEntity>

    @Query("SELECT * FROM room WHERE homeId = :homeId ORDER BY createdAt")
    fun observeForHome(homeId: String): Flow<List<RoomEntity>>
}

@Dao
interface WallDao {
    @Upsert
    suspend fun upsert(entity: WallEntity)

    @Delete
    suspend fun delete(entity: WallEntity)

    @Query("SELECT * FROM wall WHERE id = :id")
    suspend fun get(id: String): WallEntity?

    @Query("SELECT * FROM wall ORDER BY createdAt")
    suspend fun getAll(): List<WallEntity>

    @Query("SELECT * FROM wall WHERE roomId = :roomId ORDER BY createdAt")
    fun observeForRoom(roomId: String): Flow<List<WallEntity>>
}

@Dao
interface PanelDao {
    @Upsert
    suspend fun upsert(entity: PanelEntity)

    @Delete
    suspend fun delete(entity: PanelEntity)

    @Query("SELECT * FROM panel WHERE id = :id")
    suspend fun get(id: String): PanelEntity?

    @Query("SELECT * FROM panel ORDER BY createdAt")
    suspend fun getAll(): List<PanelEntity>

    @Query("SELECT * FROM panel WHERE homeId = :homeId ORDER BY createdAt")
    fun observeForHome(homeId: String): Flow<List<PanelEntity>>
}

@Dao
interface BreakerDao {
    @Upsert
    suspend fun upsert(entity: BreakerEntity)

    @Delete
    suspend fun delete(entity: BreakerEntity)

    @Query("SELECT * FROM breaker WHERE id = :id")
    suspend fun get(id: String): BreakerEntity?

    @Query("SELECT * FROM breaker ORDER BY createdAt")
    suspend fun getAll(): List<BreakerEntity>

    @Query("SELECT * FROM breaker WHERE panelId = :panelId ORDER BY createdAt")
    fun observeForPanel(panelId: String): Flow<List<BreakerEntity>>
}

@Dao
interface BreakerRoomDao {
    @Upsert
    suspend fun upsert(entity: BreakerRoomEntity)

    @Delete
    suspend fun delete(entity: BreakerRoomEntity)

    @Query("SELECT * FROM breaker_room WHERE id = :id")
    suspend fun get(id: String): BreakerRoomEntity?

    @Query("SELECT * FROM breaker_room ORDER BY createdAt")
    suspend fun getAll(): List<BreakerRoomEntity>

    @Query("SELECT * FROM breaker_room WHERE breakerId = :breakerId")
    fun observeForBreaker(breakerId: String): Flow<List<BreakerRoomEntity>>

    @Query("SELECT * FROM breaker_room WHERE roomId = :roomId")
    fun observeForRoom(roomId: String): Flow<List<BreakerRoomEntity>>

    @Query("SELECT * FROM breaker_room WHERE roomId IN (SELECT id FROM room WHERE homeId = :homeId)")
    fun observeForHome(homeId: String): Flow<List<BreakerRoomEntity>>

    @Query("DELETE FROM breaker_room WHERE breakerId = :breakerId")
    suspend fun deleteForBreaker(breakerId: String)
}

@Dao
interface EndpointDao {
    @Upsert
    suspend fun upsert(entity: EndpointEntity)

    @Delete
    suspend fun delete(entity: EndpointEntity)

    @Query("SELECT * FROM endpoint WHERE id = :id")
    suspend fun get(id: String): EndpointEntity?

    @Query("SELECT * FROM endpoint ORDER BY createdAt")
    suspend fun getAll(): List<EndpointEntity>

    @Query("SELECT * FROM endpoint WHERE breakerId = :breakerId ORDER BY createdAt")
    fun observeForBreaker(breakerId: String): Flow<List<EndpointEntity>>

    @Query("SELECT * FROM endpoint WHERE roomId = :roomId ORDER BY createdAt")
    fun observeForRoom(roomId: String): Flow<List<EndpointEntity>>

    @Query("SELECT * FROM endpoint WHERE roomId IN (SELECT id FROM room WHERE homeId = :homeId) ORDER BY createdAt")
    fun observeForHome(homeId: String): Flow<List<EndpointEntity>>
}

@Dao
interface ShutoffDao {
    @Upsert
    suspend fun upsert(entity: ShutoffEntity)

    @Delete
    suspend fun delete(entity: ShutoffEntity)

    @Query("SELECT * FROM shutoff WHERE id = :id")
    suspend fun get(id: String): ShutoffEntity?

    @Query("SELECT * FROM shutoff ORDER BY createdAt")
    suspend fun getAll(): List<ShutoffEntity>

    @Query("SELECT * FROM shutoff WHERE homeId = :homeId ORDER BY createdAt")
    fun observeForHome(homeId: String): Flow<List<ShutoffEntity>>
}

@Dao
interface EquipmentDao {
    @Upsert
    suspend fun upsert(entity: EquipmentEntity)

    @Delete
    suspend fun delete(entity: EquipmentEntity)

    @Query("SELECT * FROM equipment WHERE id = :id")
    suspend fun get(id: String): EquipmentEntity?

    @Query("SELECT * FROM equipment ORDER BY createdAt")
    suspend fun getAll(): List<EquipmentEntity>

    @Query("SELECT * FROM equipment WHERE homeId = :homeId ORDER BY createdAt")
    fun observeForHome(homeId: String): Flow<List<EquipmentEntity>>
}

@Dao
interface PhotoDao {
    @Upsert
    suspend fun upsert(entity: PhotoEntity)

    @Delete
    suspend fun delete(entity: PhotoEntity)

    @Query("SELECT * FROM photo WHERE id = :id")
    suspend fun get(id: String): PhotoEntity?

    @Query("SELECT * FROM photo ORDER BY createdAt")
    suspend fun getAll(): List<PhotoEntity>

    @Query("SELECT * FROM photo WHERE homeId = :homeId ORDER BY takenAt")
    fun observeForHome(homeId: String): Flow<List<PhotoEntity>>
}

@Dao
interface JobDao {
    @Upsert
    suspend fun upsert(entity: JobEntity)

    @Delete
    suspend fun delete(entity: JobEntity)

    @Query("SELECT * FROM job WHERE id = :id")
    suspend fun get(id: String): JobEntity?

    @Query("SELECT * FROM job WHERE id = :id")
    fun observe(id: String): Flow<JobEntity?>

    @Query("SELECT * FROM job ORDER BY createdAt")
    suspend fun getAll(): List<JobEntity>

    @Query("SELECT * FROM job ORDER BY createdAt")
    fun observeAll(): Flow<List<JobEntity>>
}

@Dao
interface LogEntryDao {
    @Upsert
    suspend fun upsert(entity: LogEntryEntity)

    @Delete
    suspend fun delete(entity: LogEntryEntity)

    @Query("SELECT * FROM log_entry WHERE id = :id")
    suspend fun get(id: String): LogEntryEntity?

    @Query("SELECT * FROM log_entry ORDER BY createdAt")
    suspend fun getAll(): List<LogEntryEntity>

    @Query("SELECT * FROM log_entry ORDER BY createdAt")
    fun observeAll(): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entry WHERE id = :id")
    fun observeById(id: String): Flow<LogEntryEntity?>

    @Query("SELECT * FROM log_entry WHERE jobId = :jobId ORDER BY createdAt")
    fun observeForJob(jobId: String): Flow<List<LogEntryEntity>>
}

@Dao
interface DailyReportDao {
    @Upsert
    suspend fun upsert(entity: DailyReportEntity)

    @Delete
    suspend fun delete(entity: DailyReportEntity)

    @Query("SELECT * FROM daily_report WHERE jobId = :jobId AND day = :day")
    suspend fun getForDay(jobId: String, day: String): DailyReportEntity?

    @Query("SELECT * FROM daily_report ORDER BY day")
    suspend fun getAll(): List<DailyReportEntity>
}

@Dao
interface CompanyProfileDao {
    @Upsert
    suspend fun upsert(entity: CompanyProfileEntity)

    @Delete
    suspend fun delete(entity: CompanyProfileEntity)

    @Query("SELECT * FROM company_profile ORDER BY createdAt LIMIT 1")
    suspend fun getFirst(): CompanyProfileEntity?

    @Query("SELECT * FROM company_profile ORDER BY createdAt LIMIT 1")
    fun observeFirst(): Flow<CompanyProfileEntity?>
}
