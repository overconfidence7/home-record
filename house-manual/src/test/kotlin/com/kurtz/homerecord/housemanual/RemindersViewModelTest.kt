package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.MaintenanceTaskEntity
import com.kurtz.homerecord.core.data.WarrantyEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.reminders.RemindersEvent
import com.kurtz.homerecord.housemanual.reminders.RemindersUiState
import com.kurtz.homerecord.housemanual.reminders.RemindersViewModel
import com.kurtz.homerecord.housemodel.reminders.ClimateZone
import com.kurtz.homerecord.housemodel.reminders.MaintenanceRule
import com.kurtz.homerecord.housemodel.reminders.ReminderBucket
import com.kurtz.homerecord.housemodel.reminders.ReminderDraft
import com.kurtz.homerecord.housemodel.reminders.ReminderRepository
import com.kurtz.homerecord.housemodel.reminders.SnoozeLength
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-H12: the Reminders tab lists Due now / Upcoming / Done, Done rolls the next due date forward, a zone seeds tasks.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RemindersViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: ReminderRepository
    private val day = LocalDate.parse("2026-11-06")
    private val vms = mutableListOf<RemindersViewModel>()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = ReminderRepository(db, context::getString, now = { 1_000L }, today = { day })
        runBlocking {
            db.homeDao().upsert(HomeEntity(id = "h1", name = "Maple St", createdAt = 1, updatedAt = 1))
            db.itemDao().upsert(ItemEntity(id = "i1", homeId = "h1", category = "HEATING_COOLING", subtype = "furnace", name = "Basement furnace", createdAt = 1, updatedAt = 1))
        }
    }

    @After fun tearDown() {
        runBlocking { vms.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(saved: SavedStateHandle = SavedStateHandle()) = RemindersViewModel(repo, "h1", saved, today = { day }).also {
        vms += it
        it.state.launchIn(it.viewModelScope)
    }

    private fun RemindersViewModel.await(what: (RemindersUiState) -> Boolean) = runBlocking { withTimeout(5_000) { state.first(what) } }

    @AcceptanceCriterion("AC-H12")
    @Test fun doneRollsARecurringReminderForwardIntoUpcoming() {
        runBlocking {
            db.maintenanceTaskDao().upsert(
                MaintenanceTaskEntity(id = "t1", homeId = "h1", itemId = "i1", title = "Replace furnace filter", intervalMonths = 3, dueOn = "2026-10-28", createdAt = 1, updatedAt = 1),
            )
        }
        val vm = vm()
        val before = vm.await { it.due.isNotEmpty() }
        assertThat(before.due.single().itemName).isEqualTo("Basement furnace")
        vm.onEvent(RemindersEvent.Done("t1"))
        val after = vm.await { it.due.isEmpty() && it.rows.isNotEmpty() }
        val row = after.rows.single()
        assertThat(row.bucket).isEqualTo(ReminderBucket.DONE)
        assertThat(row.lastDoneOn).isEqualTo(day)
        assertThat(row.dueOn).isEqualTo(day.plusMonths(3))
    }

    @AcceptanceCriterion("AC-H12")
    @Test fun snoozeMovesADueReminderToUpcomingAndClosesTheMenu() {
        runBlocking {
            db.maintenanceTaskDao().upsert(MaintenanceTaskEntity(id = "t1", homeId = "h1", title = "Test sump pump", intervalMonths = 3, dueOn = "2026-11-06", createdAt = 1, updatedAt = 1))
        }
        val vm = vm()
        vm.await { it.due.isNotEmpty() }
        vm.onEvent(RemindersEvent.OpenSnooze("t1"))
        assertThat(vm.await { it.snoozeMenuFor == "t1" }.snoozeMenuFor).isEqualTo("t1")
        vm.onEvent(RemindersEvent.Snooze("t1", SnoozeLength.WEEK))
        val after = vm.await { it.upcoming.isNotEmpty() }
        assertThat(after.snoozeMenuFor).isNull()
        assertThat(after.upcoming.single().snoozedUntil).isEqualTo(day.plusWeeks(1))
    }

    @AcceptanceCriterion("AC-H12")
    @Test fun pickingAZoneSeedsSeasonalTasksAndMarksTheChip() {
        val vm = vm()
        vm.await { !it.loading }
        vm.onEvent(RemindersEvent.PickZone(ClimateZone.COLD))
        val seeded = vm.await { it.zone == ClimateZone.COLD && it.rows.size == MaintenanceRule.seasonalTasks(ClimateZone.COLD).size }
        assertThat(seeded.rows.all { it.seasonal }).isTrue()
        assertThat(seeded.rows.map { it.title }).contains("Shut off and drain outdoor faucets")
    }

    @AcceptanceCriterion("AC-H12")
    @Test fun addReminderNeedsTitleAndDateThenListsIt() {
        val vm = vm()
        vm.onEvent(RemindersEvent.OpenForm)
        vm.onEvent(RemindersEvent.SaveForm)
        assertThat(vm.await { it.showFormErrors }.form).isNotNull()
        vm.onEvent(RemindersEvent.ChangeForm(ReminderDraft("Reseal the deck", LocalDate.parse("2027-05-01"), 12)))
        vm.onEvent(RemindersEvent.SaveForm)
        val saved = vm.await { it.form == null && it.rows.isNotEmpty() }
        assertThat(saved.upcoming.single().title).isEqualTo("Reseal the deck")
        assertThat(saved.upcoming.single().intervalMonths).isEqualTo(12)
    }

    @Test fun openFormSurvivesProcessDeath() {
        val saved = SavedStateHandle()
        val first = vm(saved)
        first.onEvent(RemindersEvent.OpenForm)
        first.onEvent(RemindersEvent.ChangeForm(ReminderDraft("Clean the chimney", LocalDate.parse("2027-09-01"), 12)))
        val restored = vm(saved).await { it.form != null }.form
        assertThat(restored).isEqualTo(ReminderDraft("Clean the chimney", LocalDate.parse("2027-09-01"), 12))
    }

    @AcceptanceCriterion("AC-C49")
    @Test fun openingTheTabWritesWarrantyRemindersForStoredWarranties() {
        runBlocking { db.warrantyDao().upsert(WarrantyEntity(id = "w1", itemId = "i1", endsOn = "2027-03-01", createdAt = 1, updatedAt = 1)) }
        val state = vm().await { it.rows.size == 2 }
        assertThat(state.rows.map { it.dueOn }).containsExactly(LocalDate.parse("2026-12-31"), LocalDate.parse("2027-02-22"))
        assertThat(state.rows.first().title).isEqualTo("Warranty ends soon: Basement furnace")
    }
}
