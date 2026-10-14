package com.kurtz.homerecord.housemanual.onboarding

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.housemanual.nav.Routes
import com.kurtz.homerecord.housemanual.nav.StartDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class OnboardingViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        homes = HomeRepository(db, Source.HOUSE_MANUAL)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm() = OnboardingViewModel(homes) { it.name }

    @Test fun `continue is disabled for blank names`() {
        val vm = vm()
        assertFalse(vm.state.value.canContinue)
        vm.onEvent(OnboardingEvent.HomeNameChanged("   "))
        assertFalse(vm.state.value.canContinue)
        vm.onEvent(OnboardingEvent.HomeNameChanged(" A "))
        assertTrue(vm.state.value.canContinue)
    }

    @Test fun `blank continue does not create anything`() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(OnboardingEvent.Continue)
        advanceUntilIdle()
        assertEquals(null, homes.getHome())
    }

    @Test fun `three quick rooms plus one custom create one home and four rooms`() = runTest(dispatcher) {
        val vm = vm()
        vm.onEvent(OnboardingEvent.HomeNameChanged("Maple St"))
        listOf(QuickRoom.KITCHEN, QuickRoom.GARAGE, QuickRoom.ATTIC).forEach { vm.onEvent(OnboardingEvent.ToggleRoom(it)) }
        vm.onEvent(OnboardingEvent.CustomInputChanged("Sunroom"))
        vm.onEvent(OnboardingEvent.AddCustomRoom)
        assertEquals("", vm.state.value.customInput)
        vm.onEvent(OnboardingEvent.Continue)
        advanceUntilIdle()
        vm.done.first()
        val home = homes.getHome()!!
        assertEquals("Maple St", home.name)
        assertEquals(listOf("ATTIC", "GARAGE", "KITCHEN", "Sunroom"), homes.observeRooms(home.id).first().map { it.name }.sorted())
        assertEquals(Routes.HOME, StartDestination.resolve(homes))
    }

    @Test fun `toggling twice deselects and duplicate custom rooms are ignored`() {
        val vm = vm()
        vm.onEvent(OnboardingEvent.ToggleRoom(QuickRoom.OFFICE))
        vm.onEvent(OnboardingEvent.ToggleRoom(QuickRoom.OFFICE))
        assertTrue(vm.state.value.selected.isEmpty())
        repeat(2) {
            vm.onEvent(OnboardingEvent.CustomInputChanged(if (it == 0) "Den" else " den "))
            vm.onEvent(OnboardingEvent.AddCustomRoom)
        }
        assertEquals(listOf("Den"), vm.state.value.customRooms)
    }
}
