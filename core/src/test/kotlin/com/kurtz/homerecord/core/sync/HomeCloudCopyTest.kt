package com.kurtz.homerecord.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.data.WallEntity
import com.kurtz.homerecord.core.data.WallPhotoEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-H15: HM-Account "Delete cloud copy": the server copy goes, the phone signs out and keeps its home, and the next
// sign-in uploads every row again (with the photos' R2 keys cleared so the files go up again too).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeCloudCopyTest {
    private val home = TenantRef(TenantKind.Home, "h1")
    private lateinit var db: HomeRecordDatabase
    private lateinit var engine: SyncEngine
    /** Swapped for a new, empty one when the cloud copy is deleted. */
    private var server = FakeSyncServer()
    private var deleteAnswer: ApiResult<Unit> = ApiResult.Ok(Unit)
    private val deleted = mutableListOf<String>()
    private var signedOut = 0
    private lateinit var cloud: HomeCloudCopy

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        val store = RoomRowStore(db)
        val api = object : SyncApi {
            override suspend fun push(tenant: TenantRef, deviceId: String, ops: List<PushOp>) = server.push(tenant, deviceId, ops)
            override suspend fun pull(tenant: TenantRef, since: Long) = server.pull(tenant, since)
        }
        engine = SyncEngine(api, store, db.outboxDao(), db.syncCursorDao(), db.syncBaseDao(), "phone-a")
        cloud = HomeCloudCopy(
            api = { homeId ->
                deleted += homeId
                deleteAnswer.also { if (it is ApiResult.Ok) server = FakeSyncServer() }
            },
            engine = engine,
            store = store,
            rows = { table, column, values -> store.readWhere(table, column, values) },
            outbox = db.outboxDao(),
            signOut = { signedOut++ },
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun seedSyncedHome() {
        db.homeDao().upsert(HomeEntity(id = "h1", name = "Maple St", createdAt = 1, updatedAt = 1))
        db.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", sortOrder = 0, name = "Main floor", createdAt = 1, updatedAt = 1))
        db.roomDao().upsert(RoomEntity(id = "r1", floorId = "f1", name = "Kitchen", createdAt = 1, updatedAt = 1))
        db.wallDao().upsert(WallEntity(id = "w1", roomId = "r1", edgeIndex = 0, orientation = "N", lengthIn = 150, heightIn = 96, createdAt = 1, updatedAt = 1))
        db.wallPhotoDao().upsert(
            WallPhotoEntity(
                id = "wp1", wallId = "w1", roomId = "r1", originalPath = "/media/wp1.jpg", takenAt = 2,
                r2Key = "h1/wall_photo/wp1/wp1.jpg", textureR2Key = "h1/wall_photo/wp1/wp1-t.jpg", createdAt = 2, updatedAt = 2,
            ),
        )
        db.itemDao().upsert(ItemEntity(id = "i1", homeId = "h1", roomId = "r1", category = "HVAC", subtype = "furnace", name = "Furnace", make = "Carrier", createdAt = 3, updatedAt = 3))
        db.photoDao().upsert(PhotoEntity(id = "p1", homeId = "h1", itemId = "i1", path = "/media/p1.jpg", takenAt = 4, r2Key = "h1/photo/p1/p1.jpg", createdAt = 4, updatedAt = 4))
        for ((entity, id) in listOf("home" to "h1", "floor" to "f1", "room" to "r1", "wall" to "w1", "wall_photo" to "wp1", "item" to "i1", "photo" to "p1")) {
            engine.recordChange(home, entity, id)
        }
        assertThat(engine.sync(home)).isInstanceOf(ApiResult.Ok::class.java)
        assertThat(server.rows(home)).hasSize(7)
    }

    @Test
    @AcceptanceCriterion("AC-H15")
    fun deleteSignsOutKeepsTheHomeAndTheNextSignInUploadsItAll() = runTest {
        seedSyncedHome()

        assertThat(cloud.delete("h1")).isEqualTo(ApiResult.Ok(Unit))

        assertThat(deleted).containsExactly("h1")
        assertThat(signedOut).isEqualTo(1)
        // The home is still on the phone, R2 keys cleared so the files are uploaded again.
        assertThat(db.itemDao().get("i1")?.name).isEqualTo("Furnace")
        assertThat(db.photoDao().get("p1")?.r2Key).isNull()
        assertThat(db.photoDao().get("p1")?.path).isEqualTo("/media/p1.jpg")
        val wallPhoto = db.wallPhotoDao().get("wp1")
        assertThat(wallPhoto?.r2Key).isNull()
        assertThat(wallPhoto?.textureR2Key).isNull()
        assertThat(db.syncCursorDao().get("home", "h1")).isNull()

        // Signing in again: the empty server gets every row with all its fields.
        assertThat(engine.sync(home)).isInstanceOf(ApiResult.Ok::class.java)
        val rows = server.rows(home).associateBy { it.entity + "/" + it.entityId }
        assertThat(rows.keys).containsExactly("home/h1", "floor/f1", "room/r1", "wall/w1", "wall_photo/wp1", "item/i1", "photo/p1")
        assertThat(rows.getValue("item/i1").row["make"]?.jsonPrimitive?.content).isEqualTo("Carrier")
        assertThat(rows.getValue("room/r1").row["name"]?.jsonPrimitive?.content).isEqualTo("Kitchen")
        assertThat(rows.getValue("photo/p1").row["r2Key"] ?: JsonNull).isEqualTo(JsonNull)
    }

    @Test
    @AcceptanceCriterion("AC-H15")
    fun aFailedDeleteChangesNothing() = runTest {
        seedSyncedHome()
        deleteAnswer = ApiResult.Err(ApiFailure.Offline)

        assertThat(cloud.delete("h1")).isEqualTo(ApiResult.Err(ApiFailure.Offline))

        assertThat(signedOut).isEqualTo(0)
        assertThat(db.photoDao().get("p1")?.r2Key).isEqualTo("h1/photo/p1/p1.jpg")
        assertThat(db.syncCursorDao().get("home", "h1")).isNotNull()
        assertThat(db.outboxDao().pendingFor("home", "h1", 100)).isEmpty()
    }

    @Test fun otherHomesOutboxIsLeftAlone() = runTest {
        seedSyncedHome()
        db.homeDao().upsert(HomeEntity(id = "h2", name = "Lake cabin", createdAt = 1, updatedAt = 1))
        engine.recordChange(TenantRef(TenantKind.Home, "h2"), "home", "h2")

        cloud.delete("h1")

        assertThat(db.outboxDao().pendingFor("home", "h2", 100).map { it.entityId }).containsExactly("h2")
        val queued = db.outboxDao().pendingFor("home", "h1", 100)
        assertThat(queued.map { it.entity }).containsExactly("home", "floor", "room", "wall", "wall_photo", "item", "photo")
        val itemPayload = Json.parseToJsonElement(queued.first { it.entity == "item" }.payload) as JsonObject
        assertThat(itemPayload.keys).containsAtLeast("name", "make", "roomId", "category")
    }
}
