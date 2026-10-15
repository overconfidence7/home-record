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
}
