package com.kurtz.homerecord.housemanual.panel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.PanelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PanelViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var panels: PanelRepository
    private lateinit var media: MediaFileStore

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        media = MediaFileStore(Files.createTempDirectory("media").toFile())
        homes = HomeRepository(db, Source.HOUSE_MANUAL)
        panels = PanelRepository(db, media, Source.HOUSE_MANUAL)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm() = PanelViewModel(homes, panels, "Main panel").also { it.state.launchIn(kotlinx.coroutines.CoroutineScope(Dispatchers.Main)) }

    @Test fun `AC-5 no panel shows setup with default 40 slots and saving creates the panel`() = runTest {
        val home = homes.createHome("H")
        val vm = vm()
        vm.state.first { it.loaded }
        assertNull(vm.state.value.panel)
        assertEquals("40", vm.state.value.slotCountInput)
        assertEquals(PanelLayout.NA_NUMBERED, vm.state.value.layoutInput)

        vm.onEvent(PanelEvent.SlotCountChanged("24"))
        vm.onEvent(PanelEvent.LayoutChanged(PanelLayout.DIN))
        vm.onEvent(PanelEvent.SavePanel)

        val panel = panels.observePanels(home.id).first { it.isNotEmpty() }.single()
        assertEquals(24, panel.slotCount)
        assertEquals(PanelLayout.DIN, panel.layout)
        assertEquals(panel.id, vm.state.first { it.panel != null }.panel?.id)
        assertTrue(vm.state.value.breakers.isEmpty())
    }

    @Test fun `invalid slot count blocks save`() = runTest {
        val home = homes.createHome("H")
        val vm = vm()
        vm.state.first { it.loaded }
        vm.onEvent(PanelEvent.SlotCountChanged("0"))
        assertFalse(vm.state.value.canSave)
        vm.onEvent(PanelEvent.SavePanel)
        withContext(Dispatchers.Default) { delay(300) }
        assertTrue(panels.observePanels(home.id).first().isEmpty())
    }

    @Test fun `saving twice does not create a second panel`() = runTest {
        val home = homes.createHome("H")
        val vm = vm()
        vm.state.first { it.loaded }
        vm.onEvent(PanelEvent.SavePanel)
        vm.state.first { it.panel != null }
        vm.onEvent(PanelEvent.SavePanel)
        withContext(Dispatchers.Default) { delay(300) }
        assertEquals(1, panels.observePanels(home.id).first().size)
    }

    @Test fun `AC-6 captured photo path is stored on the panel and replaced file is removed`() = runTest {
        homes.createHome("H")
        val vm = vm()
        vm.state.first { it.loaded }
        vm.onEvent(PanelEvent.SavePanel)
        vm.state.first { it.panel != null }

        val first = media.newPhotoPath()
        media.resolveForWrite(first)!!.writeText("a")
        vm.onPhotoCaptured(first)
        assertEquals(first, vm.state.first { it.panel?.photoPath == first }.panel?.photoPath)

        val second = media.newPhotoPath()
        media.resolveForWrite(second)!!.writeText("b")
        vm.onPhotoCaptured(second)
        assertEquals(second, vm.state.first { it.panel?.photoPath == second }.panel?.photoPath)
        assertFalse(media.exists(first))
        assertTrue(media.exists(second))
        assertNotNull(vm.state.value.panel)
    }

    private suspend fun panelWithBreaker(slots: List<Int>, poles: Int = 1): Pair<PanelViewModel, com.kurtz.homerecord.core.model.Breaker> {
        homes.createHome("H")
        val vm = vm()
        vm.state.first { it.loaded }
        vm.onEvent(PanelEvent.SavePanel)
        val panel = vm.state.first { it.panel != null }.panel!!
        val b = panels.saveBreaker(
            com.kurtz.homerecord.core.model.Breaker("", panel.id, slots, poles, label = "Oven", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL),
        )
        vm.state.first { it.breakers.isNotEmpty() }
        return vm to b
    }

    @Test fun `AC-10 long press splits a breaker slot into two tandem halves`() = runTest {
        val (vm, b) = panelWithBreaker(listOf(3))
        vm.onEvent(PanelEvent.SlotLongPressed(3))
        val state = vm.state.first { it.breakers.size == 2 }
        assertEquals(listOf(0, 1), state.tandemAt(3)?.map { it?.tandemIndex })
        assertEquals(b.id, state.tandemAt(3)?.get(0)?.id)
    }

    @Test fun `AC-11 shrinking below an occupied slot is refused with the highest slot`() = runTest {
        val (vm, _) = panelWithBreaker(listOf(12, 14), poles = 2)
        vm.onEvent(PanelEvent.SlotEditChanged("13"))
        vm.onEvent(PanelEvent.ApplySlotCount)
        assertEquals(14, vm.state.first { it.shrinkBlockedAt != null }.shrinkBlockedAt)
        assertEquals(40, vm.state.value.panel?.slotCount)
        vm.onEvent(PanelEvent.SlotEditChanged("20"))
        vm.onEvent(PanelEvent.ApplySlotCount)
        assertEquals(20, vm.state.first { it.panel?.slotCount == 20 }.panel?.slotCount)
    }

    @Test fun `AC-23 legend rows list linked room names for each breaker`() = runTest {
        val (vm, b) = panelWithBreaker(listOf(2))
        val home = homes.getHome()!!
        val room = homes.addRoom(home.id, "Kitchen")
        panels.linkRoom(b.id, room.id)
        val rows = vm.legendRows { "Standard" }
        assertEquals(40, rows.size)
        assertEquals("Oven", rows[1].label)
        assertEquals("Kitchen", rows[1].rooms)
        assertEquals("—", rows[0].label)
    }

    @Test fun `AC-101 setup saves the profile with defaults Main, Unknown, main breaker yes`() = runTest {
        val home = homes.createHome("H")
        val vm = vm()
        vm.state.first { it.loaded }
        vm.onEvent(PanelEvent.SavePanel)
        val p = panels.observePanels(home.id).first { it.isNotEmpty() }.single()
        assertEquals(com.kurtz.homerecord.core.model.PanelKind.MAIN, p.kind)
        assertEquals(com.kurtz.homerecord.core.model.TandemPolicy.UNKNOWN, p.tandemPolicy)
        assertTrue(p.hasMainBreaker)
        assertNull(p.ratingAmps)
    }

    @Test fun `AC-101 invalid profile blocks creating the panel and reports the field`() = runTest {
        val home = homes.createHome("H")
        val vm = vm()
        vm.state.first { it.loaded }
        vm.onEvent(PanelEvent.ProfileChanged(com.kurtz.homerecord.core.logic.PanelProfileInput(yearInstalled = "3000")))
        vm.onEvent(PanelEvent.SavePanel)
        assertEquals(setOf(com.kurtz.homerecord.core.logic.PanelProfileError.YEAR), vm.state.value.profileErrors)
        assertTrue(panels.observePanels(home.id).first().isEmpty())
    }

    @Test fun `AC-101 details editor saves and cancel discards`() = runTest {
        val home = homes.createHome("H")
        panels.createPanel(home.id, "Main", 20, PanelLayout.NA_NUMBERED)
        val vm = vm()
        vm.state.first { it.panel != null }
        vm.onEvent(PanelEvent.EditProfile)
        val edit = vm.state.value.profileEdit!!
        vm.onEvent(PanelEvent.ProfileChanged(edit.copy(brand = "Eaton BR", ratingAmps = "200", tandemPolicy = com.kurtz.homerecord.core.model.TandemPolicy.NOT_ALLOWED)))
        vm.onEvent(PanelEvent.CancelProfile)
        assertNull(panels.observePanels(home.id).first().single().brand)

        vm.onEvent(PanelEvent.EditProfile)
        vm.onEvent(PanelEvent.ProfileChanged(vm.state.value.profileEdit!!.copy(brand = "Eaton BR", ratingAmps = "200")))
        vm.onEvent(PanelEvent.SaveProfile)
        val saved = vm.state.first { it.profileEdit == null && it.panel?.brand != null }.panel!!
        assertEquals("Eaton BR", saved.brand)
        assertEquals(200, saved.ratingAmps)
    }

    @Test fun `AC-102 sub-panel feed breaker produces a feeds note and legend label`() = runTest {
        val home = homes.createHome("H")
        val main = panels.createPanel(home.id, "Main", 20, PanelLayout.NA_NUMBERED)
        val feed = panels.saveBreaker(com.kurtz.homerecord.core.model.Breaker("", main.id, listOf(1), label = "Garage feed", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL))
        val vm = vm()
        vm.state.first { it.panel != null }
        val sub = panels.createPanel(home.id, "Garage sub", 12, PanelLayout.NA_NUMBERED)
        panels.updateProfile(sub.id, com.kurtz.homerecord.core.logic.PanelProfile(com.kurtz.homerecord.core.model.PanelKind.SUB, null, null, null, null, com.kurtz.homerecord.core.model.TandemPolicy.UNKNOWN, true, null, feed.id))
        val s = vm.state.first { it.feedNotes.isNotEmpty() }
        assertEquals("Garage sub", s.feedNotes[feed.id])
        val rows = vm.legendRows({ "-> feeds $it" }, { "Standard" })
        assertEquals("Garage feed -> feeds Garage sub", rows.first().label)
    }

    @Test fun `AC-106 and AC-113 insights open from current data, dismiss, and write nothing`() = runTest {
        val home = homes.createHome("H")
        val panel = panels.createPanel(home.id, "Main", 20, PanelLayout.NA_NUMBERED)
        val vm = vm()
        vm.state.first { it.panel != null }
        assertNull(vm.state.value.insights)

        vm.onEvent(PanelEvent.ShowInsights)
        assertEquals(0, vm.state.first { it.insights != null }.insights?.capacity?.circuits)

        panels.saveBreaker(com.kurtz.homerecord.core.model.Breaker("", panel.id, listOf(3), label = "Dishwasher", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL))
        vm.onEvent(PanelEvent.DismissInsights)
        assertNull(vm.state.value.insights)
        vm.onEvent(PanelEvent.ShowInsights)
        val result = vm.state.first { it.insights != null }.insights
        assertEquals(1, result?.capacity?.circuits)
        assertEquals(1, panels.observeBreakers(panel.id).first().size)
    }
}
