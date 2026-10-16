package com.kurtz.homerecord.housemanual.home

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.logic.SearchResult
import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.EndpointKind
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.EquipmentRepository
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.PanelRepository
import com.kurtz.homerecord.core.repo.ShutoffRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HomeViewModelTest {
    private lateinit var db: HomeRecordDatabase

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    @Test fun `hub counts follow the data`() = runTest {
        val media = MediaFileStore(Files.createTempDirectory("media").toFile())
        val homes = HomeRepository(db, Source.HOUSE_MANUAL)
        val panels = PanelRepository(db, media, Source.HOUSE_MANUAL)
        val vm = HomeViewModel(homes, panels, ShutoffRepository(db, media, Source.HOUSE_MANUAL), EquipmentRepository(db, media, Source.HOUSE_MANUAL))
        backgroundScope.launch { vm.state.collect { } }

        val home = homes.createHomeWithRooms("Maple", listOf("Kitchen", "Garage"))
        val s0 = vm.state.first { it.roomCount == 2 }
        assertEquals("Maple", s0.homeName)
        assertEquals(false, s0.hasPanel)

        val panel = panels.createPanel(home.id, "Main", 20, PanelLayout.NA_NUMBERED)
        panels.saveBreaker(Breaker("", panel.id, listOf(1, 3), poles = 2, label = "Dryer", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL))
        val s1 = vm.state.first { it.percentLabeled == 10 }
        assertEquals(20, s1.panelSlotCount)

        val garage = homes.observeRooms(home.id).first().first { it.name == "Garage" }
        val saved = panels.saveBreaker(Breaker("", panel.id, listOf(5), label = "Garage", createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL))
        panels.saveEndpoint(Endpoint("", saved.id, garage.id, EndpointKind.OUTLET, "Freezer", 0, 0, Source.HOUSE_MANUAL))

        vm.onSearchChanged("garage freezer")
        val hit = vm.state.first { it.search == "garage freezer" && it.results.isNotEmpty() }.results.first() as SearchResult.BreakerHit
        assertEquals(saved.id, hit.breaker.id)
        vm.onSearchChanged("zzz")
        assertEquals(true, vm.state.first { it.search == "zzz" && it.results.isEmpty() }.isSearching)
    }
}
