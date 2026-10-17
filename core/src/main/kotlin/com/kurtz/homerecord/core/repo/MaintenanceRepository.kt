package com.kurtz.homerecord.core.repo

import androidx.room.withTransaction
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toDomain
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.logic.ClimateZone
import com.kurtz.homerecord.core.logic.DueDateMath
import com.kurtz.homerecord.core.logic.EquipmentRule
import com.kurtz.homerecord.core.logic.SeasonalTaskSeeder
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.MaintenanceRule
import com.kurtz.homerecord.core.model.MaintenanceTask
import com.kurtz.homerecord.core.model.Season
import com.kurtz.homerecord.core.model.Source
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.ZoneId

/** F15: maintenance tasks, sorted by nextDue. Equipment tasks are removed with their equipment by cascade (AC-85). */
class MaintenanceRepository(
    private val db: HomeRecordDatabase,
    private val source: Source,
    private val clock: Clock = Clock.System,
    private val ids: IdGenerator = IdGenerator.Random,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    private fun today(): LocalDate = java.time.Instant.ofEpochMilli(clock.now()).atZone(zone()).toLocalDate()

    fun observeTasks(homeId: String): Flow<List<MaintenanceTask>> =
        db.maintenanceTaskDao().observeForHome(homeId).map { list -> list.map { it.toDomain() } }

    suspend fun getTasks(homeId: String): List<MaintenanceTask> =
        db.maintenanceTaskDao().getForHome(homeId).map { it.toDomain() }

    suspend fun get(id: String): MaintenanceTask? = db.maintenanceTaskDao().get(id)?.toDomain()

    suspend fun getForEquipment(equipmentId: String): MaintenanceTask? =
        db.maintenanceTaskDao().getForEquipment(equipmentId)?.toDomain()

    /** AC-82: sets lastDone = today and advances nextDue by the task's rule. Null if the task is gone. */
    suspend fun markDone(id: String): MaintenanceTask? {
        val task = get(id) ?: return null
        val today = today()
        val next = when (task.rule) {
            MaintenanceRule.CALENDAR -> DueDateMath.afterDoneCalendar(today, task.intervalMonths ?: 12)
            MaintenanceRule.SEASONAL -> DueDateMath.afterDoneSeasonal(today, task.season ?: Season.SPRING)
        }
        val updated = task.copy(lastDone = today.toString(), nextDue = next.toString(), updatedAt = clock.now())
        db.maintenanceTaskDao().upsert(updated.toEntity())
        return updated
    }

    /** AC-82: pushes nextDue one week out. Null if the task is gone. */
    suspend fun snooze(id: String): MaintenanceTask? {
        val task = get(id) ?: return null
        val current = EquipmentRule.parseDate(task.nextDue) ?: today()
        val updated = task.copy(nextDue = DueDateMath.snooze(today(), current).toString(), updatedAt = clock.now())
        db.maintenanceTaskDao().upsert(updated.toEntity())
        return updated
    }

    /** AC-83: tasks whose nextDue is today or earlier, for the morning notification. */
    suspend fun getDue(homeId: String): List<MaintenanceTask> {
        val today = today().toString()
        return getTasks(homeId).filter { it.nextDue <= today }
    }

    /** AC-80: stores [zone] on the home and replaces its seasonal task list with that zone's. */
    suspend fun applyClimateZone(homeId: String, zone: ClimateZone): List<MaintenanceTask> = db.withTransaction {
        val home = db.homeDao().get(homeId) ?: return@withTransaction emptyList()
        val now = clock.now()
        db.homeDao().upsert(home.copy(climateZone = zone.name, updatedAt = now))
        db.maintenanceTaskDao().deleteSeasonalForHome(homeId)
        val tasks = SeasonalTaskSeeder.templates(zone, today()).map {
            MaintenanceTask(
                id = ids.next(), homeId = homeId, title = it.title, rule = MaintenanceRule.SEASONAL,
                season = it.season, nextDue = it.nextDue, createdAt = now, updatedAt = now, source = source,
            )
        }
        db.maintenanceTaskDao().upsertAll(tasks.map { it.toEntity() })
        tasks
    }

    /**
     * AC-81: creates or updates the interval reminder for [equipment] (which must already be saved).
     * [months] null removes the reminder. Out-of-range intervals are rejected with null and nothing changes.
     */
    suspend fun setEquipmentReminder(equipment: Equipment, months: Int?, title: String): MaintenanceTask? {
        val dao = db.maintenanceTaskDao()
        if (months == null) {
            dao.getForEquipment(equipment.id)?.let { dao.delete(it.id) }
            return null
        }
        if (!DueDateMath.isValidInterval(months)) return null
        val now = clock.now()
        val existing = dao.getForEquipment(equipment.id)?.toDomain()
        val task = if (existing == null) {
            MaintenanceTask(
                id = ids.next(), homeId = equipment.homeId, equipmentId = equipment.id, title = title,
                rule = MaintenanceRule.CALENDAR, intervalMonths = months,
                nextDue = DueDateMath.firstDue(equipment.installedOn, today(), months).toString(),
                createdAt = now, updatedAt = now, source = source,
            )
        } else if (existing.intervalMonths == months) {
            existing.copy(title = title, updatedAt = now)
        } else {
            existing.copy(
                title = title, intervalMonths = months, updatedAt = now,
                nextDue = DueDateMath.firstDue(equipment.installedOn, today(), months).toString(),
            )
        }
        dao.upsert(task.toEntity())
        return task
    }
}
