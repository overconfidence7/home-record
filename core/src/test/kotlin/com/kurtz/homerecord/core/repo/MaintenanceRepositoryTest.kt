package com.kurtz.homerecord.core.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.logic.ClimateZone
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.MaintenanceRule
import com.kurtz.homerecord.core.model.Source
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MaintenanceRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var equipment: EquipmentRepository
    private lateinit var tasks: MaintenanceRepository
    private var seq = 0
    private val ids = IdGenerator { "id${++seq}" }
    // 2026-10-17T12:00:00Z
    private val clock = Clock { 1_792_238_400_000L }

    @Before
    fun setUp() {
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        val media = MediaFileStore(Files.createTempDirectory("maint").toFile())
        homes = HomeRepository(db, Source.HOUSE_MANUAL, clock, ids)
        equipment = EquipmentRepository(db, media, Source.HOUSE_MANUAL, clock, ids)
        tasks = MaintenanceRepository(db, Source.HOUSE_MANUAL, clock, ids) { ZoneOffset.UTC }
    }

    @After
    fun tearDown() = db.close()

    private suspend fun heater(homeId: String, installedOn: String? = null): Equipment =
        equipment.save(Equipment(id = "", homeId = homeId, category = EquipmentCategory.WATER_HEATER, installedOn = installedOn, createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL))

    @Test
    fun climateZoneSeedsSeasonalTasksAndSavesZone() = runTest {
        val home = homes.createHome("Maple")
        val seeded = tasks.applyClimateZone(home.id, ClimateZone.COLD)
        assertTrue(seeded.isNotEmpty())
        assertEquals("COLD", homes.getHome()?.climateZone)
        val stored = tasks.observeTasks(home.id).first()
        assertEquals(seeded.size, stored.size)
        assertTrue(stored.all { it.rule == MaintenanceRule.SEASONAL && it.season != null })
        assertEquals(stored.map { it.nextDue }.sorted(), stored.map { it.nextDue })
    }

    @Test
    fun changingZoneReplacesSeasonalTasksButKeepsEquipmentReminders() = runTest {
        val home = homes.createHome("Maple")
        val wh = heater(home.id)
        tasks.setEquipmentReminder(wh, 12, "Flush water heater")
        val cold = tasks.applyClimateZone(home.id, ClimateZone.COLD)
        val dry = tasks.applyClimateZone(home.id, ClimateZone.HOT_DRY)
        val all = tasks.getTasks(home.id)
        assertEquals(dry.size + 1, all.size)
        assertTrue(all.none { t -> cold.any { it.id == t.id } })
        assertNotNull(tasks.getForEquipment(wh.id))
    }

    @Test
    fun equipmentReminderUsesInstallDatePlusInterval() = runTest {
        val home = homes.createHome("Maple")
        val wh = heater(home.id, installedOn = "2026-09-01")
        val task = tasks.setEquipmentReminder(wh, 3, "Service water heater")
        assertEquals("2026-12-01", task?.nextDue)
        assertEquals(MaintenanceRule.CALENDAR, task?.rule)
        assertEquals(3, task?.intervalMonths)
        assertEquals(wh.id, task?.equipmentId)
    }

    @Test
    fun equipmentReminderWithoutInstallDateUsesToday() = runTest {
        val home = homes.createHome("Maple")
        val task = tasks.setEquipmentReminder(heater(home.id), 6, "Service")
        assertEquals("2027-04-17", task?.nextDue)
    }

    @Test
    fun changingIntervalUpdatesSameTaskAndNullRemovesIt() = runTest {
        val home = homes.createHome("Maple")
        val wh = heater(home.id)
        val first = tasks.setEquipmentReminder(wh, 6, "Service")
        val second = tasks.setEquipmentReminder(wh, 12, "Service")
        assertEquals(first?.id, second?.id)
        assertEquals(1, tasks.getTasks(home.id).size)
        assertEquals("2027-10-17", tasks.getForEquipment(wh.id)?.nextDue)
        assertNull(tasks.setEquipmentReminder(wh, null, "Service"))
        assertTrue(tasks.getTasks(home.id).isEmpty())
    }

    @Test
    fun outOfRangeIntervalIsRejected() = runTest {
        val home = homes.createHome("Maple")
        val wh = heater(home.id)
        assertNull(tasks.setEquipmentReminder(wh, 0, "x"))
        assertNull(tasks.setEquipmentReminder(wh, 25, "x"))
        assertTrue(tasks.getTasks(home.id).isEmpty())
    }

    @Test
    fun deletingEquipmentDeletesItsTasks() = runTest {
        val home = homes.createHome("Maple")
        val wh = heater(home.id)
        val other = heater(home.id)
        tasks.setEquipmentReminder(wh, 6, "A")
        tasks.setEquipmentReminder(other, 6, "B")
        equipment.delete(wh.id)
        val left = tasks.getTasks(home.id)
        assertEquals(listOf(other.id), left.map { it.equipmentId })
    }

    @Test
    fun doneOnCalendarTaskSetsLastDoneAndAdvancesByInterval() = runTest {
        val home = homes.createHome("Maple")
        val task = checkNotNull(tasks.setEquipmentReminder(heater(home.id), 6, "Flush"))
        val done = checkNotNull(tasks.markDone(task.id))
        assertEquals("2026-10-17", done.lastDone)
        assertEquals("2027-04-17", done.nextDue)
        assertEquals(done, tasks.get(task.id))
    }

    @Test
    fun doneOnSeasonalTaskAdvancesToNextSeasonStart() = runTest {
        val home = homes.createHome("Maple")
        val seeded = tasks.applyClimateZone(home.id, ClimateZone.COLD).first()
        val done = checkNotNull(tasks.markDone(seeded.id))
        assertEquals("2026-10-17", done.lastDone)
        assertTrue(done.nextDue > "2026-10-17")
    }

    @Test
    fun snoozePushesNextDueOneWeek() = runTest {
        val home = homes.createHome("Maple")
        val task = checkNotNull(tasks.setEquipmentReminder(heater(home.id), 1, "Filter"))
        val snoozed = checkNotNull(tasks.snooze(task.id))
        assertEquals(java.time.LocalDate.parse(task.nextDue).plusDays(7).toString(), snoozed.nextDue)
    }

    @Test
    fun doneAndSnoozeOnMissingTaskReturnNull() = runTest {
        assertNull(tasks.markDone("nope"))
        assertNull(tasks.snooze("nope"))
    }

    @Test
    fun getDueReturnsOnlyTasksDueTodayOrEarlier() = runTest {
        val home = homes.createHome("Maple")
        val a = checkNotNull(tasks.setEquipmentReminder(heater(home.id), 1, "Soon"))
        val past = a.copy(nextDue = "2026-10-01")
        db.maintenanceTaskDao().upsert(past.toEntity())
        tasks.setEquipmentReminder(heater(home.id), 12, "Later")
        assertEquals(listOf("Soon"), tasks.getDue(home.id).map { it.title })
    }
}
