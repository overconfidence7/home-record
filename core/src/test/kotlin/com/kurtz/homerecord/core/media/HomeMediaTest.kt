package com.kurtz.homerecord.core.media

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.data.WallEntity
import com.kurtz.homerecord.core.data.WallPhotoEntity
import com.kurtz.homerecord.core.photo.OnPhone
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-H15, AC-C17 (T075a): a signed-in home's item and wall photo files go to R2 and come down on its other phones
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class HomeMediaTest {
    private lateinit var db: HomeRecordDatabase
    private val queued = mutableListOf<List<String>>()
    private val onPhone = mutableSetOf<String>()
    private val fetchResults = mutableMapOf<String, OnPhone>()
    private val fetched = mutableListOf<String>()
    private lateinit var media: HomeMedia

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        media = HomeMedia(
            db,
            enqueue = { tenant, entity, id, filename, path, mime ->
                assertThat(tenant).isEqualTo(TenantRef(TenantKind.Home, "h1"))
                queued += listOf(entity, id, filename, path, mime)
            },
            isOnPhone = { it != null && it in onPhone },
            fetchPhoto = { id -> fetched += id; fetchResults[id] ?: OnPhone.Failed },
        )
        runBlocking {
            db.homeDao().upsert(HomeEntity(id = "h1", name = "Maple Street", createdAt = 1, updatedAt = 1))
            db.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", sortOrder = 0, name = "Main", createdAt = 1, updatedAt = 1))
            db.roomDao().upsert(RoomEntity(id = "r1", floorId = "f1", name = "Kitchen", createdAt = 1, updatedAt = 1))
            db.wallDao().upsert(WallEntity(id = "w1", roomId = "r1", edgeIndex = 0, orientation = "N", lengthIn = 144, heightIn = 96, createdAt = 1, updatedAt = 1))
        }
    }

    @After fun tearDown() = db.close()

    private fun photo(id: String, path: String, r2Key: String? = null, homeId: String = "h1") =
        PhotoEntity(id = id, homeId = homeId, itemId = "i1", path = path, r2Key = r2Key, takenAt = 1, createdAt = 1, updatedAt = 1)

    @Test fun queuesItemPhotosOnThisPhoneWithNoKeyYet() = runBlocking<Unit> {
        db.photoDao().upsert(photo("p1", "/media/p1.jpg"))
        db.photoDao().upsert(photo("p2", "/media/p2.png", r2Key = "h1/photo/p2/p2.png"))
        db.photoDao().upsert(photo("p3", ""))
        db.photoDao().upsert(photo("p4", "/media/p4.jpg", homeId = "h2"))
        onPhone += listOf("/media/p1.jpg", "/media/p2.png", "/media/p4.jpg")

        val pass = media.afterSync("h1")

        assertThat(queued).containsExactly(listOf("photo", "p1", "p1.jpg", "/media/p1.jpg", "image/jpeg"))
        assertThat(pass.queued).isEqualTo(1)
    }

    @Test fun queuesWallPhotoAndItsStraightenedCopyUntilEachHasAKey() = runBlocking<Unit> {
        db.wallPhotoDao().upsert(
            WallPhotoEntity(id = "wp1", wallId = "w1", roomId = "r1", originalPath = "/media/wp1.png", texturePath = "/media/wp1-wall.jpg", takenAt = 1, createdAt = 1, updatedAt = 1),
        )
        // An imported photo with no room recorded is found through its wall; its copy is already in R2.
        db.wallPhotoDao().upsert(
            WallPhotoEntity(id = "wp2", wallId = "w1", originalPath = "/media/wp2.jpg", texturePath = "/media/wp2-wall.jpg", textureR2Key = "h1/wall_photo/wp2/texture.jpg", takenAt = 1, createdAt = 2, updatedAt = 2),
        )
        onPhone += listOf("/media/wp1.png", "/media/wp1-wall.jpg", "/media/wp2.jpg", "/media/wp2-wall.jpg")

        media.afterSync("h1")

        assertThat(queued).containsExactly(
            listOf("wall_photo", "wp1", "original.png", "/media/wp1.png", "image/png"),
            listOf("wall_photo", "wp1", "texture.jpg", "/media/wp1-wall.jpg", "image/jpeg"),
            listOf("wall_photo", "wp2", "original.jpg", "/media/wp2.jpg", "image/jpeg"),
        ).inOrder()
    }

    @Test fun deletedPhotosAndJobPhotosStayOut() = runBlocking<Unit> {
        db.photoDao().upsert(photo("p1", "/media/p1.jpg").copy(deletedAt = 5))
        db.photoDao().upsert(photo("p2", "/media/p2.jpg").copy(jobId = "j1"))
        db.wallPhotoDao().upsert(WallPhotoEntity(id = "wp1", wallId = "w1", roomId = "r1", originalPath = "/media/wp1.jpg", takenAt = 1, createdAt = 1, updatedAt = 1, deletedAt = 5))
        onPhone += listOf("/media/p1.jpg", "/media/p2.jpg", "/media/wp1.jpg")

        assertThat(media.afterSync("h1")).isEqualTo(HomeMedia.Pass(queued = 0, fetched = 0))
        assertThat(queued).isEmpty()
    }

    @Test fun fetchesPhotosTakenOnAnotherPhoneAndStopsWhenOffline() = runBlocking<Unit> {
        db.photoDao().upsert(photo("p1", "", r2Key = "h1/photo/p1/p1.jpg"))
        db.photoDao().upsert(photo("p2", "", r2Key = "h1/photo/p2/p2.jpg").copy(createdAt = 2))
        db.photoDao().upsert(photo("p3", "/media/p3.jpg", r2Key = "h1/photo/p3/p3.jpg").copy(createdAt = 3))
        onPhone += "/media/p3.jpg"
        fetchResults["p1"] = OnPhone.Ready
        fetchResults["p2"] = OnPhone.Ready

        assertThat(media.afterSync("h1").fetched).isEqualTo(2)
        assertThat(fetched).containsExactly("p1", "p2").inOrder()

        fetched.clear()
        fetchResults["p1"] = OnPhone.NoNetwork
        assertThat(media.afterSync("h1").fetched).isEqualTo(0)
        assertThat(fetched).containsExactly("p1")
        assertThat(queued).isEmpty()
    }
}
