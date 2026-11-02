package com.kurtz.homerecord.housemodel.walls

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.Sources
import com.kurtz.homerecord.core.data.WallEntity
import com.kurtz.homerecord.core.data.WallPhotoEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-C17: each wall holds a photo per stage (Framing, Rough-in, Insulation, Finished).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WallPhotoRepositoryTest {
    private lateinit var db: HomeRecordDatabase
    private lateinit var repo: WallPhotoRepository
    private val changes = mutableListOf<String>()
    private var clock = 1_000L
    private val outline = WallOutline(PhotoPoint(0.2f, 0.15f), PhotoPoint(0.8f, 0.2f), PhotoPoint(0.9f, 0.85f), PhotoPoint(0.1f, 0.8f))

    @Before fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        repo = WallPhotoRepository(db, now = { clock++ }, onChange = { e, id -> changes += "$e/$id" })
        db.wallDao().upsert(WallEntity(id = "w-north", roomId = "kitchen", edgeIndex = 0, orientation = "N", lengthIn = 168, heightIn = 96, createdAt = 1, updatedAt = 1))
    }

    @After fun tearDown() = db.close()

    @Test
    @AcceptanceCriterion("AC-C17")
    fun aWallKeepsOnePhotoPerStage() = runTest {
        val roughIn = repo.save("w-north", WallStage.ROUGH_IN, "media/r.jpg", "media/r-tex.jpg", outline, takenAt = 10)
        val finished = repo.save("w-north", WallStage.FINISHED, "media/f.jpg", "media/f-tex.jpg", WallOutline.Initial, takenAt = 20)

        val byStage = repo.observeByStage("w-north").first()
        assertThat(byStage.keys).containsExactly(WallStage.ROUGH_IN, WallStage.FINISHED)
        assertThat(byStage.getValue(WallStage.ROUGH_IN).id).isEqualTo(roughIn)
        assertThat(byStage.getValue(WallStage.ROUGH_IN).outline).isEqualTo(outline)
        assertThat(byStage.getValue(WallStage.ROUGH_IN).texturePath).isEqualTo("media/r-tex.jpg")
        assertThat(byStage.getValue(WallStage.FINISHED).id).isEqualTo(finished)
        assertThat(byStage.getValue(WallStage.FINISHED).takenAt).isEqualTo(20)
        assertThat(changes).containsExactly("wall_photo/$roughIn", "wall_photo/$finished").inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C17")
    fun photographingTheSameStageAgainReplacesTheEarlierPhoto() = runTest {
        val first = checkNotNull(repo.save("w-north", WallStage.FINISHED, "media/a.jpg", "media/a-tex.jpg", outline))
        val second = checkNotNull(repo.save("w-north", WallStage.FINISHED, "media/b.jpg", "media/b-tex.jpg", outline))

        assertThat(repo.byStage("w-north").getValue(WallStage.FINISHED).id).isEqualTo(second)
        assertThat(db.wallPhotoDao().listFor("w-north").map { it.id }).containsExactly(second)
        assertThat(db.wallPhotoDao().get(first)).isNull()
        // Both rows reach the outbox: the new photo and the removal of the old one.
        assertThat(changes).containsExactly("wall_photo/$first", "wall_photo/$second", "wall_photo/$first").inOrder()
    }

    @Test
    @AcceptanceCriterion("AC-C17")
    fun fieldLogPhotosAreMarkedAsTheirs() = runTest {
        val fl = WallPhotoRepository(db, source = Sources.FIELD_LOG, now = { clock++ })
        val id = checkNotNull(fl.save("w-north", WallStage.BehindTheWall, "media/r.jpg", null, outline))
        val row = checkNotNull(db.wallPhotoDao().get(id))
        assertThat(row.source).isEqualTo(Sources.FIELD_LOG)
        assertThat(row.stage).isEqualTo("ROUGH_IN")
    }

    @Test
    fun aDeletedWallTakesNoPhotoAndDeleteRemovesOne() = runTest {
        assertThat(repo.save("gone", WallStage.FINISHED, "media/x.jpg", null, outline)).isNull()
        val id = checkNotNull(repo.save("w-north", WallStage.FRAMING, "media/x.jpg", null, outline))
        repo.delete(id)
        assertThat(repo.byStage("w-north")).isEmpty()
        repo.delete("unknown")
        assertThat(changes).containsExactly("wall_photo/$id", "wall_photo/$id").inOrder()
    }

    @Test
    fun twoPhonesPhotographingTheSameStageShowTheLaterShot() {
        fun row(id: String, takenAt: Long) = WallPhotoEntity(id = id, wallId = "w", stage = "FINISHED", originalPath = "$id.jpg", takenAt = takenAt, createdAt = 1, updatedAt = 1)
        val latest = WallPhotoRepository.latestByStage(listOf(row("later", 50), row("earlier", 40)))
        assertThat(latest.getValue(WallStage.FINISHED).id).isEqualTo("later")
        assertThat(latest.getValue(WallStage.FINISHED).outline).isNull()
    }

    @Test
    @AcceptanceCriterion("AC-C17")
    fun roomWallsFollowNewPhotosAndRemovedWalls() = runTest {
        db.wallDao().upsert(WallEntity(id = "w-east", roomId = "kitchen", edgeIndex = 1, orientation = "E", lengthIn = 144, heightIn = 96, createdAt = 2, updatedAt = 2))
        db.wallDao().upsert(WallEntity(id = "w-den", roomId = "den", edgeIndex = 0, orientation = "N", lengthIn = 120, heightIn = 96, createdAt = 3, updatedAt = 3))
        assertThat(repo.observeRoomWalls("kitchen").first().map { it.id }).containsExactly("w-north", "w-east").inOrder()

        repo.save("w-east", WallStage.FINISHED, "/m/e.jpg", "/m/e-tex.jpg", outline)
        repo.save("w-den", WallStage.FINISHED, "/m/d.jpg", null, outline)
        val walls = repo.observeRoomWalls("kitchen").first()
        assertThat(walls.single { it.id == "w-east" }.thumbnails).containsExactly(WallStage.FINISHED, "/m/e-tex.jpg")
        assertThat(walls.single { it.id == "w-north" }.thumbnails).isEmpty()

        db.wallDao().softDelete("w-east", 99)
        assertThat(repo.observeRoomWalls("kitchen").first().map { it.id }).containsExactly("w-north")
    }

    // T048d: a new photo is handed to the upload queue with both files; the room page asks for the missing files of
    // the photos it shows (the newest per wall and stage) after it has its rows, and a failed fetch leaves the page as is.
    @Test
    @AcceptanceCriterion("AC-C17")
    fun newPhotosGoToTheCloudAndPulledOnesAreFetched() = runTest {
        val uploaded = mutableListOf<WallPhotoEntity>()
        val asked = CompletableDeferred<List<String>>()
        val cloud = WallPhotoRepository(
            db, Sources.FIELD_LOG, now = { clock++ }, upload = { uploaded += it },
            fetchMissing = { rows -> asked.complete(rows.map { it.id }); error("no network") },
        )
        val id = checkNotNull(cloud.save("w-north", WallStage.ROUGH_IN, "media/r.jpg", "media/r-tex.jpg", outline))
        assertThat(uploaded.map { Triple(it.id, it.originalPath, it.texturePath) }).containsExactly(Triple(id, "media/r.jpg", "media/r-tex.jpg"))

        // A Finished photo pulled from the other phone, and an older Rough-in one that the newer replaces on screen.
        db.wallPhotoDao().upsert(WallPhotoEntity(id = "pulled", wallId = "w-north", stage = "FINISHED", originalPath = "", r2Key = "k", takenAt = 5, createdAt = 5, updatedAt = 5))
        db.wallPhotoDao().upsert(WallPhotoEntity(id = "older", wallId = "w-north", stage = "ROUGH_IN", originalPath = "", r2Key = "k2", takenAt = 0, createdAt = 0, updatedAt = 0))

        val walls = cloud.observeRoomWalls("kitchen").first()
        assertThat(walls.single().thumbnails.keys).containsExactly(WallStage.ROUGH_IN, WallStage.FINISHED)
        assertThat(walls.single().thumbnails[WallStage.FINISHED]).isEmpty()

        backgroundScope.launch { cloud.observeRoomWalls("kitchen").collect {} }
        val ids = withContext(Dispatchers.Default) { withTimeout(10_000) { asked.await() } }
        assertThat(ids).containsExactly(id, "pulled")
    }
}
