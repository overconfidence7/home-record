package com.kurtz.homerecord.fieldlog.acceptance

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.media.MediaCache
import com.kurtz.homerecord.core.media.MediaCacheSettings
import com.kurtz.homerecord.core.media.MediaDownloads
import com.kurtz.homerecord.core.media.MediaTransferApi
import com.kurtz.homerecord.core.media.MediaUploader
import com.kurtz.homerecord.core.media.Presigned
import com.kurtz.homerecord.core.media.PutOutcome
import com.kurtz.homerecord.core.media.UploadedKeys
import com.kurtz.homerecord.core.model.PunchKind
import com.kurtz.homerecord.core.punch.NewPunch
import com.kurtz.homerecord.core.punch.PunchRepository
import com.kurtz.homerecord.core.sync.Accepted
import com.kurtz.homerecord.core.sync.PullResponse
import com.kurtz.homerecord.core.sync.PulledRow
import com.kurtz.homerecord.core.sync.PushOp
import com.kurtz.homerecord.core.sync.PushResponse
import com.kurtz.homerecord.core.sync.RoomRowStore
import com.kurtz.homerecord.core.sync.SyncApi
import com.kurtz.homerecord.core.sync.SyncEngine
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.Brand
import com.kurtz.homerecord.coreui.theme.HomeRecordTheme
import com.kurtz.homerecord.fieldlog.photos.PhotosDeps
import com.kurtz.homerecord.fieldlog.photos.PhotosScreen
import com.kurtz.homerecord.fieldlog.photos.PhotosViewModel
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Worker's sync contract in process: serverSeq per tenant, (device, outbox) dedupe, per-field last writer wins. */
private class InProcessSyncServer : SyncApi {
    private var seq = 0L
    private val rows = LinkedHashMap<String, PulledRow>()
    private val applied = HashMap<Pair<String, Long>, Long>()

    private val lock = Mutex()

    override suspend fun push(tenant: TenantRef, deviceId: String, ops: List<PushOp>): ApiResult<PushResponse> = lock.withLock {
        ApiResult.Ok(
            PushResponse(
                ops.map { op ->
                    Accepted(
                        op.outboxId,
                        applied.getOrPut(deviceId to op.outboxId) {
                            val key = "${tenant.key}/${op.entity}/${op.entityId}"
                            val old = rows[key]
                            val row = LinkedHashMap(old?.row ?: emptyMap())
                            val times = HashMap(old?.times ?: emptyMap())
                            for ((f, v) in Json.parseToJsonElement(op.payload) as JsonObject) {
                                if (op.updatedAt >= (times[f] ?: Long.MIN_VALUE)) { row[f] = v; times[f] = op.updatedAt }
                            }
                            rows[key] = PulledRow(op.entity, op.entityId, JsonObject(row), times, ++seq)
                            seq
                        },
                    )
                },
            ),
        )
    }

    override suspend fun pull(tenant: TenantRef, since: Long): ApiResult<PullResponse> = lock.withLock {
        val out = rows.filterKeys { it.startsWith("${tenant.key}/") }.values.filter { it.serverSeq > since }.sortedBy { it.serverSeq }
        ApiResult.Ok(PullResponse(out, out.lastOrNull()?.serverSeq ?: since))
    }
}

/** R2 in process: a PUT keeps the file's bytes under its key, a GET hands them back. */
private class InProcessR2 : MediaTransferApi {
    val objects = ConcurrentHashMap<String, ByteArray>()
    override suspend fun presign(tenant: TenantRef, entity: String, entityId: String, filename: String, size: Long, mime: String) =
        "${tenant.id}/$entity/$entityId/$filename".let { ApiResult.Ok(Presigned(it, "r2://put/$it", Long.MAX_VALUE)) }
    override suspend fun presignGet(tenant: TenantRef, key: String): ApiResult<String> = ApiResult.Ok("r2://get/$key")
    override suspend fun put(url: String, file: File, mime: String): PutOutcome {
        objects[url.removePrefix("r2://put/")] = file.readBytes()
        return PutOutcome.Ok
    }
    override suspend fun download(url: String, dest: File): Boolean {
        val bytes = objects[url.removePrefix("r2://get/")] ?: return false
        dest.parentFile?.mkdirs()
        dest.writeBytes(bytes)
        return true
    }
}

