package com.kurtz.homerecord.core.media

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.sync.TenantRef
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/** Answer of `POST /media/presign` (backend/src/routes/media.ts). */
@Serializable
data class Presigned(val key: String, val putUrl: String, val putExpiresAt: Long, val getUrl: String, val getExpiresAt: Long)

@Serializable
data class PresignRequest(val tenantKind: String, val tenantId: String, val entity: String, val entityId: String, val filename: String)

/** How a PUT to R2 ended; [Rejected] means the signed URL was refused (expired), so ask for a new one. */
enum class PutOutcome { Ok, Offline, Rejected, Failed }

/** Network side of the upload queue and the download cache; tests use a fake. */
interface MediaTransferApi {
    suspend fun presign(tenant: TenantRef, entity: String, entityId: String, filename: String): ApiResult<Presigned>
    suspend fun put(url: String, file: File, mime: String): PutOutcome
    suspend fun download(url: String, dest: File): Boolean
}

interface MediaService {
    @POST("media/presign") suspend fun presign(@Body body: PresignRequest): Presigned
}

/** OkHttp's `execute()` blocks, so [put] and [download] run on [io] and are safe to call from Main (S7). */
class RetrofitMediaTransferApi(
    private val service: MediaService,
    private val client: OkHttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : MediaTransferApi {
    override suspend fun presign(tenant: TenantRef, entity: String, entityId: String, filename: String): ApiResult<Presigned> =
        try {
            ApiResult.Ok(service.presign(PresignRequest(tenant.kind.wire, tenant.id, entity, entityId, filename)))
        } catch (e: HttpException) {
            ApiResult.Err(
                when (e.code()) {
                    404 -> ApiFailure.NotFound
                    403 -> ApiFailure.Forbidden
                    400 -> ApiFailure.Invalid
                    else -> ApiFailure.Failed
                },
            )
        } catch (e: IOException) {
            ApiResult.Err(ApiFailure.Offline)
        }

    // The presigned URL carries its own signature, so these go through a client without the bearer interceptor.
    override suspend fun put(url: String, file: File, mime: String): PutOutcome = withContext(io) {
        try {
            val request = Request.Builder().url(url).put(file.asRequestBody(mime.toMediaType())).build()
            client.newCall(request).execute().use {
                when {
                    it.isSuccessful -> PutOutcome.Ok
                    it.code == 403 || it.code == 401 -> PutOutcome.Rejected
                    else -> PutOutcome.Failed
                }
            }
        } catch (e: IOException) {
            PutOutcome.Offline
        }
    }

    override suspend fun download(url: String, dest: File): Boolean = withContext(io) {
        try {
            val tmp = File(dest.parentFile, dest.name + ".part")
            val ok = client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) tmp.outputStream().use { out -> r.body.byteStream().copyTo(out) }
                r.isSuccessful
            }
            ok && tmp.renameTo(dest)
        } catch (e: IOException) {
            false
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String, authed: OkHttpClient, plain: OkHttpClient = OkHttpClient()): RetrofitMediaTransferApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .client(authed)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitMediaTransferApi(retrofit.create(MediaService::class.java), plain)
        }
    }
}
