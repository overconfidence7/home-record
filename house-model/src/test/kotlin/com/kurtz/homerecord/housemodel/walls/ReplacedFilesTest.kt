package com.kurtz.homerecord.housemodel.walls

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.WallEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.housemodel.overlays.OverlayDraft
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.OverlayPoint
import com.kurtz.homerecord.housemodel.overlays.OverlayRepository
import com.kurtz.homerecord.housemodel.overlays.OverlaySave
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// R10c (AC-C17, AC-C26): files of replaced or removed wall and overlay photos are deleted once no live row uses them.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReplacedFilesTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var photos: WallPhotoRepository
    private lateinit var overlays: OverlayRepository
    private val deleted = mutableListOf<String>()
    private var clock = 1_000L
    private val outline = WallOutline.Initial

    @Before fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        val files = ReplacedFiles(db) { deleted += it }
        photos = WallPhotoRepository(db, now = { clock++ }, sweep = files::sweep)
        overlays = OverlayRepository(db, now = { clock++ }, sweep = files::sweep)
        db.wallDao().upsert(WallEntity(id = "w-north", roomId = "kitchen", edgeIndex = 0, orientation = "N", lengthIn = 168, heightIn = 96, createdAt = 1, updatedAt = 1))
        db.wallDao().upsert(WallEntity(id = "w-east", roomId = "kitchen", edgeIndex = 1, orientation = "E", lengthIn = 144, heightIn = 96, createdAt = 1, updatedAt = 1))
    }

    @After fun tearDown() = db.close()

    @Test
    @AcceptanceCriterion("AC-C17")
    fun retakingAWallPhotoDeletesTheOldPhotoAndItsStraightenedCopy() = runTest {
        photos.save("w-north", WallStage.FINISHED, "media/a.jpg", "media/a-wall.jpg", outline)
        assertThat(deleted).isEmpty()

        photos.save("w-north", WallStage.FINISHED, "media/b.jpg", "media/b-wall.jpg", outline)

        assertThat(deleted).containsExactly("media/a.jpg", "media/a-wall.jpg")
    }

    @Test
    @AcceptanceCriterion("AC-C17")
    fun aPhotoAtAnotherStageIsLeftAlone() = runTest {
        photos.save("w-north", WallStage.ROUGH_IN, "media/r.jpg", "media/r-wall.jpg", outline)
        photos.save("w-north", WallStage.FINISHED, "media/f.jpg", "media/f-wall.jpg", outline)

        assertThat(deleted).isEmpty()
    }

    @Test
    @AcceptanceCriterion("AC-C17")
    fun aPhotoSharedWithTheOtherPartOfASplitWallIsKept() = runTest {
        photos.save("w-north", WallStage.FINISHED, "media/a.jpg", "media/a-wall.jpg", outline)
        // The split copies the row to the new part with its own crop and texture, same photo as taken (R10a).
        photos.save("w-east", WallStage.FINISHED, "media/a.jpg", "media/a-east-wall.jpg", outline)

        photos.save("w-north", WallStage.FINISHED, "media/b.jpg", "media/b-wall.jpg", outline)

        assertThat(deleted).containsExactly("media/a-wall.jpg")
    }

    @Test
    @AcceptanceCriterion("AC-C17")
    fun aPhotoWhoseCopyIsItselfIsDeletedOnce() = runTest {
        photos.save("w-north", WallStage.FINISHED, "media/a.jpg", "media/a.jpg", outline)
        photos.save("w-north", WallStage.FINISHED, "media/b.jpg", null, outline)

        assertThat(deleted).containsExactly("media/a.jpg")
    }

    @Test
    @AcceptanceCriterion("AC-C17")
    fun deletingAWallPhotoDeletesItsFiles() = runTest {
        val id = checkNotNull(photos.save("w-north", WallStage.ROUGH_IN, "media/r.jpg", "media/r-wall.jpg", outline))

        photos.delete(id)

        assertThat(deleted).containsExactly("media/r.jpg", "media/r-wall.jpg")
    }

    @Test
    @AcceptanceCriterion("AC-C26")
    fun replacingOrRemovingAnOverlayPhotoDeletesTheOldFile() = runTest {
        val draft = OverlayDraft(OverlayKind.WATER_COLD, listOf(OverlayPoint(0.3f, 0.2f)), WallStage.ROUGH_IN, photoPath = "media/o1.jpg")
        val id = (overlays.addOnWall("home", "w-north", draft) as OverlaySave.Saved).id

        overlays.update(id, draft.copy(label = "Sink supply"))
        assertThat(deleted).isEmpty()

        overlays.update(id, draft.copy(photoPath = "media/o2.jpg"))
        assertThat(deleted).containsExactly("media/o1.jpg")

        overlays.update(id, draft.copy(photoPath = null))
        assertThat(deleted).containsExactly("media/o1.jpg", "media/o2.jpg").inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C26")
    fun deletingAnOverlayDeletesItsPhoto() = runTest {
        val draft = OverlayDraft(OverlayKind.ELECTRICAL_WIRE, listOf(OverlayPoint(0.1f, 0.5f)), WallStage.ROUGH_IN, photoPath = "media/o.jpg")
        val id = (overlays.addOnWall("home", "w-north", draft) as OverlaySave.Saved).id

        overlays.delete(id)

        assertThat(deleted).containsExactly("media/o.jpg")
    }

    @Test
    @AcceptanceCriterion("AC-C26")
    fun aFailedDeleteDoesNotFailTheSave() = runTest {
        val failing = WallPhotoRepository(db, now = { clock++ }, sweep = ReplacedFiles(db) { error("disk") }::sweep)
        failing.save("w-north", WallStage.FINISHED, "media/a.jpg", "media/a-wall.jpg", outline)

        val id = failing.save("w-north", WallStage.FINISHED, "media/b.jpg", "media/b-wall.jpg", outline)

        assertThat(id).isNotNull()
    }
}
