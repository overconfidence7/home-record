package com.kurtz.homerecord.core.media

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobEntity
import com.kurtz.homerecord.core.photo.CaptureTags
import com.kurtz.homerecord.core.photo.JobPhotos
import com.kurtz.homerecord.core.sync.TenantRef
import java.io.File
import com.kurtz.homerecord.core.photo.UploadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Network that is down until [online] is set; counts every presign and PUT. */
private class SwitchableNetwork : MediaTransferApi {
    var online = false
    var presigns = 0
    val puts = mutableListOf<String>()

    override suspend fun presign(tenant: TenantRef, entity: String, entityId: String, filename: String, size: Long, mime: String): ApiResult<Presigned> {
        presigns++
        return ApiResult.Ok(Presigned("${tenant.id}/$entity/$entityId/$filename", "https://r2/put/$presigns", Long.MAX_VALUE))
    }

    override suspend fun presignGet(tenant: TenantRef, key: String): ApiResult<String> = ApiResult.Ok("https://r2/get")

    override suspend fun put(url: String, file: File, mime: String): PutOutcome {
        puts += url
        return if (online) PutOutcome.Ok else PutOutcome.Offline
    }

    override suspend fun download(url: String, dest: File): Boolean = false
}

/**
 * R08 offline scenario on a database file that is closed and reopened, the way a killed process comes back:
 * photos taken offline stay queued, upload once each when the network returns, and their R2 key reaches the photo
 * row exactly once, even when the process dies between the PUT and recording the key.
 */
// AC-F54 (queue survives process death; no duplicate uploads), AC-F55 (photos taken offline sync once back online)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class OfflineMediaScenarioTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var dbFile: File
    private lateinit var db: HomeRecordDatabase
    private val net = SwitchableNetwork()
    private var clock = 1_000_000L
    private val changes = mutableListOf<Pair<String, String>>()

    @Before fun setUp() {
        dbFile = File(tmp.root, "homerecord.db")
        db = open()
        runBlocking { db.jobDao().upsert(JobEntity(id = "j1", companyId = "c1", name = "Garage subpanel", createdAt = 1, updatedAt = 1)) }
    }

    @After fun tearDown() = db.close()

    private fun open(): HomeRecordDatabase =
        Room.databaseBuilder(context, HomeRecordDatabase::class.java, dbFile.path).allowMainThreadQueries().build()

    /** Kill the process: drop every object and reopen the database file. */
    private fun restart() {
        db.close()
        db = open()
    }

    private fun uploader(onUploaded: suspend (com.kurtz.homerecord.core.data.MediaUploadEntity, Presigned) -> Unit = { row, p ->
        UploadedKeys(db, { clock }, { e, id -> changes += e to id }).record(row.entity, row.entityId, p.key.ifEmpty { row.key.orEmpty() })
    }) = MediaUploader(db.mediaUploadDao(), net, { clock }, onUploaded = onUploaded)

    private suspend fun takePhotos(n: Int): List<String> {
        val queue = uploader()
        val photos = JobPhotos(db, { t, e, id, name, path, mime -> queue.enqueue(t, e, id, name, path, mime) }, { "c1" }, { clock })
        return (1..n).map { i ->
            val file = tmp.newFile("shot$i.jpg").also { it.writeText("jpeg $i") }
            checkNotNull(photos.save(file.path, CaptureTags("j1"), fix = null))
        }
    }

    @Test fun photosTakenOfflineUploadOnceEachAfterARestart() = runBlocking<Unit> {
        val ids = takePhotos(3)
        // Offline: the first PUT fails, the pass stops and nothing counts against the files.
        val offlinePass = uploader().runPending()
        assertThat(offlinePass.uploaded).isEqualTo(0)
        assertThat(net.puts).hasSize(1)

        restart()
        net.online = true
        // Files not tried yet stay due at once; the one that failed waits out its backoff.
        assertThat(offlinePass.retryAt).isEqualTo(0L)
        clock += MediaUploader.backoffMs(1)
        val queue = uploader()
        queue.recover()
        assertThat(queue.runPending().uploaded).isEqualTo(3)
        // One extra PUT only for the try that failed offline; no file goes twice once online, and its signed URL is reused.
        assertThat(net.puts).hasSize(4)
        assertThat(net.presigns).isEqualTo(3)
        ids.forEach { assertThat(db.photoDao().get(it)?.r2Key).isEqualTo("c1/photo/$it/${db.photoDao().get(it)?.path?.substringAfterLast('/')}") }
        assertThat(changes.map { it.second }).containsExactlyElementsIn(ids)

        // Another restart and pass: nothing left to send, nothing queued for sync again.
        restart()
        uploader().apply { recover(); runPending() }
        assertThat(net.puts).hasSize(4)
        assertThat(changes).hasSize(3)
    }

    @Test fun dyingBetweenThePutAndTheKeyReplaysOnlyTheKey() = runBlocking<Unit> {
        val (id) = takePhotos(1)
        net.online = true
        val dies = uploader(onUploaded = { _, _ -> throw IllegalStateException("process killed") })
        runCatching { dies.runPending() }
        assertThat(net.puts).hasSize(1)
        assertThat(db.photoDao().get(id)?.r2Key).isNull()

        restart()
        val queue = uploader()
        queue.recover()
        queue.runPending()
        assertThat(net.puts).hasSize(1)
        assertThat(db.photoDao().get(id)?.r2Key).isNotNull()
        assertThat(changes).containsExactly("photo" to id)
        assertThat(db.mediaUploadDao().observePendingCount().first()).isEqualTo(0)
        assertThat(db.mediaUploadDao().observeAllFor("photo").first().map { UploadStatus.of(it.state) }).containsExactly(UploadStatus.Uploaded)
    }

    @Test fun aRowWhosePutFinishedShowsAsUploadedBeforeItsKeyIsRecorded() {
        assertThat(UploadStatus.of(MediaUploader.STATE_UPLOADED)).isEqualTo(UploadStatus.Uploaded)
        assertThat(UploadStatus.of("done")).isEqualTo(UploadStatus.Uploaded)
        assertThat(UploadStatus.of("queued")).isEqualTo(UploadStatus.Waiting)
        assertThat(UploadStatus.of(null)).isEqualTo(UploadStatus.None)
    }
}
