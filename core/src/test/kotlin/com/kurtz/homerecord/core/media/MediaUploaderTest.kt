package com.kurtz.homerecord.core.media

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeTransfer : MediaTransferApi {
    var presigns = 0
    val puts = mutableListOf<String>()
    var presignResult: ApiResult<Presigned>? = null
    val putOutcomes = ArrayDeque<PutOutcome>()
    val downloads = mutableListOf<String>()
    var downloadOk = true
    var expiresAt = Long.MAX_VALUE

    val signed = mutableListOf<Pair<Long, String>>()

    override suspend fun presign(tenant: TenantRef, entity: String, entityId: String, filename: String, size: Long, mime: String): ApiResult<Presigned> {
        presigns++
        signed += size to mime
        return presignResult ?: ApiResult.Ok(Presigned("${tenant.id}/$entity/$entityId/$filename", "https://r2/put/$presigns", expiresAt))
    }

    override suspend fun presignGet(tenant: TenantRef, key: String): ApiResult<String> = ApiResult.Ok("https://r2/get")

    override suspend fun put(url: String, file: File, mime: String): PutOutcome {
        puts += url
        return putOutcomes.removeFirstOrNull() ?: PutOutcome.Ok
    }

    override suspend fun download(url: String, dest: File): Boolean {
        downloads += url
        if (downloadOk) dest.writeText("x".repeat(100))
        return downloadOk
    }
}

