package com.kurtz.homerecord.core.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobFileEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.data.RoomEntity
import com.kurtz.homerecord.core.data.TaskEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
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

/** In-memory stand-in for the Worker: serverSeq per tenant, (device, outbox) dedupe and per-field last-writer-wins. */
private class FakeServer : SyncApi {
    var online = true
    var pushCalls = 0
    val pullsSince = mutableListOf<Pair<String, Long>>()
    /** Entities this tenant refuses, as the Worker does for a row outside the tenant's list. */
    var refused = emptySet<String>()
    var throwOnPull: Exception? = null
    val extraPulled = mutableListOf<PulledRow>()
    private val seqs = HashMap<String, Long>()
    private val tenants = HashMap<String, LinkedHashMap<String, PulledRow>>()
    private val applied = HashMap<Pair<String, Long>, Long>()

    override suspend fun push(tenant: TenantRef, deviceId: String, ops: List<PushOp>): ApiResult<PushResponse> {
        if (!online) return ApiResult.Err(ApiFailure.Offline)
        pushCalls++
        val rows = tenants.getOrPut(tenant.key) { LinkedHashMap() }
        val rejected = ops.filter { it.entity in refused }.map { Rejected(it.outboxId, "entity") }
        val accepted = ops.filter { it.entity !in refused }.map { op ->
            val serverSeq = applied.getOrPut(deviceId to op.outboxId) {
                val incoming = kotlinx.serialization.json.Json.parseToJsonElement(op.payload) as JsonObject
                val key = "${op.entity}/${op.entityId}"
                val old = rows[key]
                val row = LinkedHashMap(old?.row ?: emptyMap())
                val times = HashMap(old?.times ?: emptyMap())
                for ((f, v) in incoming) {
                    if (op.updatedAt >= (times[f] ?: Long.MIN_VALUE)) { row[f] = v; times[f] = op.updatedAt }
                }
                val seq = (seqs[tenant.key] ?: 0L) + 1
                seqs[tenant.key] = seq
                rows[key] = PulledRow(op.entity, op.entityId, JsonObject(row), times, seq)
                seq
            }
            Accepted(op.outboxId, serverSeq)
        }
        return ApiResult.Ok(PushResponse(accepted, rejected = rejected))
    }

    override suspend fun pull(tenant: TenantRef, since: Long): ApiResult<PullResponse> {
        if (!online) return ApiResult.Err(ApiFailure.Offline)
        throwOnPull?.let { throw it }
        pullsSince += tenant.id to since
        val rows = tenants[tenant.key]?.values.orEmpty()
        val out = (rows + extraPulled).filter { it.serverSeq > since }.sortedBy { it.serverSeq }
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
        SyncEngine(server, RoomRowStore(db), db.outboxDao(), db.syncCursorDao(), db.syncBaseDao(), device)

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
        val kept = RowMerge.applyPulled("task", local, hasPending = true, server = server, times = mapOf("title" to 20))
        assertThat(kept["title"]).isEqualTo(JsonPrimitive("Mine"))
        assertThat(RowMerge.applyPulled("task", local, hasPending = false, server = server, times = mapOf("title" to 20))["title"]).isEqualTo(JsonPrimitive("Theirs"))
        assertThat(RowMerge.applyPulled("task", local, true, server, mapOf("title" to 90))["title"]).isEqualTo(JsonPrimitive("Theirs"))
    }

    private fun room(polygon: String, name: String, at: Long) =
        RoomEntity(id = "r1", floorId = "f1", name = name, polygonJson = polygon, createdAt = 1, updatedAt = at)

    private suspend fun saveRoom(db: HomeRecordDatabase, engine: SyncEngine, r: RoomEntity) {
        db.roomDao().upsert(r)
        engine.recordChange(tenant, "room", r.id)
    }

    // cr:sync-json-union
    @Test fun movedRoomKeepsOnlyItsNewCornersOnTheOtherDevice() = runTest {
        val square = """[{"x":0,"y":0},{"x":120,"y":0},{"x":120,"y":120},{"x":0,"y":120}]"""
        val moved = """[{"x":24,"y":0},{"x":144,"y":0},{"x":144,"y":120},{"x":24,"y":120}]"""
        saveRoom(dbA, a, room(square, "Kitchen", 10))
        a.sync(tenant); b.sync(tenant)
        saveRoom(dbA, a, room(moved, "Kitchen", 20))
        a.sync(tenant)
        // B renamed the room while A moved it and was offline for one pass; both edits land on both phones.
        saveRoom(dbB, b, dbB.roomDao().get("r1")!!.copy(name = "Galley kitchen", updatedAt = 30))
        server.online = false
        b.sync(tenant)
        server.online = true
        b.sync(tenant); a.sync(tenant)
        for (db in listOf(dbA, dbB)) {
            val r = db.roomDao().get("r1")!!
            assertThat(r.polygonJson).isEqualTo(moved)
            assertThat(r.name).isEqualTo("Galley kitchen")
        }
    }

