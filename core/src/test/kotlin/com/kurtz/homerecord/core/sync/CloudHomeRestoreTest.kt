package com.kurtz.homerecord.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.auth.AccountHome
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.FloorEntity
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.ItemEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// AC-H15: a second phone signed in to the same account shows the same home with its photos; an edit there syncs back.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CloudHomeRestoreTest {
    private val home = TenantRef(TenantKind.Home, "h1")
    private val server = FakeSyncServer()
    private lateinit var phoneA: HomeRecordDatabase
    private lateinit var phoneB: HomeRecordDatabase
    private lateinit var engineA: SyncEngine
    private lateinit var engineB: SyncEngine
    private var accountHomes: ApiResult<List<AccountHome>> = ApiResult.Ok(listOf(AccountHome("h1", "Maple St", "owner")))

    private fun db(): HomeRecordDatabase =
        Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java).allowMainThreadQueries().build()

    private fun engine(db: HomeRecordDatabase, device: String) =
        SyncEngine(server, RoomRowStore(db), db.outboxDao(), db.syncCursorDao(), db.syncBaseDao(), device)

    private fun restoreOn(db: HomeRecordDatabase, engine: SyncEngine) = CloudHomeRestore(
        homesOnAccount = { accountHomes },
        localHomeIds = { db.homeDao().observeAll().first().map { it.id } },
        sync = engine::sync,
    )

    @Before fun setUp() {
        phoneA = db()
        phoneB = db()
        engineA = engine(phoneA, "phone-a")
        engineB = engine(phoneB, "phone-b")
    }

    @After fun tearDown() {
        phoneA.close()
        phoneB.close()
    }

    private suspend fun uploadFromPhoneA() {
        phoneA.homeDao().upsert(HomeEntity(id = "h1", name = "Maple St", createdAt = 1, updatedAt = 1))
        phoneA.floorDao().upsert(FloorEntity(id = "f1", homeId = "h1", sortOrder = 0, name = "Main floor", createdAt = 1, updatedAt = 1))
        phoneA.roomDao().upsert(RoomEntity(id = "r1", floorId = "f1", name = "Kitchen", createdAt = 1, updatedAt = 1))
        phoneA.itemDao().upsert(ItemEntity(id = "i1", homeId = "h1", roomId = "r1", category = "HVAC", subtype = "furnace", name = "Furnace", make = "Carrier", createdAt = 3, updatedAt = 3))
        phoneA.photoDao().upsert(PhotoEntity(id = "p1", homeId = "h1", itemId = "i1", path = "/media/p1.jpg", takenAt = 4, r2Key = "h1/photo/p1/p1.jpg", createdAt = 4, updatedAt = 4))
        for ((entity, id) in listOf("home" to "h1", "floor" to "f1", "room" to "r1", "item" to "i1", "photo" to "p1")) engineA.recordChange(home, entity, id)
        assertThat(engineA.sync(home)).isInstanceOf(ApiResult.Ok::class.java)
    }

    @Test
    @AcceptanceCriterion("AC-H15")
    fun signingInOnAnEmptyPhoneBringsTheHomeAndItsPhotoKeys() = runTest {
        uploadFromPhoneA()

        assertThat(restoreOn(phoneB, engineB).restore()).isEqualTo(CloudRestoreResult.Restored("h1"))

        assertThat(phoneB.homeDao().get("h1")?.name).isEqualTo("Maple St")
        assertThat(phoneB.roomDao().get("r1")?.name).isEqualTo("Kitchen")
        assertThat(phoneB.itemDao().get("i1")?.make).isEqualTo("Carrier")
        // The photo row arrives with its R2 key and no file yet, so the photo pass fetches it.
        val photo = phoneB.photoDao().get("p1")
        assertThat(photo?.r2Key).isEqualTo("h1/photo/p1/p1.jpg")
        assertThat(photo?.path).isEmpty()
    }

    @Test
    @AcceptanceCriterion("AC-H15")
    fun anEditOnTheSecondPhoneReachesTheFirst() = runTest {
        uploadFromPhoneA()
        restoreOn(phoneB, engineB).restore()

        val item = requireNotNull(phoneB.itemDao().get("i1"))
        phoneB.itemDao().upsert(item.copy(model = "59SC5A", updatedAt = 10))
        engineB.recordChange(home, "item", "i1")
        assertThat(engineB.sync(home)).isInstanceOf(ApiResult.Ok::class.java)
        assertThat(engineA.sync(home)).isInstanceOf(ApiResult.Ok::class.java)

        assertThat(phoneA.itemDao().get("i1")?.model).isEqualTo("59SC5A")
        assertThat(phoneA.photoDao().get("p1")?.path).isEqualTo("/media/p1.jpg")
    }

    @Test fun aPhoneThatHasAHomeIsLeftAlone() = runTest {
        uploadFromPhoneA()
        phoneB.homeDao().upsert(HomeEntity(id = "h2", name = "Lake cabin", createdAt = 1, updatedAt = 1))

        assertThat(restoreOn(phoneB, engineB).restore()).isEqualTo(CloudRestoreResult.AlreadyHasHome)
        assertThat(phoneB.homeDao().get("h1")).isNull()
    }

    @Test fun anAccountWithNoHomeSaysSo() = runTest {
        accountHomes = ApiResult.Ok(emptyList())
        assertThat(restoreOn(phoneB, engineB).restore()).isEqualTo(CloudRestoreResult.NoHome)
    }

    @Test fun offlineFailsAndPullsNothing() = runTest {
        uploadFromPhoneA()
        accountHomes = ApiResult.Err(ApiFailure.Offline)
        assertThat(restoreOn(phoneB, engineB).restore()).isEqualTo(CloudRestoreResult.Failed(ApiFailure.Offline))

        accountHomes = ApiResult.Ok(listOf(AccountHome("h1", "Maple St", "owner")))
        server.online = false
        assertThat(restoreOn(phoneB, engineB).restore()).isEqualTo(CloudRestoreResult.Failed(ApiFailure.Offline))
        assertThat(phoneB.homeDao().get("h1")).isNull()
    }

    @Test fun theOwnedHomeIsPickedBeforeAShareOne() {
        val picked = CloudHomeRestore.pick(
            listOf(AccountHome("h9", "Aunt Ruth's house", "viewer"), AccountHome("h1", "Maple St", "owner"), AccountHome("h2", "Lake cabin", "owner")),
        )
        assertThat(picked?.id).isEqualTo("h2")
        assertThat(CloudHomeRestore.pick(emptyList())).isNull()
    }
}
