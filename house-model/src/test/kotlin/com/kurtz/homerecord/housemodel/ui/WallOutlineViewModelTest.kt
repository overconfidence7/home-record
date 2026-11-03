package com.kurtz.homerecord.housemodel.ui

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemodel.NewHome
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.walls.PhotoPoint
import com.kurtz.homerecord.housemodel.walls.WallOutline
import com.kurtz.homerecord.housemodel.walls.WallPhotoRepository
import com.kurtz.homerecord.housemodel.walls.WallSide
import com.kurtz.homerecord.housemodel.walls.WallStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
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

// AC-C18: the outline screen starts with the handles 10% in, drags them, and Use straightens and saves the wall photo.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WallOutlineViewModelTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var homes: HomeRepository
    private lateinit var wallPhotos: WallPhotoRepository
    private val media = FakeWallMedia()
    private val maker = FakeTextureMaker()
    private val created = mutableListOf<WallOutlineViewModel>()
    private lateinit var northWall: String

    private fun test(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
        runBlocking { block() }
    }

    @Before fun setUp() = test {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        homes = HomeRepository(db, context.resources::getString)
        wallPhotos = WallPhotoRepository(db)
        val homeId = homes.createHome(NewHome("Maple St", null, HouseType.COLONIAL, Foundation.BASEMENT, false))
        val floor = homes.observeFloors(homeId).first { it.isNotEmpty() }.single { it.name == "Main" }
        homes.savePlan(floor.id, listOf(PlanRoom.rectangle("k", "Kitchen", "KITCHEN", 0, 0, 168, 144)))
        northWall = wallPhotos.observeRoomWalls("k").first { it.size == 4 }.first { it.side == WallSide.NORTH }.id
    }

    @After fun tearDown() {
        runBlocking { created.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() } }
        db.close()
        Dispatchers.resetMain()
    }

    private fun vm(stage: WallStage = WallStage.FINISHED, saved: SavedStateHandle = SavedStateHandle(), wallId: String = northWall) =
        WallOutlineViewModel(wallPhotos, maker, media, "k", wallId, stage, "media/7.jpg", saved).also { created += it }

    private suspend fun loaded(model: WallOutlineViewModel) = withTimeout(10_000) { model.state.first { !it.loading } }

    @Test
    @AcceptanceCriterion("AC-C18")
    fun opensOnThePhotoWithTheHandlesTenPercentIn() = test {
        val state = loaded(vm())
        assertThat(state.outline).isEqualTo(WallOutline.Initial)
        assertThat(state.outline.topLeft).isEqualTo(PhotoPoint(0.1f, 0.1f))
        assertThat(state.photoWidth to state.photoHeight).isEqualTo(4032 to 3024)
        assertThat(state.wall?.lengthIn).isEqualTo(168)
        assertThat(state.wall?.heightIn).isGreaterThan(0)
        assertThat(state.error).isNull()
    }

    @Test
    @AcceptanceCriterion("AC-C18")
    fun draggingAHandleMovesThatCornerAndShowsTheLoupeUntilLetGo() = test {
        val saved = SavedStateHandle()
        val model = vm(saved = saved)
        loaded(model)
        model.onEvent(WallOutlineEvent.DragStart(1))
        model.onEvent(WallOutlineEvent.Drag(1, PhotoPoint(0.95f, 0.04f)))
        assertThat(model.state.value.dragging).isEqualTo(1)
        assertThat(model.state.value.outline.topRight).isEqualTo(PhotoPoint(0.95f, 0.04f))
        model.onEvent(WallOutlineEvent.Drag(1, PhotoPoint(1.4f, -0.2f)))
        assertThat(model.state.value.outline.topRight).isEqualTo(PhotoPoint(1f, 0f))
        model.onEvent(WallOutlineEvent.DragEnd)
        assertThat(model.state.value.dragging).isNull()

        // The corners come back after process death.
        val restored = loaded(vm(saved = saved))
        assertThat(restored.outline.topRight).isEqualTo(PhotoPoint(1f, 0f))
    }

    @Test
    @AcceptanceCriterion("AC-C18")
    fun useStraightensThePhotoAndSavesItAtTheStage() = test {
        val model = vm(WallStage.ROUGH_IN)
        loaded(model)
        model.onEvent(WallOutlineEvent.Drag(0, PhotoPoint(0.2f, 0.15f)))
        model.onEvent(WallOutlineEvent.Use)
        val done = withTimeout(10_000) { model.state.first { it.done } }
        assertThat(done.error).isNull()
        val (path, outline, size) = maker.made.single()
        assertThat(path).isEqualTo("media/7.jpg")
        assertThat(outline.topLeft).isEqualTo(PhotoPoint(0.2f, 0.15f))
        assertThat(size.first).isEqualTo(168)
        val photo = wallPhotos.byStage(northWall).getValue(WallStage.ROUGH_IN)
        assertThat(photo.originalPath).isEqualTo("media/7.jpg")
        assertThat(photo.texturePath).isEqualTo("media/7-wall.jpg")
        assertThat(photo.outline?.topLeft).isEqualTo(PhotoPoint(0.2f, 0.15f))
        // Leaving after Use keeps the saved photo.
        model.onEvent(WallOutlineEvent.Back)
        assertThat(media.discarded).isEmpty()
    }

    // R10: Back pressed while Use is still straightening the photo must not delete the photo being saved.
    @Test
    @AcceptanceCriterion("AC-C18")
    fun backWhileUseIsSavingKeepsThePhotoAndTheSaveFinishes() = test {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        maker.gate = gate
        val model = vm()
        loaded(model)
        model.onEvent(WallOutlineEvent.Use)
        assertThat(model.state.value.saving).isTrue()
        model.onEvent(WallOutlineEvent.Back)
        assertThat(media.discarded).isEmpty()
        gate.complete(Unit)
        withTimeout(10_000) { model.state.first { it.done } }
        assertThat(wallPhotos.byStage(northWall).getValue(WallStage.FINISHED).originalPath).isEqualTo("media/7.jpg")
        assertThat(media.discarded).isEmpty()
    }

    @Test
    fun crossedCornersAreNotSaved() = test {
        val model = vm()
        loaded(model)
        model.onEvent(WallOutlineEvent.Drag(0, PhotoPoint(0.95f, 0.95f)))
        model.onEvent(WallOutlineEvent.Use)
        assertThat(model.state.value.error).isEqualTo(WallOutlineError.NotUsable)
        assertThat(maker.made).isEmpty()
        // Moving a handle again clears the message; Reset puts the handles back.
        model.onEvent(WallOutlineEvent.Reset)
        assertThat(model.state.value.error).isNull()
        assertThat(model.state.value.outline).isEqualTo(WallOutline.Initial)
    }

    @Test
    fun aFailedTextureKeepsTheScreenWithTheReason() = test {
        maker.fails = true
        val model = vm()
        loaded(model)
        model.onEvent(WallOutlineEvent.Use)
        val failed = withTimeout(10_000) { model.state.first { it.error != null } }
        assertThat(failed.error).isEqualTo(WallOutlineError.TextureFailed)
        assertThat(failed.saving).isFalse()
        assertThat(failed.done).isFalse()
        assertThat(wallPhotos.byStage(northWall)).isEmpty()
    }

    @Test
    fun backDropsTheNewPhoto() = test {
        val model = vm()
        loaded(model)
        model.onEvent(WallOutlineEvent.Back)
        assertThat(media.discarded).containsExactly("media/7.jpg")
    }

    @Test
    fun anUnreadablePhotoOrAMissingWallSaysSo() = test {
        maker.size = null
        assertThat(loaded(vm()).error).isEqualTo(WallOutlineError.PhotoUnreadable)
        maker.size = com.kurtz.homerecord.housemodel.walls.TextureSize(4032, 3024)
        val gone = vm(wallId = "deleted")
        assertThat(loaded(gone).error).isEqualTo(WallOutlineError.WallGone)
        gone.onEvent(WallOutlineEvent.Use)
        assertThat(maker.made).isEmpty()
    }
}
