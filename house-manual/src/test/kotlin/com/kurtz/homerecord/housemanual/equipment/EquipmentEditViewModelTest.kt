package com.kurtz.homerecord.housemanual.equipment

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.EquipmentRepository
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
class EquipmentEditViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var equipment: EquipmentRepository
    private lateinit var media: MediaFileStore

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        media = MediaFileStore(Files.createTempDirectory("media").toFile())
        homes = HomeRepository(db, Source.HOUSE_MANUAL)
        equipment = EquipmentRepository(db, media, Source.HOUSE_MANUAL)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun vm(id: String? = null) =
        EquipmentEditViewModel(id, homes, equipment, media).also { v -> v.state.first { it.loaded } }

    private fun writePhoto(): String {
        val path = media.newPhotoPath()
        media.resolveForWrite(path)!!.writeBytes(byteArrayOf(1, 2, 3))
        return path
    }

    @Test fun `AC-43 category alone is enough to save`() = runTest {
        val home = homes.createHome("H")
        val vm = vm()
        assertFalse(vm.state.value.canSave)
        vm.onEvent(EquipmentEditEvent.CategoryChanged(EquipmentCategory.FURNACE))
        assertTrue(vm.state.value.canSave)
        vm.onEvent(EquipmentEditEvent.Save)
        vm.done.first()
        val saved = equipment.observeEquipment(home.id).first().single()
        assertEquals(EquipmentCategory.FURNACE, saved.category)
        assertNull(saved.make)
        assertNull(saved.installedOn)
        assertNull(saved.labelPhotoPath)
    }

    @Test fun `invalid date blocks save and blank fields are stored as null`() = runTest {
        val home = homes.createHome("H")
        val vm = vm()
        vm.onEvent(EquipmentEditEvent.CategoryChanged(EquipmentCategory.AC))
        vm.onEvent(EquipmentEditEvent.InstalledOnChanged("last year"))
        assertFalse(vm.state.value.canSave)
        vm.onEvent(EquipmentEditEvent.Save)
        assertTrue(equipment.observeEquipment(home.id).first().isEmpty())
        vm.onEvent(EquipmentEditEvent.InstalledOnChanged("2020-05-01"))
        vm.onEvent(EquipmentEditEvent.MakeChanged("  Trane "))
        vm.onEvent(EquipmentEditEvent.ModelChanged("   "))
        vm.onEvent(EquipmentEditEvent.Save)
        vm.done.first()
        val saved = equipment.observeEquipment(home.id).first().single()
        assertEquals("2020-05-01", saved.installedOn)
        assertEquals("Trane", saved.make)
        assertNull(saved.model)
    }

    @Test fun `AC-48 label photo is kept and AC-50 delete removes record and photo file`() = runTest {
        val home = homes.createHome("H")
        val create = vm()
        create.onEvent(EquipmentEditEvent.CategoryChanged(EquipmentCategory.WATER_HEATER))
        val photo = writePhoto()
        create.onPhotoCaptured(photo)
        create.onEvent(EquipmentEditEvent.Save)
        create.done.first()
        val id = equipment.observeEquipment(home.id).first().single().id
        assertTrue(media.resolve(photo)!!.exists())

        val edit = vm(id)
        assertEquals(photo, edit.state.value.labelPhotoPath)
        edit.onEvent(EquipmentEditEvent.AskDelete)
        assertTrue(edit.state.value.confirmingDelete)
        edit.onEvent(EquipmentEditEvent.ConfirmDelete)
        edit.done.first()
        assertNull(equipment.get(id))
        assertFalse(media.resolve(photo)!!.exists())
    }

    @Test fun `retaking before save removes the discarded file`() = runTest {
        homes.createHome("H")
        val vm = vm()
        val first = writePhoto()
        vm.onPhotoCaptured(first)
        val second = writePhoto()
        vm.onPhotoCaptured(second)
        assertFalse(media.resolve(first)!!.exists())
        assertTrue(media.resolve(second)!!.exists())
    }
}