/**
 * AC-F55 on a device: two phones on one company, each with its own Room database, SyncEngine, upload queue and
 * download cache, talking to the sync contract and R2 in process. Phone B's Photos tab is the real screen and
 * ViewModel. MediaDownloadsTest, PhotosViewModelTest and SyncEngineTest prove the same rules on the JVM.
 */
@RunWith(AndroidJUnit4::class)
class TwoPhoneSyncAcceptanceTest {
    @get:Rule val rule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val company = TenantRef(TenantKind.Company, "c-ortiz")
    private val server = InProcessSyncServer()
    private val r2 = InProcessR2()
    private lateinit var root: File
    private lateinit var dbA: HomeRecordDatabase
    private lateinit var dbB: HomeRecordDatabase
    private lateinit var a: SyncEngine
    private lateinit var b: SyncEngine

    private fun db() = Room.inMemoryDatabaseBuilder(context, HomeRecordDatabase::class.java).build()
    private fun engine(db: HomeRecordDatabase, device: String) =
        SyncEngine(server, RoomRowStore(db), db.outboxDao(), db.syncCursorDao(), db.syncBaseDao(), device)

    @Before fun setUp() {
        root = File(context.cacheDir, "two-phones").apply { deleteRecursively(); mkdirs() }
        dbA = db(); dbB = db()
        a = engine(dbA, "phone-owner"); b = engine(dbB, "phone-crew")
    }

    @After fun tearDown() {
        dbA.close(); dbB.close()
        root.deleteRecursively()
    }

