package com.kurtz.homerecord.housemanual.imports

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kurtz.homerecord.core.bundle.BundleCrypto
import com.kurtz.homerecord.core.bundle.BundleWriter
import com.kurtz.homerecord.core.bundle.RecordBundle
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.toEntity
import com.kurtz.homerecord.core.imports.ConflictChoice
import com.kurtz.homerecord.core.imports.ImportApplier
import com.kurtz.homerecord.core.imports.ImportEntityType
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Home
import com.kurtz.homerecord.core.model.Photo
import com.kurtz.homerecord.core.model.PhotoStage
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.HomeRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ImportViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var filesDir: File
    private val files = HashMap<String, ByteArray>()
    private val io = UnconfinedTestDispatcher()

    private val source = object : BundleSource {
        override fun open(uri: String): InputStream? = files[uri]?.let(::ByteArrayInputStream)
        override fun newStagingDir(): File = tmp.newFolder()
    }

    @Before fun setUp() {
        Dispatchers.setMain(io)
        db = HomeRecordDatabase.inMemory(ApplicationProvider.getApplicationContext<Context>())
        filesDir = tmp.newFolder("files")
        homes = HomeRepository(db, Source.HOUSE_MANUAL)
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /** What Field Log would export: a room with a behind-wall photo and its jpeg. */
    private fun fieldLogBundle(roomName: String = "Kitchen"): ByteArray {
        val flFiles = tmp.newFolder()
        File(flFiles, "media").mkdirs()
        File(flFiles, "media/bw1.jpg").writeText("jpeg")
        val bundle = RecordBundle(
            home = Home("fl-home", "Elm St", createdAt = 1, updatedAt = 1, source = Source.FIELD_LOG),
            rooms = listOf(Room("r1", "fl-home", roomName, null, 1, 1, Source.FIELD_LOG)),
            photos = listOf(Photo("bw1", "fl-home", "r1", null, PhotoStage.BEHIND_WALL, 5, null, "media/bw1.jpg", 1, 1, Source.FIELD_LOG)),
        )
        val out = ByteArrayOutputStream()
        BundleWriter(MediaFileStore(flFiles)).write(bundle, out)
        return out.toByteArray()
    }

    /** Room runs queries on its own threads, so wait for the view model to leave READING / finish applying. */
    private suspend fun ImportViewModel.loaded(): ImportUiState = state.first { it.phase != ImportPhase.READING }

    private suspend fun ImportViewModel.confirm() {
        onEvent(ImportEvent.Confirm)
        state.first { it.phase == ImportPhase.DONE }
    }

    private fun vm(uri: String? = null) =
        ImportViewModel(ImportApplier(db, MediaFileStore(filesDir)), source, uri, io = io)

    @Test fun `AC-76 and AC-77 preview shows counts, apply puts the behind-wall photo in the local room`() = runTest {
        val home = homes.createHome("Mine")
        files["content://a.homerecord"] = fieldLogBundle()
        val vm = vm("content://a.homerecord")

        val preview = vm.loaded()
        assertEquals(ImportPhase.PREVIEW, preview.phase)
        assertEquals(listOf(ImportEntityType.ROOM to 1, ImportEntityType.PHOTO to 1), preview.counts)
        assertTrue(preview.conflicts.isEmpty())

        vm.confirm()
        assertEquals(ImportPhase.DONE, vm.state.value.phase)
        assertEquals(2, vm.state.value.result?.inserted)

        val photo = homes.observePhotos(home.id).first().single()
        assertEquals("r1", photo.roomId)
        assertEquals(PhotoStage.BEHIND_WALL, photo.stage)
        assertEquals("jpeg", File(filesDir, "media/bw1.jpg").readText())
    }

    @Test fun `a room edited here is listed as a conflict and defaults to keep mine (AC-76)`() = runTest {
        val home = homes.createHome("Mine")
        db.roomDao().upsert(Room("r1", home.id, "Kitchen (mine)", null, 1, 1, Source.HOUSE_MANUAL).toEntity())
        files["u"] = fieldLogBundle("Kitchen (theirs)")
        val vm = vm("u")
        vm.loaded()
        assertEquals(1, vm.state.value.conflicts.size)
        assertEquals(ConflictChoice.KEEP_MINE, vm.state.value.choiceFor("r1"))

        vm.confirm()
        assertEquals("Kitchen (mine)", db.roomDao().get("r1")!!.name)
    }

    @Test fun `choosing use imported replaces the local room (AC-78)`() = runTest {
        val home = homes.createHome("Mine")
        db.roomDao().upsert(Room("r1", home.id, "Kitchen (mine)", null, 1, 1, Source.HOUSE_MANUAL).toEntity())
        files["u"] = fieldLogBundle("Kitchen (theirs)")
        val vm = vm("u")
        vm.loaded()
        vm.onEvent(ImportEvent.Choose("r1", ConflictChoice.USE_IMPORTED))
        vm.confirm()
        assertEquals("Kitchen (theirs)", db.roomDao().get("r1")!!.name)
    }

    @Test fun `importing the same file again reports nothing new (AC-79)`() = runTest {
        homes.createHome("Mine")
        files["u"] = fieldLogBundle()
        val vm = vm("u")
        vm.loaded()
        vm.confirm()
        vm.onEvent(ImportEvent.FileChosen("u"))
        vm.loaded()
        assertTrue(vm.state.value.nothingToImport)
        assertEquals(2, vm.state.value.alreadyPresent)
    }

    @Test fun `garbage file gives a readable error and no changes`() = runTest {
        homes.createHome("Mine")
        files["bad"] = "not a zip".toByteArray()
        val vm = vm("bad")
        assertEquals(ImportPhase.ERROR, vm.loaded().phase)
        assertTrue(db.roomDao().getAll().isEmpty())
    }

    @Test fun `unopenable uri is an error`() = runTest {
        homes.createHome("Mine")
        assertEquals(ImportPhase.ERROR, vm("missing").loaded().phase)
    }

    @Test fun `no local home restores the bundle's home on import (AC-90)`() = runTest {
        files["u"] = fieldLogBundle()
        val vm = vm("u")
        val preview = vm.loaded()
        assertEquals(ImportPhase.PREVIEW, preview.phase)
        assertTrue(preview.restoresHome)
        assertFalse(preview.nothingToImport)
        vm.confirm()
        assertEquals(listOf("fl-home"), db.homeDao().getAll().map { it.id })
        assertEquals("jpeg", File(filesDir, "media/bw1.jpg").readText())
    }

    private fun encrypted(plain: ByteArray, pass: String): ByteArray =
        ByteArrayOutputStream().also { BundleCrypto.encrypt(ByteArrayInputStream(plain), it, pass) }.toByteArray()

    @Test fun `encrypted bundle asks for the passphrase, wrong one is an error, right one previews (AC-89)`() = runTest {
        homes.createHome("Mine")
        files["u"] = encrypted(fieldLogBundle(), "correct horse")
        val vm = vm("u")
        assertEquals(ImportPhase.NEEDS_PASSPHRASE, vm.loaded().phase)
        assertFalse(vm.state.value.wrongPassphrase)

        vm.onEvent(ImportEvent.PassphraseEntered("wrong horse"))
        val wrong = vm.state.first { it.phase == ImportPhase.NEEDS_PASSPHRASE && it.wrongPassphrase }
        assertTrue(wrong.wrongPassphrase)
        assertTrue(db.roomDao().getAll().isEmpty())

        vm.onEvent(ImportEvent.PassphraseEntered("correct horse"))
        val preview = vm.state.first { it.phase == ImportPhase.PREVIEW }
        assertEquals(listOf(ImportEntityType.ROOM to 1, ImportEntityType.PHOTO to 1), preview.counts)
        vm.confirm()
        assertEquals("jpeg", File(filesDir, "media/bw1.jpg").readText())
    }

    @Test fun `starts at the picker when there is no uri`() = runTest {
        assertEquals(ImportPhase.PICK, vm().state.value.phase)
        assertFalse(vm().state.value.applying)
    }
}
