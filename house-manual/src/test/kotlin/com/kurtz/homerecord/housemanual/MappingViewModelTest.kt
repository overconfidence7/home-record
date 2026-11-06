package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.mapping.MappingAlarm
import com.kurtz.homerecord.core.mapping.MappingPhase
import com.kurtz.homerecord.core.mapping.PowerMonitor
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemanual.mapping.MappingEvent
import com.kurtz.homerecord.housemanual.mapping.MappingUiState
import com.kurtz.homerecord.housemanual.mapping.MappingViewModel
import com.kurtz.homerecord.housemodel.panel.BreakerDraft
import com.kurtz.homerecord.housemodel.panel.BreakerSave
import com.kurtz.homerecord.housemodel.panel.FeedChoices
import com.kurtz.homerecord.housemodel.panel.FeedOption
import com.kurtz.homerecord.housemodel.panel.PanelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

private class MapFakePower : PowerMonitor {
    val flow = MutableStateFlow(true)
    override fun plugged(): Flow<Boolean> = flow
}

private class MapFakeAlarm : MappingAlarm {
    var playing = false
    override fun start() { playing = true }
    override fun stop() { playing = false }
}

// AC-C42: HM-Mapping: unplug while armed → alarm; Silence → pick breaker and room → link Confirmed; leaving silences it.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
@AcceptanceCriterion("AC-C42")
class MappingViewModelTest {
    private val main = UnconfinedTestDispatcher()
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: PanelRepository
    private val power = MapFakePower()
    private val alarm = MapFakeAlarm()
    private var clock = 0L
    private var vm: MappingViewModel? = null
    private lateinit var breakerId: String

    @Before fun setUp() {
        Dispatchers.setMain(main)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = PanelRepository(db)
        runBlocking {
            db.itemDao().upsert(ItemEntity(id = "pi", homeId = "h1", category = "ELECTRICAL", subtype = "breaker_panel", name = "Main panel", createdAt = 1, updatedAt = 1))
            val panel = repo.ensurePanel("pi")
            breakerId = (repo.saveBreaker(panel, BreakerDraft(startSlot = 12, amps = "20", label = "Kitchen outlets")) as BreakerSave.Saved).id
        }
    }

    @After fun tearDown() {
        runBlocking { vm?.viewModelScope?.coroutineContext?.get(Job)?.cancelAndJoin() }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm() = MappingViewModel(
        repo, "pi", db.itemDao().observeOne("pi").map { it?.name },
        flowOf(FeedChoices(rooms = listOf(FeedOption("kitchen", "Kitchen")), items = listOf(FeedOption("fridge", "Refrigerator")))),
        power, alarm, { clock },
    ).also { vm = it }

    private fun MappingViewModel.await(what: (MappingUiState) -> Boolean) = runBlocking { withTimeout(5_000) { state.first(what) } }

    @Test fun powerDropSoundsTheAlarmAndThePickConfirmsTheLink() {
        val vm = vm()
        val idle = vm.await { !it.loading }
        assertThat(idle.panelName).isEqualTo("Main panel")
        assertThat(idle.breakers.map { it.number }).containsExactly(12)
        assertThat(idle.plugged).isTrue()

        vm.onEvent(MappingEvent.Start)
        assertThat(vm.await { it.phase == MappingPhase.ARMED }.keepScreenOn).isTrue()
        clock = 30_000
        power.flow.value = false
        vm.await { it.phase == MappingPhase.ALARM }
        assertThat(alarm.playing).isTrue()

        vm.onEvent(MappingEvent.Silence)
        vm.await { it.phase == MappingPhase.PICKING }
        assertThat(alarm.playing).isFalse()
        vm.onEvent(MappingEvent.Save)
        assertThat(vm.state.value.phase).isEqualTo(MappingPhase.PICKING)
        vm.onEvent(MappingEvent.PickBreaker(breakerId))
        vm.onEvent(MappingEvent.PickRoom("kitchen"))
        assertThat(vm.await { it.canSave }.roomId).isEqualTo("kitchen")
        vm.onEvent(MappingEvent.Save)
        val after = vm.await { it.confirmed == 1 }
        assertThat(after.phase).isEqualTo(MappingPhase.WAITING_FOR_POWER)
        assertThat(after.breakerId).isNull()

        val links = runBlocking { db.breakerLinkDao().listFor(breakerId) }
        assertThat(links.single().roomId).isEqualTo("kitchen")
        assertThat(links.single().confidence).isEqualTo("CONFIRMED")
    }

    @Test fun leavingTheScreenSilencesAndNothingSoundsLater() {
        val vm = vm()
        vm.await { !it.loading }
        vm.onEvent(MappingEvent.Start)
        clock = 30_000
        power.flow.value = false
        vm.await { it.phase == MappingPhase.ALARM }
        vm.leave()
        assertThat(alarm.playing).isFalse()
        power.flow.value = true
        clock = 60_000
        power.flow.value = false
        val stopped = vm.await { it.phase == MappingPhase.STOPPED }
        assertThat(stopped.keepScreenOn).isFalse()
        assertThat(alarm.playing).isFalse()

        vm.onEvent(MappingEvent.Resume)
        assertThat(vm.await { it.phase == MappingPhase.IDLE }.plugged).isFalse()
    }

    @Test fun skipGoesBackToListeningWithoutALink() {
        val vm = vm()
        vm.await { !it.loading }
        vm.onEvent(MappingEvent.Start)
        clock = 30_000
        power.flow.value = false
        vm.onEvent(MappingEvent.Silence)
        power.flow.value = true
        vm.onEvent(MappingEvent.PickBreaker(breakerId))
        vm.onEvent(MappingEvent.Skip)
        val s = vm.await { it.phase == MappingPhase.ARMED }
        assertThat(s.confirmed).isEqualTo(0)
        assertThat(s.breakerId).isNull()
        assertThat(runBlocking { db.breakerLinkDao().listFor(breakerId) }).isEmpty()
    }
}
