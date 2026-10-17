package com.kurtz.homerecord.housemanual.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.HomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class SettingsViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private val exported = mutableListOf<Pair<String, String>>()
    private var result: String? = "shared/b.homerecord"

    private val exporter = object : BackupExporter {
        override suspend fun export(homeId: String, passphrase: String): String? {
            exported += homeId to passphrase
            return result
        }
    }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        homes = HomeRepository(db, Source.HOUSE_MANUAL)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun SettingsViewModel.type(pass: String, confirm: String = pass) {
        onEvent(SettingsEvent.PassphraseChanged(pass))
        onEvent(SettingsEvent.ConfirmChanged(confirm))
    }

    @Test fun `export needs 8 characters and a matching confirmation (AC-88)`() = runTest {
        homes.createHome("Mine")
        val vm = SettingsViewModel(homes, exporter)
        vm.state.first { it.hasHome }
        vm.type("short")
        assertFalse(vm.state.value.canExport)
        vm.type("longenough", "different1")
        assertTrue(vm.state.value.passphraseMismatch)
        assertFalse(vm.state.value.canExport)
        vm.onEvent(SettingsEvent.Export)
        assertTrue(exported.isEmpty())
        vm.type("longenough")
        assertTrue(vm.state.value.canExport)
    }

    @Test fun `export hands the file to the share sheet and clears the passphrase`() = runTest {
        val home = homes.createHome("Mine")
        val vm = SettingsViewModel(homes, exporter)
        vm.state.first { it.hasHome }
        vm.type("longenough")
        vm.onEvent(SettingsEvent.Export)
        assertEquals("shared/b.homerecord", vm.share.first())
        assertEquals(listOf(home.id to "longenough"), exported)
        assertEquals("", vm.state.value.passphrase)
        assertFalse(vm.state.value.exportFailed)
    }

    @Test fun `a failed export shows an error and keeps the passphrase`() = runTest {
        homes.createHome("Mine")
        result = null
        val vm = SettingsViewModel(homes, exporter)
        vm.state.first { it.hasHome }
        vm.type("longenough")
        vm.onEvent(SettingsEvent.Export)
        vm.state.first { it.exportFailed }
        assertEquals("longenough", vm.state.value.passphrase)
        assertFalse(vm.state.value.exporting)
    }
}
