package com.kurtz.homerecord.housemanual

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.media.CaptureTarget
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.items.PhotoKind
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.ui.ItemEditEvent
import com.kurtz.homerecord.housemodel.ui.ItemEditViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class ItemEditViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var items: ItemRepository
    private lateinit var homeId: String

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        homes = HomeRepository(db)
        items = ItemRepository(db)
    }

    @After fun tearDown() {
        // A save still in flight when the database closes would throw into the next test class.
        runBlocking { created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private val created = mutableListOf<ItemEditViewModel>()
    private val media = FakeMediaFiles()
    private var plateLines = listOf("RHEEM", "MODEL NO: XG50T06EC36U1", "SERIAL NO: 1234567890", "50 GAL")
    private val recognizer = object : com.kurtz.homerecord.core.ocr.TextRecognizer {
        override suspend fun recognizeLines(path: String) = plateLines
    }

    private var visionResult: com.kurtz.homerecord.core.company.ApiResult<com.kurtz.homerecord.core.lookup.PlateReading> =
        com.kurtz.homerecord.core.company.ApiResult.Err(com.kurtz.homerecord.core.company.ApiFailure.Offline)
    private val vision = object : com.kurtz.homerecord.core.lookup.DataPlateVisionApi {
        override suspend fun read(path: String) = visionResult
    }

    private fun made(items: ItemRepository, homes: HomeRepository, homeId: String, itemId: String?, handle: SavedStateHandle) =
        ItemEditViewModel(items, homes, homeId, itemId, handle, media, recognizer, vision).also { created += it }

    private suspend fun home() = homes.createHome(NewHome("Maple St", null, HouseType.TWO_STORY, Foundation.BASEMENT, true)).also { homeId = it }

    @AcceptanceCriterion("AC-C30")
    @Test fun saveNeedsTypeRoomAndNameThenStoresTheItem() = runTest {
        home()
        val vm = made(items, homes, homeId, null, SavedStateHandle())
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.Save)
        assertThat(vm.state.value.showErrors).isTrue()
        assertThat(vm.state.value.savedItemId).isNull()

        vm.onEvent(ItemEditEvent.PickSubtype("furnace"))
        // Picking a type names a blank item after it.
        assertThat(vm.state.value.draft.name).isEqualTo("Furnace")
        vm.onEvent(ItemEditEvent.PickRoom(PseudoRooms.WHOLE_HOUSE))
        vm.onEvent(ItemEditEvent.Save)
        val id = vm.state.first { it.savedItemId != null }.savedItemId
        assertThat(id).isNotNull()
        assertThat(items.get(checkNotNull(id))?.subtype).isEqualTo("furnace")
    }

    @AcceptanceCriterion("AC-H2")
    @Test fun addShutoffOpensWithTheShutoffTypeChosen() = runTest {
        home()
        val vm = ItemEditViewModel(items, homes, homeId, null, SavedStateHandle(), media, recognizer, vision, initialSubtypeId = "water_main").also { created += it }
        vm.state.first { !it.loading }
        assertThat(vm.state.value.draft.subtypeId).isEqualTo("water_main")
        assertThat(vm.state.value.draft.name).isEqualTo("Water main")
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun roomPickerListsEveryRoomOfTheHome() = runTest {
        home()
        val vm = made(items, homes, homeId, null, SavedStateHandle())
        vm.state.first { !it.loading }
        assertThat(vm.state.value.rooms.map { it.name }).contains("Garage")
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun draftSurvivesProcessDeath() = runTest {
        home()
        val handle = SavedStateHandle()
        val vm = made(items, homes, homeId, null, handle)
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.PickSubtype("dishwasher"))
        vm.onEvent(ItemEditEvent.Change(vm.state.value.draft.copy(make = "Bosch")))
        val revived = made(items, homes, homeId, null, SavedStateHandle(mapOf("draft" to handle.get<ArrayList<String>>("draft"))))
        revived.state.first { !it.loading }
        assertThat(revived.state.value.draft.make).isEqualTo("Bosch")
        assertThat(revived.state.value.draft.subtypeId).isEqualTo("dishwasher")
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun editingAnItemLoadsItsDraftAndMissingItemsAreReported() = runTest {
        home()
        val id = checkNotNull(items.save(homeId, com.kurtz.homerecord.housemodel.items.ItemDraft(subtypeId = "range", roomId = PseudoRooms.EXTERIOR, name = "Range")))
        val vm = made(items, homes, homeId, id, SavedStateHandle())
        vm.state.first { !it.loading }
        assertThat(vm.state.value.draft.name).isEqualTo("Range")
        assertThat(vm.state.value.dirty).isFalse()
        val gone = made(items, homes, homeId, "nope", SavedStateHandle())
        gone.state.first { !it.loading }
        assertThat(gone.state.value.missing).isTrue()
    }

    @AcceptanceCriterion("AC-C31")
    @Test fun dataPlatePhotoProposesValuesAndTapAssignsLines() = runTest {
        home()
        val vm = made(items, homes, homeId, null, SavedStateHandle())
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.TakePhoto(PhotoKind.DATA_PLATE))
        val path = checkNotNull(checkNotNull(vm.state.value.photoLaunch).target).path
        media.files += path
        vm.onEvent(ItemEditEvent.PhotoCaptured(PhotoKind.DATA_PLATE, path))
        val scan = checkNotNull(vm.state.first { it.plateScan != null }.plateScan)
        assertThat(scan.make).isEqualTo("Rheem")
        assertThat(scan.model).isEqualTo("XG50T06EC36U1")
        assertThat(scan.lines).contains("50 GAL")

        // Tap-to-assign: pick Serial, tap a line, then apply to the form.
        vm.onEvent(ItemEditEvent.PlateTarget(com.kurtz.homerecord.core.model.PlateField.SERIAL))
        vm.onEvent(ItemEditEvent.PlateAssign("1234567890"))
        vm.onEvent(ItemEditEvent.PlateApply)
        assertThat(vm.state.value.plateScan).isNull()
        assertThat(vm.state.value.draft.make).isEqualTo("Rheem")
        assertThat(vm.state.value.draft.serial).isEqualTo("1234567890")
    }

    @AcceptanceCriterion("AC-C32")
    @Test fun serialFillsManufacturedOnWithANoteAndTheHomeownerCanOverride() = runTest {
        home()
        val vm = made(items, homes, homeId, null, SavedStateHandle())
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.Change(vm.state.value.draft.copy(make = "Rheem", serial = "0415123456")))
        assertThat(vm.state.value.draft.manufacturedOn).isEqualTo("2015-04-01")
        assertThat(vm.state.value.decodedRule).contains("Rheem")

        // A corrected serial re-decodes while the date is still the decoded one.
        vm.onEvent(ItemEditEvent.Change(vm.state.value.draft.copy(serial = "0618123456")))
        assertThat(vm.state.value.draft.manufacturedOn).isEqualTo("2018-06-01")

        // A hand-picked date wins and the note goes away; later serial edits leave it alone.
        vm.onEvent(ItemEditEvent.Change(vm.state.value.draft.copy(manufacturedOn = "2016-01-15")))
        assertThat(vm.state.value.decodedRule).isNull()
        vm.onEvent(ItemEditEvent.Change(vm.state.value.draft.copy(serial = "0415123456")))
        assertThat(vm.state.value.draft.manufacturedOn).isEqualTo("2016-01-15")
    }

    @AcceptanceCriterion("AC-B11")
    @Test fun noModelFromOcrFallsBackToServerVision() = runTest {
        home()
        plateLines = listOf("50 GAL")
        visionResult = com.kurtz.homerecord.core.company.ApiResult.Ok(com.kurtz.homerecord.core.lookup.PlateReading("Rheem", "XG50T06", null, null))
        val vm = made(items, homes, homeId, null, SavedStateHandle())
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.TakePhoto(PhotoKind.DATA_PLATE))
        val path = checkNotNull(checkNotNull(vm.state.value.photoLaunch).target).path
        media.files += path
        vm.onEvent(ItemEditEvent.PhotoCaptured(PhotoKind.DATA_PLATE, path))
        val scan = checkNotNull(vm.state.first { it.plateScan != null }.plateScan)
        assertThat(scan.make).isEqualTo("Rheem")
        assertThat(scan.model).isEqualTo("XG50T06")
        assertThat(scan.target).isEqualTo(com.kurtz.homerecord.core.model.PlateField.SERIAL)
    }

    @AcceptanceCriterion("AC-B11")
    @Test fun offlineVisionKeepsTheOcrResultAndSkipsWhenOcrFoundTheModel() = runTest {
        home()
        plateLines = listOf("50 GAL")
        val vm = made(items, homes, homeId, null, SavedStateHandle())
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.TakePhoto(PhotoKind.DATA_PLATE))
        val path = checkNotNull(checkNotNull(vm.state.value.photoLaunch).target).path
        media.files += path
        vm.onEvent(ItemEditEvent.PhotoCaptured(PhotoKind.DATA_PLATE, path))
        val scan = checkNotNull(vm.state.first { it.plateScan != null }.plateScan)
        assertThat(scan.model).isEmpty()
        assertThat(scan.lines).containsExactly("50 GAL")
    }

    @AcceptanceCriterion("AC-C31")
    @Test fun unreadablePlateOpensNoSheet() = runTest {
        home()
        plateLines = emptyList()
        val vm = made(items, homes, homeId, null, SavedStateHandle())
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.TakePhoto(PhotoKind.DATA_PLATE))
        val path = checkNotNull(checkNotNull(vm.state.value.photoLaunch).target).path
        media.files += path
        vm.onEvent(ItemEditEvent.PhotoCaptured(PhotoKind.DATA_PLATE, path))
        assertThat(vm.state.value.plateScan).isNull()
        assertThat(vm.state.value.scanning).isFalse()
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun photosAreAddedRemovedAndStoredWithTheItem() = runTest {
        home()
        val handle = SavedStateHandle()
        val vm = made(items, homes, homeId, null, handle)
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.PickSubtype("furnace"))
        vm.onEvent(ItemEditEvent.PickRoom(PseudoRooms.WHOLE_HOUSE))

        vm.onEvent(ItemEditEvent.TakePhoto(PhotoKind.DATA_PLATE))
        val launch = checkNotNull(vm.state.value.photoLaunch)
        assertThat(launch.target).isNotNull()
        vm.onEvent(ItemEditEvent.PhotoLaunchHandled)
        assertThat(vm.state.value.photoLaunch).isNull()
        media.files += checkNotNull(launch.target).path
        vm.onEvent(ItemEditEvent.PhotoCaptured(PhotoKind.DATA_PLATE, checkNotNull(launch.target).path))
        // A cancelled camera leaves no file, so no photo is added.
        vm.onEvent(ItemEditEvent.PhotoCaptured(PhotoKind.ITEM, "/media/none.jpg"))
        vm.onEvent(ItemEditEvent.PhotoPicked(PhotoKind.ITEM, "content://picked/1"))
        vm.onEvent(ItemEditEvent.PhotoPicked(PhotoKind.ITEM, "content://broken"))
        assertThat(vm.state.value.draft.photos.map { it.kind }).containsExactly(PhotoKind.DATA_PLATE, PhotoKind.ITEM).inOrder()

        // The photos ride along with the draft through process death.
        val revived = made(items, homes, homeId, null, SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        revived.state.first { !it.loading }
        assertThat(revived.state.value.draft.photos).hasSize(2)

        vm.onEvent(ItemEditEvent.Save)
        val id = checkNotNull(vm.state.first { it.savedItemId != null }.savedItemId)
        assertThat(db.photoDao().listForItem(id).map { PhotoKind.fromTagsJson(it.tagsJson) })
            .containsExactly(PhotoKind.DATA_PLATE, PhotoKind.ITEM)

        val edit = made(items, homes, homeId, id, SavedStateHandle())
        edit.state.first { !it.loading }
        edit.onEvent(ItemEditEvent.RemovePhoto(edit.state.value.draft.photos.first().path))
        edit.onEvent(ItemEditEvent.Save)
        edit.state.first { it.savedItemId != null }
        assertThat(db.photoDao().listForItem(id)).hasSize(1)
    }

    @AcceptanceCriterion("AC-C30")
    @Test
    fun removingAnUnsavedPhotoDeletesItsFile() = runTest {
        home()
        val vm = made(items, homes, homeId, null, SavedStateHandle())
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.PhotoPicked(PhotoKind.ITEM, "content://picked/9"))
        val path = vm.state.value.draft.photos.single().path
        assertThat(media.files).contains(path)
        vm.onEvent(ItemEditEvent.RemovePhoto(path))
        assertThat(media.files).doesNotContain(path)
        assertThat(vm.state.value.draft.photos).isEmpty()
    }

    @AcceptanceCriterion("AC-C30")
    @Test
    fun closingWithoutSavingDeletesOnlyTheUnsavedPhotoFiles() = runTest {
        home()
        val store = ViewModelStore()
        val vm = provide(store, null)
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.PhotoPicked(PhotoKind.ITEM, "content://picked/1"))
        val draftPhoto = vm.state.value.draft.photos.single().path
        store.clear()
        assertThat(media.files).doesNotContain(draftPhoto)

        // A saved item keeps its photos when its form closes, and an edit that adds one and walks away loses only the new file.
        val saved = provide(ViewModelStore(), null)
        saved.state.first { !it.loading }
        saved.onEvent(ItemEditEvent.PickSubtype("furnace"))
        saved.onEvent(ItemEditEvent.PickRoom(PseudoRooms.WHOLE_HOUSE))
        saved.onEvent(ItemEditEvent.PhotoPicked(PhotoKind.ITEM, "content://picked/2"))
        val kept = saved.state.value.draft.photos.single().path
        saved.onEvent(ItemEditEvent.Save)
        val id = checkNotNull(saved.state.first { it.savedItemId != null }.savedItemId)

        val editStore = ViewModelStore()
        val edit = provide(editStore, id)
        edit.state.first { !it.loading }
        edit.onEvent(ItemEditEvent.PhotoPicked(PhotoKind.ITEM, "content://picked/3"))
        val added = edit.state.value.draft.photos.last().path
        editStore.clear()
        assertThat(media.files).contains(kept)
        assertThat(media.files).doesNotContain(added)
    }

    private fun provide(store: ViewModelStore, itemId: String?): ItemEditViewModel =
        ViewModelProvider.create(store, viewModelFactory { initializer { made(items, homes, homeId, itemId, SavedStateHandle()) } })[ItemEditViewModel::class]

    // AC-C31: a data plate shot whose result lands after process death is still filed as a plate and read (cr:photo-pending-remember).
    @AcceptanceCriterion("AC-C31")
    @Test fun cameraResultAfterProcessDeathKeepsThePlateKindAndScans() = runTest {
        home()
        val handle = SavedStateHandle()
        val vm = made(items, homes, homeId, null, handle)
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.TakePhoto(PhotoKind.DATA_PLATE))
        val path = checkNotNull(checkNotNull(vm.state.value.photoLaunch).target).path
        vm.onEvent(ItemEditEvent.PhotoLaunchHandled)
        media.files += path

        val revived = made(items, homes, homeId, null, SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        revived.state.first { !it.loading }
        revived.onEvent(ItemEditEvent.CameraResult(ok = true))
        assertThat(revived.state.value.draft.photos.map { it.kind to it.path }).containsExactly(PhotoKind.DATA_PLATE to path)
        assertThat(checkNotNull(revived.state.first { it.plateScan != null }.plateScan).model).isEqualTo("XG50T06EC36U1")
        // The request is answered once; a stray second result adds nothing.
        revived.onEvent(ItemEditEvent.CameraResult(ok = true))
        assertThat(revived.state.value.draft.photos).hasSize(1)
    }

    @AcceptanceCriterion("AC-C30")
    @Test fun cancelledCameraDiscardsTheFileAndGalleryKeepsItsKindAcrossRecreation() = runTest {
        home()
        val handle = SavedStateHandle()
        val vm = made(items, homes, homeId, null, handle)
        vm.state.first { !it.loading }
        vm.onEvent(ItemEditEvent.TakePhoto(PhotoKind.ITEM))
        val path = checkNotNull(checkNotNull(vm.state.value.photoLaunch).target).path
        media.files += path
        vm.onEvent(ItemEditEvent.CameraResult(ok = false))
        assertThat(media.files).doesNotContain(path)
        assertThat(vm.state.value.draft.photos).isEmpty()

        vm.onEvent(ItemEditEvent.ChoosePhoto(PhotoKind.DATA_PLATE))
        val revived = made(items, homes, homeId, null, SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
        revived.state.first { !it.loading }
        revived.onEvent(ItemEditEvent.GalleryResult("content://picked/3"))
        assertThat(revived.state.value.draft.photos.single().kind).isEqualTo(PhotoKind.DATA_PLATE)

        // Backing out of the gallery adds nothing.
        revived.onEvent(ItemEditEvent.ChoosePhoto(PhotoKind.ITEM))
        revived.onEvent(ItemEditEvent.GalleryResult(null))
        assertThat(revived.state.value.draft.photos).hasSize(1)
    }
}

private class FakeMediaFiles : MediaFiles {
    val files = mutableSetOf<String>()
    private var n = 0
    override fun newCaptureTarget() = CaptureTarget("content://capture/${++n}", "/media/capture-$n.jpg")
    override suspend fun import(uri: String): String? = if (uri.contains("broken")) null else "/media/imported-${++n}.jpg".also { files += it }
    override fun exists(path: String) = path in files
    override fun discard(path: String) { files -= path }
}