    @AcceptanceCriterion("AC-F55")
    @Test fun photoTakenOnOnePhoneAppearsInTheOtherPhonesJobGalleryWithin60Seconds() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3, 0xFF.toByte(), 0xD9.toByte())
        val started = System.currentTimeMillis()
        runBlocking {
            // Phone A: the job, then a photo saved on the phone, uploaded and synced as the app does after a capture.
            dbA.jobDao().upsert(JobEntity(id = "j-harlan", companyId = company.id, name = "Harlan kitchen remodel", createdAt = started, updatedAt = started))
            a.recordChange(company, "job", "j-harlan")
            val file = File(root, "phone-a/IMG_0412.jpg").apply { parentFile?.mkdirs(); writeBytes(jpeg) }
            dbA.photoDao().upsert(
                PhotoEntity(id = "p-panel", homeId = "h-harlan", jobId = "j-harlan", stage = "Rough-in", path = file.absolutePath, takenAt = started, createdAt = started, updatedAt = started),
            )
            a.recordChange(company, "photo", "p-panel")
            val keys = UploadedKeys(dbA, onChange = { e, id -> a.recordChange(company, e, id) })
            val uploader = MediaUploader(dbA.mediaUploadDao(), r2, onUploaded = { row, signed -> keys.record(row.entity, row.entityId, signed.key) })
            uploader.enqueue(company, "photo", "p-panel", file.name, file.absolutePath, "image/jpeg")
            assertThat(uploader.runPending().uploaded).isEqualTo(1)
            assertThat(a.sync(company)).isInstanceOf(ApiResult.Ok::class.java)
            assertThat(a.pendingCount.first()).isEqualTo(0)

            // Phone B pulls: the record arrives without bytes (path is local to phone A).
            assertThat(b.sync(company)).isInstanceOf(ApiResult.Ok::class.java)
            val pulled = dbB.photoDao().get("p-panel")
            assertThat(pulled?.path).isEmpty()
            assertThat(pulled?.r2Key).isEqualTo("c-ortiz/photo/p-panel/IMG_0412.jpg")
        }

        val cache = MediaCache(File(root, "phone-b/downloads"), object : MediaCacheSettings { override var capBytes = 10_000_000L }, r2)
        val downloads = MediaDownloads(dbB, r2, cache, tenant = { company })
        val vm = PhotosViewModel(
            PhotosDeps("j-harlan", dbB.photoDao(), dbB.jobDao(), dbB.roomDao(), missing = downloads::missing, download = downloads::photo),
        )
        rule.setContent {
            CompositionLocalProvider(LocalTagPrefix provides "fl") {
                HomeRecordTheme(Brand.FieldLog, dynamicColor = false) {
                    val state by vm.state.collectAsStateWithLifecycle()
                    PhotosScreen(state, onEvent = {})
                }
            }
        }
        rule.waitUntil(60_000) { runBlocking { dbB.photoDao().get("p-panel")?.path.orEmpty().isNotEmpty() } }
        rule.waitUntil(10_000) { rule.onAllNodesWithTagExists("fl_photos_tile_p-panel") }
        rule.onNodeWithTag("fl_photos_tile_p-panel").assertIsDisplayed()
        rule.onNodeWithTag("fl_photos_tile_waiting_p-panel").assertDoesNotExist()
        assertThat(System.currentTimeMillis() - started).isLessThan(60_000L)

        runBlocking {
            val onB = dbB.photoDao().get("p-panel")
            assertThat(File(onB?.path.orEmpty()).readBytes()).isEqualTo(jpeg)
            // The local path is this phone's business: setting it queued nothing for sync.
            assertThat(b.pendingCount.first()).isEqualTo(0)
        }
    }

    @AcceptanceCriterion("AC-F55")
    @Test fun punchEditByTheOwnerAppearsOnTheCrewPhoneWithTheAuditNote() = runBlocking<Unit> {
        val day = 1_761_220_800_000L // 2026-10-23 12:00 UTC
        val crew = PunchRepository(dbB, now = { day }, onChange = { e, id -> b.recordChange(company, e, id) })
        val owner = PunchRepository(dbA, now = { day + 3_600_000 }, onChange = { e, id -> a.recordChange(company, e, id) })

        // The crew phone clocks in late and out; both punches sync up.
        val clockIn = crew.record(NewPunch(company.id, "m-luis", "j-harlan", PunchKind.IN, at = day - 4 * 3_600_000))
        crew.record(NewPunch(company.id, "m-luis", "j-harlan", PunchKind.OUT, at = day))
        b.sync(company)

        // The owner's phone pulls them, moves the clock in to 7:00 with a reason, and syncs.
        a.sync(company)
        val corrected = day - 5 * 3_600_000
        assertThat(owner.edit(clockIn, "m-dana", "Dana Ortiz", corrected, "j-harlan", "Forgot to clock in at the gate")).isTrue()
        a.sync(company)

        // The crew phone pulls the edit: the new time, the audit entry, and the shift flagged Edited.
        b.sync(company)
        val onCrew = dbB.punchDao().get(clockIn)
        assertThat(onCrew?.at).isEqualTo(corrected)
        val audit = crew.auditOf(requireNotNull(onCrew)).single()
        assertThat(audit.byName).isEqualTo("Dana Ortiz")
        assertThat(audit.reason).isEqualTo("Forgot to clock in at the gate")
        assertThat(audit.beforeAt).isEqualTo(day - 4 * 3_600_000)
        assertThat(audit.afterAt).isEqualTo(corrected)
        val shift = crew.observeShifts(company.id, "m-luis").first().single()
        assertThat(shift.startAt).isEqualTo(corrected)
        assertThat(shift.edited).isTrue()
        assertThat(b.pendingCount.first()).isEqualTo(0)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagExists(tag: String): Boolean =
    onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