// AC-F54 (presigned upload with retry and resume; queue survives process death; no duplicate uploads)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MediaUploaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: HomeRecordDatabase
    private val net = FakeTransfer()
    private var clock = 1_000_000L
    private val tenant = TenantRef(TenantKind.Company, "c1")
    private lateinit var photo: File
    private var ids = 0
    private lateinit var uploader: MediaUploader

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        photo = tmp.newFile("a.jpg").also { it.writeText("jpeg") }
        uploader = MediaUploader(db.mediaUploadDao(), net, { clock }, { "u${++ids}" })
    }

    @After fun tearDown() = db.close()

    private suspend fun enqueue(name: String = "a.jpg") = uploader.enqueue(tenant, "photo", "p1", name, photo.path, "image/jpeg")

    @Test fun uploadsThroughPresignedUrl() = runBlocking<Unit> {
        val id = enqueue()
        val pass = uploader.runPending()
        assertThat(pass.uploaded).isEqualTo(1)
        assertThat(net.puts).containsExactly("https://r2/put/1")
        assertThat(db.mediaUploadDao().get(id)?.state).isEqualTo("done")
        assertThat(db.mediaUploadDao().get(id)?.key).isEqualTo("c1/photo/p1/a.jpg")
        // The PUT is signed for this file's exact size and type (cr:presign-size).
        assertThat(net.signed).containsExactly(4L to "image/jpeg")
    }

    @Test fun queuingTheSameFileTwiceUploadsOnce() = runBlocking<Unit> {
        val a = enqueue()
        val b = enqueue()
        assertThat(b).isEqualTo(a)
        uploader.runPending()
        uploader.runPending()
        assertThat(net.puts).hasSize(1)
    }

    @Test fun offlineKeepsFileQueuedAndRetriesAfterBackoff() = runBlocking<Unit> {
        val id = enqueue()
        net.putOutcomes += PutOutcome.Offline
        val first = uploader.runPending()
        assertThat(first.uploaded).isEqualTo(0)
        assertThat(first.retryAt).isGreaterThan(clock)
        assertThat(db.mediaUploadDao().get(id)?.attempts).isEqualTo(0)
        // Not due yet: nothing happens.
        assertThat(uploader.runPending().uploaded).isEqualTo(0)
        assertThat(net.puts).hasSize(1)
        clock = checkNotNull(first.retryAt)
        assertThat(uploader.runPending().uploaded).isEqualTo(1)
        // The signed URL from the first try is reused, not asked for again.
        assertThat(net.presigns).isEqualTo(1)
        assertThat(net.puts).hasSize(2)
    }

    @Test fun queueSurvivesProcessDeathMidUpload() = runBlocking<Unit> {
        val id = enqueue()
        db.mediaUploadDao().save(checkNotNull(db.mediaUploadDao().get(id)).copy(state = "uploading"))
        // New process: a fresh uploader over the same database.
        val restarted = MediaUploader(db.mediaUploadDao(), net, { clock })
        restarted.recover()
        assertThat(db.mediaUploadDao().get(id)?.state).isEqualTo("queued")
        assertThat(restarted.runPending().uploaded).isEqualTo(1)
        assertThat(net.puts).hasSize(1)
    }

    @Test fun expiredUrlIsReplacedBeforeUpload() = runBlocking<Unit> {
        net.expiresAt = clock + 1_000
        enqueue()
        net.putOutcomes += PutOutcome.Failed
        uploader.runPending()
        clock += 10 * 60_000
        net.expiresAt = Long.MAX_VALUE
        uploader.runPending()
        assertThat(net.presigns).isEqualTo(2)
    }

    @Test fun rejectedSignatureAsksForAFreshUrlAtOnce() = runBlocking<Unit> {
        val id = enqueue()
        net.putOutcomes += PutOutcome.Rejected
        uploader.runPending()
        assertThat(db.mediaUploadDao().get(id)?.putUrl).isNull()
        assertThat(uploader.runPending().uploaded).isEqualTo(1)
        assertThat(net.presigns).isEqualTo(2)
    }

    // cr:upload-rejected-loop
    @Test fun aFreshUrlTheServerKeepsRefusingStopsAfterTheLimit() = runBlocking<Unit> {
        val id = enqueue()
        repeat(MediaUploader.MAX_ATTEMPTS) { net.putOutcomes += PutOutcome.Rejected }
        repeat(MediaUploader.MAX_ATTEMPTS + 2) { uploader.runPending() }
        assertThat(db.mediaUploadDao().get(id)?.state).isEqualTo("failed")
        assertThat(net.puts).hasSize(MediaUploader.MAX_ATTEMPTS)
        uploader.retryFailed()
        assertThat(uploader.runPending().uploaded).isEqualTo(1)
    }

    @Test fun aRefusedReusedUrlDoesNotCountAgainstTheFile() = runBlocking<Unit> {
        val id = enqueue()
        net.putOutcomes += PutOutcome.Failed
        uploader.runPending()
        assertThat(db.mediaUploadDao().get(id)?.attempts).isEqualTo(1)
        clock += 10 * 60_000
        net.putOutcomes += PutOutcome.Rejected
        uploader.runPending()
        assertThat(db.mediaUploadDao().get(id)?.attempts).isEqualTo(1)
        assertThat(db.mediaUploadDao().get(id)?.state).isEqualTo("queued")
    }

    @Test fun repeatedServerFailureStopsThenRetryFailedRequeues() = runBlocking<Unit> {
        val id = enqueue()
        repeat(MediaUploader.MAX_ATTEMPTS) {
            net.putOutcomes += PutOutcome.Failed
            clock += 2 * 60 * 60_000
            uploader.runPending()
        }
        assertThat(db.mediaUploadDao().get(id)?.state).isEqualTo("failed")
        uploader.retryFailed()
        assertThat(uploader.runPending().uploaded).isEqualTo(1)
    }

    @Test fun missingFileFailsWithoutNetwork() = runBlocking<Unit> {
        val id = uploader.enqueue(tenant, "photo", "p2", "gone.jpg", File(tmp.root, "gone.jpg").path, "image/jpeg")
        val pass = uploader.runPending()
        assertThat(pass.failed).isEqualTo(1)
        assertThat(db.mediaUploadDao().get(id)?.state).isEqualTo("failed")
        assertThat(net.presigns).isEqualTo(0)
    }

    @Test fun noMembershipOnServerGivesUp() = runBlocking<Unit> {
        val id = enqueue()
        net.presignResult = ApiResult.Err(ApiFailure.NotFound)
        assertThat(uploader.runPending().failed).isEqualTo(1)
        assertThat(db.mediaUploadDao().get(id)?.state).isEqualTo("failed")
    }

    @Test fun aFileTooLargeOrRefusedByTheServerGivesUp() = runBlocking<Unit> {
        // 413 (over the size cap) and 403 (someone else's object) come back as Invalid and Forbidden: retrying cannot help.
        for (failure in listOf(ApiFailure.Invalid, ApiFailure.Forbidden)) {
            val id = enqueue("${failure.name}.jpg")
            net.presignResult = ApiResult.Err(failure)
            assertThat(uploader.runPending().failed).isEqualTo(1)
            assertThat(db.mediaUploadDao().get(id)?.state).isEqualTo("failed")
            assertThat(net.puts).isEmpty()
        }
    }

    @Test fun backoffDoublesAndCaps() {
        assertThat(MediaUploader.backoffMs(1)).isEqualTo(30_000)
        assertThat(MediaUploader.backoffMs(3)).isEqualTo(120_000)
        assertThat(MediaUploader.backoffMs(20)).isEqualTo(3_600_000)
    }

    // AC-F54: downloads cache to app storage, cap configurable
    @Test fun cacheDownloadsOnceAndEvictsOldestOverCap() = runBlocking<Unit> {
        var cap = 250L
        val settings = object : MediaCacheSettings { override var capBytes: Long
            get() = cap
            set(v) { cap = v } }
        val cache = MediaCache(tmp.newFolder("cache"), settings, net)
        val a = checkNotNull(cache.open("c1/p/a.jpg") { "https://r2/get/a" })
        a.setLastModified(1_000)
        val b = checkNotNull(cache.open("c1/p/b.jpg") { "https://r2/get/b" })
        b.setLastModified(2_000)
        assertThat(cache.open("c1/p/b.jpg") { error("cached") }).isEqualTo(b)
        assertThat(net.downloads).hasSize(2)
        cache.open("c1/p/c.jpg") { "https://r2/get/c" }
        assertThat(a.exists()).isFalse()
        assertThat(b.exists()).isTrue()
        assertThat(cache.sizeBytes()).isAtMost(250)
    }

    @Test fun freeUpKeepsAvailableOfflineFiles() = runBlocking<Unit> {
        val settings = object : MediaCacheSettings { override var capBytes = 10_000L }
        val cache = MediaCache(tmp.newFolder("cache2"), settings, net, pinned = { setOf("c1/p/keep.pdf") })
        cache.open("c1/p/keep.pdf") { "u1" }
        cache.open("c1/p/other.jpg") { "u2" }
        assertThat(cache.freeUp()).isEqualTo(100)
        assertThat(cache.fileFor("c1/p/keep.pdf").exists()).isTrue()
        assertThat(cache.fileFor("c1/p/other.jpg").exists()).isFalse()
    }

    @Test fun failedDownloadLeavesNothingBehind() = runBlocking<Unit> {
        net.downloadOk = false
        val cache = MediaCache(tmp.newFolder("cache3"), object : MediaCacheSettings { override var capBytes = 1L }, net)
        assertThat(cache.open("c1/p/x.jpg") { "u" }).isNull()
        assertThat(cache.sizeBytes()).isEqualTo(0)
    }
}
