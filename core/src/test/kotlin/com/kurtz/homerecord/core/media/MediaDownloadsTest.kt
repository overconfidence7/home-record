package com.kurtz.homerecord.core.media

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.NoteEntity
import com.kurtz.homerecord.core.data.PhotoEntity
import com.kurtz.homerecord.core.photo.OnPhone
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class R2 : MediaTransferApi {
    var offline = false
    var getFailure: ApiFailure? = null
    val downloads = mutableListOf<String>()
    override suspend fun presign(tenant: TenantRef, entity: String, entityId: String, filename: String, size: Long, mime: String): ApiResult<Presigned> =
        error("downloads never sign a PUT")
    override suspend fun presignGet(tenant: TenantRef, key: String): ApiResult<String> {
        if (offline) return ApiResult.Err(ApiFailure.Offline)
        return getFailure?.let { ApiResult.Err(it) } ?: ApiResult.Ok("https://r2/get/$key")
    }
    override suspend fun put(url: String, file: File, mime: String) = PutOutcome.Ok
    override suspend fun download(url: String, dest: File): Boolean {
        downloads += url
        dest.writeText("jpeg-bytes")
        return true
    }
}

// AC-F55: a photo or voice memo pulled from another phone (path "", r2Key set) downloads by its key, once, and the
// local path it lands at is never queued for sync (cr:sync-local-columns).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class MediaDownloadsTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: HomeRecordDatabase
    private val net = R2()
    private lateinit var cache: MediaCache
    private lateinit var downloads: MediaDownloads
    private var signedIn: TenantRef? = TenantRef(TenantKind.Company, "c1")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        cache = MediaCache(File(tmp.root, "cache"), object : MediaCacheSettings { override var capBytes = 1_000_000L }, net)
        downloads = MediaDownloads(db, net, cache, { signedIn })
    }

    @After fun tearDown() = db.close()

    private suspend fun pulledPhoto(id: String = "p1", key: String? = "c1/photo/$id/IMG_1.jpg") {
        db.photoDao().upsert(PhotoEntity(id = id, homeId = "j1", jobId = "j1", path = "", r2Key = key, takenAt = 1, createdAt = 1, updatedAt = 7))
    }

    @Test fun pulledPhotoDownloadsByKeyAndKeepsUpdatedAt() = runBlocking<Unit> {
        pulledPhoto()
        assertThat(downloads.photo("p1")).isEqualTo(OnPhone.Ready)
        val row = db.photoDao().get("p1")!!
        assertThat(File(row.path).readText()).isEqualTo("jpeg-bytes")
        assertThat(net.downloads).containsExactly("https://r2/get/c1/photo/p1/IMG_1.jpg")
        // A device-local column: the shared record did not change, so nothing is sent back.
        assertThat(row.updatedAt).isEqualTo(7)
        assertThat(db.outboxDao().pending()).isEmpty()
    }

    @Test fun photoOnPhoneIsNotDownloadedAgain() = runBlocking<Unit> {
        val local = File(tmp.root, "IMG_local.jpg").apply { writeText("mine") }
        db.photoDao().upsert(PhotoEntity(id = "p1", homeId = "j1", path = local.absolutePath, r2Key = "k", takenAt = 1, createdAt = 1, updatedAt = 1))
        assertThat(downloads.photo("p1")).isEqualTo(OnPhone.Ready)
        assertThat(net.downloads).isEmpty()
        assertThat(db.photoDao().get("p1")!!.path).isEqualTo(local.absolutePath)
    }

    @Test fun twoAsksForTheSamePhotoFetchItOnce() = runBlocking<Unit> {
        pulledPhoto()
        val results = listOf(async { downloads.photo("p1") }, async { downloads.photo("p1") }).awaitAll()
        assertThat(results).containsExactly(OnPhone.Ready, OnPhone.Ready)
        assertThat(net.downloads).hasSize(1)
    }

    @Test fun droppedFromCacheDownloadsAgain() = runBlocking<Unit> {
        pulledPhoto()
        downloads.photo("p1")
        cache.freeUp()
        assertThat(downloads.missing(listOf(db.photoDao().get("p1")!!.path))).hasSize(1)
        assertThat(downloads.photo("p1")).isEqualTo(OnPhone.Ready)
        assertThat(net.downloads).hasSize(2)
    }

    @Test fun notUploadedOfflineAndFailedLeaveThePathEmpty() = runBlocking<Unit> {
        pulledPhoto("p1", key = null)
        assertThat(downloads.photo("p1")).isEqualTo(OnPhone.NotUploaded)
        pulledPhoto("p2")
        net.offline = true
        assertThat(downloads.photo("p2")).isEqualTo(OnPhone.NoNetwork)
        net.offline = false
        net.getFailure = ApiFailure.Forbidden
        assertThat(downloads.photo("p2")).isEqualTo(OnPhone.Failed)
        signedIn = null
        assertThat(downloads.photo("p2")).isEqualTo(OnPhone.NoNetwork)
        assertThat(downloads.photo("gone")).isEqualTo(OnPhone.Failed)
        assertThat(db.photoDao().get("p2")!!.path).isEmpty()
        assertThat(net.downloads).isEmpty()
    }

    @Test fun pulledVoiceMemoDownloadsByKey() = runBlocking<Unit> {
        db.noteDao().upsert(NoteEntity(id = "n1", jobId = "j1", day = "2026-10-28", r2Key = "c1/note/n1/memo.m4a", createdAt = 1, updatedAt = 3))
        assertThat(downloads.voiceMemo("n1")).isEqualTo(OnPhone.Ready)
        val row = db.noteDao().get("n1")!!
        assertThat(row.voicePath?.let { File(it).isFile }).isTrue()
        assertThat(row.updatedAt).isEqualTo(3)
        assertThat(downloads.voiceMemo("n1")).isEqualTo(OnPhone.Ready)
        assertThat(net.downloads).hasSize(1)
    }

    @Test fun missingListsPathsNotOnThePhone() = runBlocking<Unit> {
        val here = File(tmp.root, "a.jpg").apply { writeText("x") }.absolutePath
        assertThat(downloads.missing(listOf(here, "", null, "/nowhere/b.jpg"))).containsExactly("", null, "/nowhere/b.jpg")
    }
}
