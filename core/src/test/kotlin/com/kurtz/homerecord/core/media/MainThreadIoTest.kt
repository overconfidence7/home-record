package com.kurtz.homerecord.core.media

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.core.data.JobFileEntity
import com.kurtz.homerecord.core.sync.RoomRowStore
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Counts the blocks sent to IO, so a test can tell a call left the main thread. */
private class CountingDispatcher(private val delegate: CoroutineDispatcher = Dispatchers.IO) : CoroutineDispatcher() {
    val dispatched = AtomicInteger()
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatched.incrementAndGet()
        delegate.dispatch(context, block)
    }
}

/**
 * Network and file work in :core never runs on the caller's thread when that is Main (S7, cr:core-main-io): opening a
 * file that is not on the phone used to throw NetworkOnMainThreadException (AC-F19).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainThreadIoTest {
    @get:Rule val tmp = TemporaryFolder()

    private val ioThread = Executors.newSingleThreadExecutor { Thread(it, "test-io") }
    private val io = ioThread.asCoroutineDispatcher()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HomeRecordDatabase::class.java)
        .allowMainThreadQueries().build()

    @After fun tearDown() {
        db.close()
        ioThread.shutdownNow()
    }

    /** A client whose calls never touch the network and remember the thread they ran on. */
    private fun recordingClient(threads: MutableList<String>) = OkHttpClient.Builder().addInterceptor { chain ->
        threads += Thread.currentThread().name
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("pdf-bytes".toResponseBody()).build()
    }.build()

    // AC-F19
    @Test fun uploadAndDownloadRunOnTheIoDispatcher() = runTest {
        val threads = mutableListOf<String>()
        val api = RetrofitMediaTransferApi(mediaService(), recordingClient(threads), io)
        val src = tmp.newFile("a.jpg").apply { writeText("jpg") }
        val dest = File(tmp.newFolder("cache"), "plan.pdf")

        assertThat(api.put("https://r2/put", src, "image/jpeg")).isEqualTo(PutOutcome.Ok)
        assertThat(api.download("https://r2/get", dest)).isTrue()

        assertThat(threads).containsExactly("test-io", "test-io")
        assertThat(dest.readText()).isEqualTo("pdf-bytes")
    }

    // AC-F19, AC-F20
    @Test fun cacheChecksAndDeletesFilesOnTheIoDispatcher() = runTest {
        val counting = CountingDispatcher()
        val net = object : MediaTransferApi {
            override suspend fun presign(
                tenant: com.kurtz.homerecord.core.sync.TenantRef,
                entity: String,
                entityId: String,
                filename: String,
                size: Long,
                mime: String,
            ): ApiResult<Presigned> = error("not used")
            override suspend fun presignGet(tenant: com.kurtz.homerecord.core.sync.TenantRef, key: String): ApiResult<String> =
                error("not used")
            override suspend fun put(url: String, file: File, mime: String) = PutOutcome.Ok
            override suspend fun download(url: String, dest: File): Boolean { dest.writeText("x"); return true }
        }
        val cache = MediaCache(tmp.newFolder("downloads"), object : MediaCacheSettings { override var capBytes = 100L }, net, io = counting)

        assertThat(cache.open("c1/job_file/f1/plan.pdf") { "https://r2/get" }).isNotNull()
        assertThat(cache.sizeBytes()).isEqualTo(1)
        assertThat(cache.freeUp()).isEqualTo(1)
        assertThat(counting.dispatched.get()).isAtLeast(4)
    }

    // AC-F53
    @Test fun rowStoreLeavesMainButQueriesInPlaceInsideATransaction() = runTest {
        val counting = CountingDispatcher()
        val store = RoomRowStore(db, counting)
        db.jobFileDao().upsert(JobFileEntity(id = "f1", jobId = "j1", name = "Panel schedule.pdf", mime = "application/pdf", createdAt = 1, updatedAt = 1))

        assertThat(store.read("job_file", "f1")?.get("name")?.jsonPrimitive?.content).isEqualTo("Panel schedule.pdf")
        assertThat(counting.dispatched.get()).isAtLeast(1)

        // A repository records a change inside its own transaction: the store must see the uncommitted row, not wait on it.
        val seen = db.withTransaction {
            db.jobFileDao().upsert(JobFileEntity(id = "f2", jobId = "j1", name = "Permit.pdf", mime = "application/pdf", createdAt = 2, updatedAt = 2))
            store.read("job_file", "f2")
        }
        assertThat(seen?.get("name")).isEqualTo(JsonPrimitive("Permit.pdf"))
    }

    private fun mediaService() = object : MediaService {
        override suspend fun presign(body: PresignRequest) = error("not used")
        override suspend fun presignGet(body: PresignGetRequest) = error("not used")
    }
}