    // cr:sync-json-union: a field this device did not change takes the server's value even while another field is pending
    @Test fun pendingRowTakesServerValueForFieldsThisDeviceDidNotChange() {
        fun row(polygon: String, name: String, at: Long) = JsonObject(
            mapOf("id" to JsonPrimitive("r1"), "polygonJson" to JsonPrimitive(polygon), "name" to JsonPrimitive(name), "updatedAt" to JsonPrimitive(at)),
        )
        val local = row("[1]", "Galley", 50)
        val server = row("[2]", "Kitchen", 20)
        val merged = RowMerge.applyPulled("room", local, true, server, mapOf("polygonJson" to 20, "name" to 10), changedHere = setOf("id", "name", "updatedAt"))
        assertThat(merged["polygonJson"]?.jsonPrimitive?.content).isEqualTo("[2]")
        assertThat(merged["name"]?.jsonPrimitive?.content).isEqualTo("Galley")
    }

    // cr:sync-json-union: phases are last-writer-wins, so a removed phase stays removed
    @Test fun removedPhaseDoesNotComeBackOnPull() {
        fun row(phases: String, at: Long) = JsonObject(mapOf("id" to JsonPrimitive("j"), "phasesJson" to JsonPrimitive(phases), "updatedAt" to JsonPrimitive(at)))
        val merged = RowMerge.applyPulled("job", row("""["Demo","Paint"]""", 50), true, row("""["Demo","Frame","Paint"]""", 20), mapOf("phasesJson" to 20))
        assertThat(merged["phasesJson"]?.jsonPrimitive?.content).isEqualTo("""["Demo","Paint"]""")
        val older = RowMerge.applyPulled("job", row("""["Demo","Paint"]""", 10), true, row("""["Demo","Frame"]""", 20), mapOf("phasesJson" to 20))
        assertThat(older["phasesJson"]?.jsonPrimitive?.content).isEqualTo("""["Demo","Frame"]""")
    }

    @Test fun punchEditLogIsTheOnlyUnionedList() {
        fun row(edits: String) = JsonObject(mapOf("id" to JsonPrimitive("p"), "editsJson" to JsonPrimitive(edits), "updatedAt" to JsonPrimitive(1)))
        val merged = RowMerge.applyPulled("punch", row("""[{"at":1}]"""), true, row("""[{"at":2}]"""), emptyMap())
        assertThat(merged["editsJson"]?.jsonPrimitive?.content).isEqualTo("""[{"at":1},{"at":2}]""")
        assertThat(RowMerge.isUnionField("job", "phasesJson")).isFalse()
        assertThat(RowMerge.isUnionField("room", "polygonJson")).isFalse()
    }

    // cr:sync-poison-op
    @Test fun refusedOpIsSetAsideAndLaterChangesStillSync() = runTest {
        server.refused = setOf("room")
        saveRoom(dbA, a, room("[]", "Kitchen", 10))
        save(dbA, a, task("After the refused row", 11))
        val result = a.sync(tenant)
        assertThat(result).isInstanceOf(ApiResult.Ok::class.java)
        val conflicts = (result as ApiResult.Ok).value.conflicts
        assertThat(conflicts.map { it.entity to it.reason }).containsExactly("room" to "refused:entity")
        assertThat(a.pendingCount.first()).isEqualTo(0)
        assertThat(a.refusedCount.first()).isEqualTo(1)
        assertThat(a.phase.value).isEqualTo(SyncPhase.Idle)
        save(dbA, a, dbA.taskDao().get("t1")!!.copy(title = "Next edit", updatedAt = 12))
        a.sync(tenant); b.sync(tenant)
        assertThat(dbB.taskDao().get("t1")?.title).isEqualTo("Next edit")
        assertThat(dbB.roomDao().get("r1")).isNull()
    }

