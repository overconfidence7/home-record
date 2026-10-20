package com.kurtz.homerecord.core.sync

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import java.io.IOException
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Network side of the sync engine (AC-B20); tests use a fake server instead. */
interface SyncApi {
    suspend fun push(tenant: TenantRef, deviceId: String, ops: List<PushOp>): ApiResult<PushResponse>
    suspend fun pull(tenant: TenantRef, since: Long): ApiResult<PullResponse>
}

interface SyncService {
    @POST("{kind}/{id}/sync/push") suspend fun push(@Path("kind") kind: String, @Path("id") id: String, @Body body: PushRequest): PushResponse

    @GET("{kind}/{id}/sync/pull") suspend fun pull(@Path("kind") kind: String, @Path("id") id: String, @Query("since") since: Long): PullResponse
}

class RetrofitSyncApi(private val service: SyncService) : SyncApi {
    override suspend fun push(tenant: TenantRef, deviceId: String, ops: List<PushOp>) =
        call { service.push(tenant.kind.path, tenant.id, PushRequest(deviceId, ops)) }

    override suspend fun pull(tenant: TenantRef, since: Long) = call { service.pull(tenant.kind.path, tenant.id, since) }

    private suspend fun <T> call(block: suspend () -> T): ApiResult<T> =
        try {
            ApiResult.Ok(block())
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

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String, client: OkHttpClient): RetrofitSyncApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitSyncApi(retrofit.create(SyncService::class.java))
        }
    }
}
