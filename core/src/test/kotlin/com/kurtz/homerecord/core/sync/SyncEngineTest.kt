package com.kurtz.homerecord.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** In-memory stand-in for the Worker: serverSeq, (device, outbox) dedupe and per-field last-writer-wins. */
private class FakeServer : SyncApi {
    var online = true
    var pushCalls = 0
    private var seq = 0L
    private val rows = LinkedHashMap<String, PulledRow>()
    private val applied = HashMap<Pair<String, Long>, Long>()

    override suspend fun push(tenant: TenantRef, deviceId: String, ops: List<PushOp>): ApiResult<PushResponse> {
        if (!online) return ApiResult.Err(ApiFailure.Offline)
        pushCalls++
        val accepted = ops.map { op ->
            val serverSeq = applied.getOrPut(deviceId to op.outboxId) {
                val incoming = kotlinx.serialization.json.Json.parseToJsonElement(op.payload) as JsonObject
                val key = "${op.entity}/${op.entityId}"
                val old = rows[key]
                val row = LinkedHashMap(old?.row ?: emptyMap())
                val times = HashMap(old?.times ?: emptyMap())
                for ((f, v) in incoming) {
                    if (op.updatedAt >= (times[f] ?: Long.MIN_VALUE)) { row[f] = v; times[f] = op.updatedAt }
                }
                rows[key] = PulledRow(op.entity, op.entityId, JsonObject(row), times, ++seq)
                seq
            }
            Accepted(op.outboxId, serverSeq)
        }
        return ApiResult.Ok(PushResponse(accepted))
    }

