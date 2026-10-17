package com.kurtz.homerecord.core.reminders

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.logic.ClimateZone
import com.kurtz.homerecord.core.model.MaintenanceTask
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.Clock
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.MaintenanceRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReminderCheckerTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var tasks: MaintenanceRepository
    private val posted = mutableListOf<List<MaintenanceTask>>()
    private val clock = Clock { 1_792_238_400_000L } // 2026-10-17T12:00Z

    @Before
    fun setUp() {
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        homes = HomeRepository(db, Source.HOUSE_MANUAL, clock)
        tasks = MaintenanceRepository(db, Source.HOUSE_MANUAL, clock) { ZoneOffset.UTC }
    }

    @After
    fun tearDown() = db.close()

    private fun checker() = ReminderChecker(homes, tasks) { posted += it }

    @Test
    fun noHomeMeansNothingToDo() = runTest {
        assertEquals(0, checker().run())
        assertTrue(posted.isEmpty())
    }

    @Test
    fun nothingDueDoesNotNotify() = runTest {
        val home = homes.createHome("Maple")
        tasks.applyClimateZone(home.id, ClimateZone.COLD).forEach {
            db.maintenanceTaskDao().upsert(it.copy(nextDue = "2099-01-01").toEntity())
        }
        assertEquals(0, checker().run())
        assertTrue(posted.isEmpty())
    }

    @Test
    fun dueAndOverdueTasksAreNotifiedTogether() = runTest {
        val home = homes.createHome("Maple")
        val seeded = tasks.applyClimateZone(home.id, ClimateZone.COLD)
        db.maintenanceTaskDao().upsert(seeded[0].copy(nextDue = "2026-10-17").toEntity())
        db.maintenanceTaskDao().upsert(seeded[1].copy(nextDue = "2026-09-01").toEntity())
        seeded.drop(2).forEach { db.maintenanceTaskDao().upsert(it.copy(nextDue = "2099-01-01").toEntity()) }
        assertEquals(2, checker().run())
        assertEquals(1, posted.size)
        assertEquals(setOf(seeded[0].id, seeded[1].id), posted.single().map { it.id }.toSet())
    }

    @Test
    fun initialDelayTargetsNextMorning() {
        // 12:00Z with an 08:00 target: next run is tomorrow 08:00, 20h away.
        assertEquals(Duration.ofHours(20), ReminderSchedule.initialDelay(1_792_238_400_000L, ZoneOffset.UTC))
        // 06:00Z: later today, 2h away.
        assertEquals(Duration.ofHours(2), ReminderSchedule.initialDelay(1_792_238_400_000L - 6 * 3_600_000L, ZoneOffset.UTC))
        // exactly 08:00 rolls to tomorrow.
        assertEquals(Duration.ofHours(24), ReminderSchedule.initialDelay(1_792_238_400_000L - 4 * 3_600_000L, ZoneOffset.UTC))
    }
}
