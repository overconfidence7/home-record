package com.kurtz.homerecord.housemanual.shutoffs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.ShutoffKind
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.ShutoffRepository
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
class ShutoffEditViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var shutoffs: ShutoffRepository
    private lateinit var media: MediaFileStore

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        media = MediaFileStore(Files.createTempDirectory("media").toFile())
        homes = HomeRepository(db, Source.HOUSE_MANUAL)
        shutoffs = ShutoffRepository(db, media, Source.HOUSE_MANUAL)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun vm(id: String? = null) =
        ShutoffEditViewModel(id, homes, shutoffs, media).also { v -> v.state.first { it.loaded } }

    private suspend fun ShutoffEditViewModel.saveAndWait() {
        onEvent(ShutoffEditEvent.Save)
        done.first()
    }

    private suspend fun ShutoffEditViewModel.deleteAndWait() {
        onEvent(ShutoffEditEvent.ConfirmDelete)
        done.first()
    }

    private fun writePhoto(): String {
        val path = media.newPhotoPath()
        media.resolveForWrite(path)!!.writeBytes(byteArrayOf(1, 2, 3))
        return path
    }

    @Test fun `AC-26 save needs kind and instructions, room and photo optional`() = runTest {
        val home = homes.createHome("H")
        val vm = vm()
        assertFalse(vm.state.value.canSave)
        vm.onEvent(ShutoffEditEvent.KindChanged(ShutoffKind.WATER_MAIN))
        assertFalse(vm.state.value.canSave)
        vm.onEvent(ShutoffEditEvent.InstructionsChanged("   "))
        assertFalse(vm.state.value.canSave)
        vm.onEvent(ShutoffEditEvent.InstructionsChanged("Turn clockwise "))
        assertTrue(vm.state.value.canSave)
        vm.saveAndWait()
        val saved = shutoffs.observeShutoffs(home.id).first().single()
        assertEquals(ShutoffKind.WATER_MAIN, saved.kind)
        assertEquals("Turn clockwise", saved.instructions)
        assertNull(saved.roomId)
        assertNull(saved.photoPath)
    }

    @Test fun `save is ignored while invalid`() = runTest {
        val home = homes.createHome("H")
        val vm = vm()
        vm.onEvent(ShutoffEditEvent.InstructionsChanged("x"))
        vm.onEvent(ShutoffEditEvent.Save)
        assertTrue(shutoffs.observeShutoffs(home.id).first().isEmpty())
    }

    @Test fun `AC-27 captured photo is stored and kept after save`() = runTest {
        val home = homes.createHome("H")
        val room = homes.addRoom(home.id, "Basement")
        val vm = vm()
        vm.onEvent(ShutoffEditEvent.KindChanged(ShutoffKind.GAS_MAIN))
        vm.onEvent(ShutoffEditEvent.RoomChanged(room.id))
        vm.onEvent(ShutoffEditEvent.InstructionsChanged("Quarter turn"))
        val path = writePhoto()
        vm.onPhotoCaptured(path)
        vm.saveAndWait()
        val saved = shutoffs.observeShutoffs(home.id).first().single()
        assertEquals(path, saved.photoPath)
        assertEquals(room.id, saved.roomId)
        assertNotNull(media.resolve(path))
        assertTrue(media.resolve(path)!!.exists())
    }

    @Test fun `retaking a photo before saving removes the discarded file`() = runTest {
        homes.createHome("H")
        val vm = vm()
        val first = writePhoto()
        vm.onPhotoCaptured(first)
        val second = writePhoto()
        vm.onPhotoCaptured(second)
        assertFalse(media.resolve(first)!!.exists())
        assertTrue(media.resolve(second)!!.exists())
    }

    @Test fun `editing replaces the saved photo file and delete removes record and photo`() = runTest {
        val home = homes.createHome("H")
        val create = vm()
        create.onEvent(ShutoffEditEvent.KindChanged(ShutoffKind.SUMP))
        create.onEvent(ShutoffEditEvent.InstructionsChanged("Unplug"))
        val original = writePhoto()
        create.onPhotoCaptured(original)
        create.saveAndWait()
        val id = shutoffs.observeShutoffs(home.id).first().single().id

        val edit = vm(id)
        assertFalse(edit.state.value.isNew)
        assertEquals("Unplug", edit.state.value.instructions)
        val replacement = writePhoto()
        edit.onPhotoCaptured(replacement)
        edit.saveAndWait()
        assertFalse(media.resolve(original)!!.exists())
        assertEquals(replacement, shutoffs.get(id)!!.photoPath)

        val del = vm(id)
        del.onEvent(ShutoffEditEvent.AskDelete)
        assertTrue(del.state.value.confirmingDelete)
        del.deleteAndWait()
        assertNull(shutoffs.get(id))
        assertFalse(media.resolve(replacement)!!.exists())
    }
}