    private fun outboxRows(db: HomeRecordDatabase): List<Pair<Long, Long?>> =
        db.openHelper.readableDatabase.query("SELECT seq, serverSeq FROM outbox ORDER BY seq").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0) to if (c.isNull(1)) null else c.getLong(1)) }
        }

    // cr:outbox-growth
    @Test fun pulledBackRowsArePrunedRefusedAndUnsentRowsStay() = runTest {
        server.refused = setOf("room")
        saveRoom(dbA, a, room("[]", "Kitchen", 10))
        save(dbA, a, task("Patch drywall", 11))
        a.sync(tenant)
        // The accepted task row is gone; the refused room row stays for the log.
        assertThat(outboxRows(dbA).map { it.second }).containsExactly(SyncEngine.REFUSED)
        assertThat(a.refusedCount.first()).isEqualTo(1)
        server.online = false
        save(dbA, a, dbA.taskDao().get("t1")!!.copy(title = "Offline edit", updatedAt = 12))
        a.sync(tenant)
        assertThat(outboxRows(dbA)).hasSize(2)
        assertThat(a.pendingCount.first()).isEqualTo(1)
        server.online = true
        a.sync(tenant); b.sync(tenant)
        assertThat(outboxRows(dbA)).hasSize(1)
        assertThat(dbB.taskDao().get("t1")?.title).isEqualTo("Offline edit")
    }

    // cr:outbox-growth: the server dedupes by (device, outbox id), so a pruned id must never come back
    @Test fun outboxIdsAreNotReusedAfterPruning() = runTest {
        save(dbA, a, task("Patch drywall", 10))
        a.sync(tenant)
        assertThat(outboxRows(dbA)).isEmpty()
        save(dbA, a, dbA.taskDao().get("t1")!!.copy(title = "Second coat", updatedAt = 11))
        assertThat(outboxRows(dbA).single().first).isEqualTo(2L)
        a.sync(tenant); b.sync(tenant)
        assertThat(dbB.taskDao().get("t1")?.title).isEqualTo("Second coat")
    }

    // cr:sync-exceptions
    @Test fun anExceptionDuringSyncEndsAsFailedNotStuckSyncing() = runTest {
        server.throwOnPull = IllegalStateException("bad response")
        assertThat(a.sync(tenant)).isEqualTo(ApiResult.Err(ApiFailure.Failed))
        assertThat(a.phase.value).isEqualTo(SyncPhase.Failed)
        server.throwOnPull = null
        assertThat(a.sync(tenant)).isInstanceOf(ApiResult.Ok::class.java)
        assertThat(a.phase.value).isEqualTo(SyncPhase.Idle)
    }

    // cr:sync-exceptions: a newer server column is ignored and a row that cannot be stored is skipped and logged
    @Test fun unknownColumnsAndUnstorableRowsDoNotBlockThePull() = runTest {
        fun obj(vararg pairs: Pair<String, Any?>) = JsonObject(pairs.associate { (k, v) -> k to when (v) { null -> kotlinx.serialization.json.JsonNull; is Number -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) } })
        server.extraPulled += PulledRow("task", "t9", obj("id" to "t9", "jobId" to "j1", "title" to "From a newer app", "done" to 0, "punch" to 0, "createdAt" to 1, "updatedAt" to 5, "source" to "FIELD_LOG", "futureColumn" to "x"), serverSeq = 101)
        server.extraPulled += PulledRow("task", "t10", obj("id" to "t10", "title" to "No job id", "updatedAt" to 5), serverSeq = 102)
        server.extraPulled += PulledRow("no_such_table", "x", obj("id" to "x"), serverSeq = 103)
        server.extraPulled += PulledRow("task", "t11", obj("id" to "t11", "jobId" to "j1", "title" to "After the bad rows", "done" to 0, "punch" to 0, "createdAt" to 1, "updatedAt" to 6, "source" to "FIELD_LOG"), serverSeq = 104)
        val result = a.sync(tenant)
        assertThat(result).isInstanceOf(ApiResult.Ok::class.java)
        assertThat((result as ApiResult.Ok).value.conflicts.map { it.entityId }).containsExactly("t10", "x")
        assertThat(dbA.taskDao().get("t9")?.title).isEqualTo("From a newer app")
        assertThat(dbA.taskDao().get("t11")?.title).isEqualTo("After the bad rows")
        assertThat(a.phase.value).isEqualTo(SyncPhase.Idle)
    }

    // A local column the server row does not carry is kept
    @Test fun pulledRowWithoutALocalColumnKeepsTheLocalValue() {
        val local = JsonObject(mapOf("id" to JsonPrimitive("t1"), "title" to JsonPrimitive("Old"), "note" to JsonPrimitive("Local only"), "updatedAt" to JsonPrimitive(1)))
        val server = JsonObject(mapOf("id" to JsonPrimitive("t1"), "title" to JsonPrimitive("New"), "updatedAt" to JsonPrimitive(2)))
        val merged = RowMerge.applyPulled("task", local, false, server, emptyMap())
        assertThat(merged["title"]?.jsonPrimitive?.content).isEqualTo("New")
        assertThat(merged["note"]?.jsonPrimitive?.content).isEqualTo("Local only")
    }

    // cr:sync-global-cursor: serverSeq counts per tenant, so each tenant keeps its own pull cursor
    @Test fun secondTenantOnTheSamePhonePullsItsRowsFromTheStart() = runTest {
        val other = TenantRef(TenantKind.Company, "c2")
        for (i in 1..5) save(dbA, a, task("Company one edit $i", i.toLong()))
        a.sync(tenant)
        dbB.taskDao().upsert(TaskEntity(id = "t2", jobId = "j9", title = "Company two task", createdAt = 1, updatedAt = 1))
        b.recordChange(other, "task", "t2")
        b.sync(other)
        a.sync(other)
        assertThat(dbA.taskDao().get("t2")?.title).isEqualTo("Company two task")
        // A's first pull of the second company starts at 0, not at the first company's cursor.
        assertThat(server.pullsSince.last()).isEqualTo("c2" to 0L)
    }

    // cr:sync-global-cursor: a tenant the phone left is pulled from the start when it comes back
    @Test fun forgottenTenantPullsFromTheStartAndPushesWholeRows() = runTest {
        save(dbA, a, task("Kept", 10))
        a.sync(tenant)
        a.forgetTenant(tenant)
        server.pullsSince.clear()
        a.sync(tenant)
        assertThat(server.pullsSince.first()).isEqualTo("c1" to 0L)
        a.forgetTenant(tenant)
        save(dbA, a, dbA.taskDao().get("t1")!!.copy(title = "After rejoining", updatedAt = 20))
        val payload = dbA.outboxDao().pending().single().payload
        assertThat(payload).contains("jobId")
    }

    @Test fun endedTenantsEmitsTheTenantLeft() = runTest {
        val c2 = TenantRef(TenantKind.Company, "c2")
        val ended = kotlinx.coroutines.flow.flowOf(null, tenant, tenant, c2, null, c2).endedTenants().toList()
        assertThat(ended).containsExactly(tenant, c2).inOrder()
    }

    // cr:sync-merge-race: only fields in this phone's unsent payloads stay local; every other field takes the server value
    @Test fun pullWhileAnEditIsPendingKeepsOnlyTheEditedField() = runTest {
        save(dbA, a, task("Base", 10))
        a.sync(tenant); b.sync(tenant)
        save(dbB, b, dbB.taskDao().get("t1")!!.copy(done = true, updatedAt = 20))
        b.sync(tenant)
        // A edits the title offline; its row still says not done, written later than B's done.
        save(dbA, a, dbA.taskDao().get("t1")!!.copy(title = "Renamed on A", updatedAt = 50))
        server.online = false
        a.sync(tenant)
        server.online = true
        assertThat(dbA.outboxDao().pending().single().payload).doesNotContain("\"done\"")
        a.sync(tenant); b.sync(tenant)
        for (db in listOf(dbA, dbB)) {
            val t = db.taskDao().get("t1")!!
            assertThat(t.title).isEqualTo("Renamed on A")
            assertThat(t.done).isTrue()
        }
    }

    @Test fun mergeKeepsOnlyFieldsChangedHere() {
        fun row(title: String, done: Int, at: Long) = JsonObject(
            mapOf("id" to JsonPrimitive("t1"), "title" to JsonPrimitive(title), "done" to JsonPrimitive(done), "updatedAt" to JsonPrimitive(at)),
        )
        val merged = RowMerge.applyPulled("task", row("Mine", 0, 50), true, row("Theirs", 1, 20), mapOf("title" to 20, "done" to 20), setOf("id", "title", "updatedAt"))
        assertThat(merged["title"]?.jsonPrimitive?.content).isEqualTo("Mine")
        assertThat(merged["done"]).isEqualTo(JsonPrimitive(1))
    }
    // cr:sync-local-columns: this phone's file path never goes out; the other phone stores the photo with its R2 key
    @Test fun photoPathStaysOnThePhoneAndTheR2KeySyncs() = runTest {
        dbA.photoDao().upsert(
            PhotoEntity(id = "p1", homeId = "j1", jobId = "j1", path = "/data/a/media/p1.jpg", thumbPath = "/data/a/media/p1_t.jpg", r2Key = "c1/photo/p1/p1.jpg", takenAt = 5, createdAt = 5, updatedAt = 5),
        )
        a.recordChange(tenant, "photo", "p1")
        val payload = dbA.outboxDao().pending().single().payload
        assertThat(payload).doesNotContain("/data/a")
        assertThat(payload).contains("r2Key")
        a.sync(tenant); b.sync(tenant)
        val onB = dbB.photoDao().get("p1")!!
        assertThat(onB.path).isEmpty()
        assertThat(onB.thumbPath).isNull()
        assertThat(onB.r2Key).isEqualTo("c1/photo/p1/p1.jpg")
    }

    // cr:sync-local-columns, AC-F20: Available offline and the cached path are per phone and survive the other phone's edit
    @Test fun jobFileOfflineAndPathAreKeptWhenTheOtherPhoneEdits() = runTest {
        val file = JobFileEntity(id = "f1", jobId = "j1", name = "Plans.pdf", path = "/a/Plans.pdf", r2Key = "c1/job_file/f1/Plans.pdf", mime = "application/pdf", createdAt = 1, updatedAt = 1)
        dbA.jobFileDao().upsert(file)
        a.recordChange(tenant, "job_file", "f1")
        a.sync(tenant); b.sync(tenant)
        assertThat(dbB.jobFileDao().get("f1")?.path).isNull()
        dbB.jobFileDao().upsert(dbB.jobFileDao().get("f1")!!.copy(path = "/b/offline/Plans.pdf", offline = true))
        dbA.jobFileDao().upsert(dbA.jobFileDao().get("f1")!!.copy(name = "Plans rev B.pdf", updatedAt = 30))
        a.recordChange(tenant, "job_file", "f1")
        a.sync(tenant); b.sync(tenant)
        val onB = dbB.jobFileDao().get("f1")!!
        assertThat(onB.name).isEqualTo("Plans rev B.pdf")
        assertThat(onB.path).isEqualTo("/b/offline/Plans.pdf")
        assertThat(onB.offline).isTrue()
        assertThat(dbA.jobFileDao().get("f1")?.offline).isFalse()
    }

    // cr:sync-local-columns: a row an older build pushed with its own path and offline flag does not overwrite this phone's
    @Test fun pulledLocalColumnsFromAnOlderBuildAreIgnored() = runTest {
        dbB.jobFileDao().upsert(JobFileEntity(id = "f2", jobId = "j1", name = "Permit.pdf", path = "/b/Permit.pdf", mime = "application/pdf", createdAt = 1, updatedAt = 1))
        server.extraPulled += PulledRow(
            "job_file", "f2",
            JsonObject(
                mapOf(
                    "id" to JsonPrimitive("f2"), "jobId" to JsonPrimitive("j1"), "name" to JsonPrimitive("Permit signed.pdf"),
                    "path" to JsonPrimitive("/a/cache/Permit.pdf"), "offline" to JsonPrimitive(1), "mime" to JsonPrimitive("application/pdf"),
                    "createdAt" to JsonPrimitive(1), "updatedAt" to JsonPrimitive(40),
                ),
            ),
            serverSeq = 1,
        )
        b.sync(tenant)
        val onB = dbB.jobFileDao().get("f2")!!
        assertThat(onB.name).isEqualTo("Permit signed.pdf")
        assertThat(onB.path).isEqualTo("/b/Permit.pdf")
        assertThat(onB.offline).isFalse()
        assertThat(dbB.syncBaseDao().get("job_file/f2")).doesNotContain("/a/cache")
    }

    @Test fun localColumnRule() {
        assertThat(LocalColumns.isLocal("photo", "path")).isTrue()
        assertThat(LocalColumns.isLocal("photo", "thumbPath")).isTrue()
        assertThat(LocalColumns.isLocal("note", "voicePath")).isTrue()
        assertThat(LocalColumns.isLocal("job_file", "offline")).isTrue()
        assertThat(LocalColumns.isLocal("photo", "r2Key")).isFalse()
        assertThat(LocalColumns.isLocal("task", "offline")).isFalse()
    }
}