    override suspend fun pull(tenant: TenantRef, since: Long): ApiResult<PullResponse> {
        if (!online) return ApiResult.Err(ApiFailure.Offline)
        val out = rows.values.filter { it.serverSeq > since }.sortedBy { it.serverSeq }
        return ApiResult.Ok(PullResponse(out, out.lastOrNull()?.serverSeq ?: since))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
// AC-F53
class SyncEngineTest {
    private val tenant = TenantRef(TenantKind.Company, "c1")
    private val server = FakeServer()
    private lateinit var dbA: HomeRecordDatabase
    private lateinit var dbB: HomeRecordDatabase
    private lateinit var a: SyncEngine
    private lateinit var b: SyncEngine

    private fun engine(db: HomeRecordDatabase, device: String) =
        SyncEngine(server, RoomRowStore(db), db.outboxDao(), db.syncStateDao(), db.syncBaseDao(), device)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        dbA = Room.inMemoryDatabaseBuilder(ctx, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        dbB = Room.inMemoryDatabaseBuilder(ctx, HomeRecordDatabase::class.java).allowMainThreadQueries().build()
        a = engine(dbA, "phone-a")
        b = engine(dbB, "phone-b")
    }

    @After fun tearDown() { dbA.close(); dbB.close() }

    private fun task(title: String, at: Long, done: Boolean = false) =
        TaskEntity(id = "t1", jobId = "j1", title = title, done = done, createdAt = 1, updatedAt = at)

    private suspend fun save(db: HomeRecordDatabase, engine: SyncEngine, t: TaskEntity) {
        db.taskDao().upsert(t)
        engine.recordChange(tenant, "task", t.id)
    }

    @Test fun pushedChangeAppearsOnTheOtherDevice() = runTest {
        save(dbA, a, task("Patch drywall", 10))
        assertThat(a.sync(tenant)).isInstanceOf(ApiResult.Ok::class.java)
        b.sync(tenant)
        assertThat(dbB.taskDao().get("t1")?.title).isEqualTo("Patch drywall")
        assertThat(dbA.outboxDao().pending()).isEmpty()
    }

    @Test fun offlineChangesWaitThenSyncOnceWithoutDuplicates() = runTest {
        server.online = false
        save(dbA, a, task("Offline edit", 10))
        save(dbA, a, task("Offline edit 2", 11))
        assertThat(a.pendingCount.first()).isEqualTo(2)
        assertThat(a.sync(tenant)).isEqualTo(ApiResult.Err(ApiFailure.Offline))
        assertThat(a.phase.value).isEqualTo(SyncPhase.Offline)
        assertThat(a.pendingCount.first()).isEqualTo(2)

        server.online = true
        a.sync(tenant)
        a.sync(tenant)
        assertThat(a.pendingCount.first()).isEqualTo(0)
        assertThat(server.pushCalls).isEqualTo(1)
        b.sync(tenant)
        assertThat(dbB.taskDao().get("t1")?.title).isEqualTo("Offline edit 2")
    }

    @Test fun editsToDifferentFieldsOnTwoDevicesBothSurvive() = runTest {
        save(dbA, a, task("Base", 10))
        a.sync(tenant); b.sync(tenant)
        save(dbA, a, dbA.taskDao().get("t1")!!.copy(title = "Renamed", updatedAt = 30))
        save(dbB, b, dbB.taskDao().get("t1")!!.copy(done = true, updatedAt = 20))
        a.sync(tenant); b.sync(tenant); a.sync(tenant)
        for (db in listOf(dbA, dbB)) {
            val t = db.taskDao().get("t1")!!
            assertThat(t.title).isEqualTo("Renamed")
            assertThat(t.done).isTrue()
        }
    }

    @Test fun sameFieldEditedOnTwoDevicesLatestWriteWins() = runTest {
        save(dbA, a, task("Base", 10))
        a.sync(tenant); b.sync(tenant)
        save(dbA, a, dbA.taskDao().get("t1")!!.copy(title = "From A", updatedAt = 30))
        save(dbB, b, dbB.taskDao().get("t1")!!.copy(title = "From B", updatedAt = 20))
        b.sync(tenant); a.sync(tenant); b.sync(tenant)
        assertThat(dbA.taskDao().get("t1")?.title).isEqualTo("From A")
        assertThat(dbB.taskDao().get("t1")?.title).isEqualTo("From A")
    }

    @Test fun unsentLocalEditNewerThanServerSurvivesAPull() = runTest {
        save(dbA, a, task("Base", 10))
        a.sync(tenant); b.sync(tenant)
        save(dbA, a, dbA.taskDao().get("t1")!!.copy(title = "Server side", updatedAt = 20))
        a.sync(tenant)
        server.online = true
        save(dbB, b, dbB.taskDao().get("t1")!!.copy(title = "Newer local", updatedAt = 50))
        b.sync(tenant)
        a.sync(tenant)
        assertThat(dbB.taskDao().get("t1")?.title).isEqualTo("Newer local")
        assertThat(dbA.taskDao().get("t1")?.title).isEqualTo("Newer local")
    }

    @Test fun softDeletePropagates() = runTest {
        save(dbA, a, task("Gone soon", 10))
        a.sync(tenant); b.sync(tenant)
        dbA.taskDao().softDelete("t1", at = 40)
        a.recordChange(tenant, "task", "t1")
        a.sync(tenant); b.sync(tenant)
        assertThat(dbB.taskDao().get("t1")).isNull()
        assertThat(RoomRowStore(dbB).read("task", "t1")?.get("deletedAt")?.jsonPrimitive?.longOrNull).isEqualTo(40)
    }

    @Test fun pullKeepsALocalEditNewerThanTheServerValue() {
        val local = JsonObject(mapOf("id" to JsonPrimitive("t1"), "title" to JsonPrimitive("Mine"), "updatedAt" to JsonPrimitive(50)))
        val server = JsonObject(mapOf("id" to JsonPrimitive("t1"), "title" to JsonPrimitive("Theirs"), "updatedAt" to JsonPrimitive(20)))
        val kept = RowMerge.applyPulled(local, hasPending = true, server = server, times = mapOf("title" to 20))
        assertThat(kept["title"]).isEqualTo(JsonPrimitive("Mine"))
        assertThat(RowMerge.applyPulled(local, hasPending = false, server = server, times = mapOf("title" to 20))["title"]).isEqualTo(JsonPrimitive("Theirs"))
        assertThat(RowMerge.applyPulled(local, true, server, mapOf("title" to 90))["title"]).isEqualTo(JsonPrimitive("Theirs"))
    }

    @Test fun jsonListsUnionOnPull() {
        fun row(phases: String) = JsonObject(mapOf("id" to JsonPrimitive("j"), "phasesJson" to JsonPrimitive(phases), "updatedAt" to JsonPrimitive(1)))
        val merged = RowMerge.applyPulled(row("""["Demo","Frame"]"""), true, row("""["Frame","Paint"]"""), emptyMap())
        assertThat(merged["phasesJson"]?.jsonPrimitive?.content).isEqualTo("""["Demo","Frame","Paint"]""")
    }
}
