package com.kurtz.homerecord.core.photo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.MediaUploadEntity
import com.kurtz.homerecord.core.media.MediaCache
import com.kurtz.homerecord.core.media.MediaCacheSettings
import com.kurtz.homerecord.core.media.MediaTransferApi
import com.kurtz.homerecord.core.media.Presigned
import com.kurtz.homerecord.core.media.PutOutcome
import com.kurtz.homerecord.core.sync.TenantKind
import com.kurtz.homerecord.core.sync.TenantRef
import java.io.File
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

private class Net : MediaTransferApi {
    var offline = false
    var getFailure: ApiFailure? = null
    val downloads = mutableListOf<String>()
    val signedGets = mutableListOf<String>()
    override suspend fun presign(tenant: TenantRef, entity: String, entityId: String, filename: String, size: Long, mime: String): ApiResult<Presigned> =
        error("downloads never sign a PUT")
    override suspend fun presignGet(tenant: TenantRef, key: String): ApiResult<String> {
        signedGets += key
        if (offline) return ApiResult.Err(ApiFailure.Offline)
        return getFailure?.let { ApiResult.Err(it) } ?: ApiResult.Ok("https://r2/get/${key.substringAfterLast('/')}")
    }
    override suspend fun put(url: String, file: File, mime: String) = PutOutcome.Ok
    override suspend fun download(url: String, dest: File): Boolean {
        downloads += url
        dest.writeText("pdf-bytes")
        return true
    }
}

// AC-F19 (upload status on the row), AC-F20 (download on open, Available offline, storage and Free up)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class JobFileTransfersTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: HomeRecordDatabase
    private val net = Net()
    private lateinit var jobFiles: JobFiles
    private lateinit var transfers: JobFileTransfers
    private lateinit var offlineDir: File
    private var signedIn: TenantRef? = TenantRef(TenantKind.Company, "c1")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
            .allowMainThreadQueries().build()
        jobFiles = JobFiles(db)
        offlineDir = File(tmp.root, "offline")
        val settings = object : MediaCacheSettings { override var capBytes = 1_000_000L }
        val cache = MediaCache(File(tmp.root, "cache"), settings, net)
        transfers = JobFileTransfers(db, net, cache, offlineDir, { signedIn }, jobFiles)
    }

    @After fun tearDown() = db.close()

    /** A file another member uploaded: a record with a key but no bytes here. */
    private suspend fun remote(): String {
        val row = jobFiles.add("j1", "", "Plans.pdf", "/gone", "application/pdf", 9, "Sam")
        jobFiles.setLocal(row.id, false, null)
        jobFiles.setKey(row.id, "c1/job_file/${row.id}/Plans.pdf")
        return row.id
    }

    @Test fun opensARemoteFileByDownloadingItOnce() = runBlocking<Unit> {
        val id = remote()
        assertThat(transfers.ensureOnPhone(id)).isEqualTo(OnPhone.Ready)
        assertThat(transfers.ensureOnPhone(id)).isEqualTo(OnPhone.Ready)
        assertThat(net.downloads).containsExactly("https://r2/get/Plans.pdf")
        val path = db.jobFileDao().get(id)?.path
        assertThat(File(path!!).readText()).isEqualTo("pdf-bytes")
    }

    @Test fun offlineWithoutNetworkAsksToDownloadLater() = runBlocking<Unit> {
        val id = remote()
        net.offline = true
        assertThat(transfers.ensureOnPhone(id)).isEqualTo(OnPhone.NoNetwork)
        assertThat(net.downloads).isEmpty()
    }

    @Test fun downloadSignsAGetForTheRowsKeyAndARefusalIsNotANetworkProblem() = runBlocking<Unit> {
        val id = remote()
        net.getFailure = ApiFailure.NotFound
        assertThat(transfers.ensureOnPhone(id)).isEqualTo(OnPhone.Failed)
        assertThat(net.signedGets).containsExactly(db.jobFileDao().get(id)?.r2Key)
        assertThat(net.downloads).isEmpty()
    }

    @Test fun aFileNeverUploadedHasNothingToDownload() = runBlocking<Unit> {
        val row = jobFiles.add("j1", "", "x.pdf", "/gone", "application/pdf", 9, null)
        assertThat(transfers.ensureOnPhone(row.id)).isEqualTo(OnPhone.NotUploaded)
    }

    @Test fun availableOfflineKeepsTheFileThroughFreeUp() = runBlocking<Unit> {
        val id = remote()
        assertThat(transfers.setAvailableOffline(id, true)).isEqualTo(OnPhone.Ready)
        val row = db.jobFileDao().get(id)!!
        assertThat(row.offline).isTrue()
        assertThat(File(row.path!!).parentFile).isEqualTo(offlineDir)
        assertThat(transfers.storage().offlineBytes).isEqualTo(9)
        transfers.freeUp()
        assertThat(File(db.jobFileDao().get(id)!!.path!!).isFile).isTrue()
    }

    @Test fun freeUpDropsDownloadsAndTheNextOpenDownloadsAgain() = runBlocking<Unit> {
        val id = remote()
        transfers.ensureOnPhone(id)
        assertThat(transfers.storage().cachedBytes).isEqualTo(9)
        assertThat(transfers.freeUp()).isEqualTo(9)
        assertThat(transfers.storage().totalBytes).isEqualTo(0)
        assertThat(db.jobFileDao().get(id)?.path).isNull()
        assertThat(transfers.ensureOnPhone(id)).isEqualTo(OnPhone.Ready)
        assertThat(net.downloads).hasSize(2)
    }

    @Test fun turningOfflineOffHandsTheFileBackToTheCache() = runBlocking<Unit> {
        val id = remote()
        transfers.setAvailableOffline(id, true)
        transfers.setAvailableOffline(id, false)
        assertThat(db.jobFileDao().get(id)?.offline).isFalse()
        assertThat(transfers.storage().offlineBytes).isEqualTo(0)
        assertThat(transfers.freeUp()).isEqualTo(9)
    }

    @Test fun uploadStatusFollowsTheQueueRow() = runBlocking<Unit> {
        val id = remote()
        assertThat(transfers.uploadStatus(id).first()).isEqualTo(UploadStatus.None)
        fun row(state: String) = MediaUploadEntity("u", "company", "c1", "job_file", id, "Plans.pdf", "/p", "application/pdf", state = state, createdAt = 1)
        for ((state, want) in listOf("queued" to UploadStatus.Waiting, "uploading" to UploadStatus.Uploading, "failed" to UploadStatus.Failed, "done" to UploadStatus.Uploaded)) {
            db.mediaUploadDao().save(row(state))
            assertThat(transfers.uploadStatus(id).first()).isEqualTo(want)
        }
    }
}
