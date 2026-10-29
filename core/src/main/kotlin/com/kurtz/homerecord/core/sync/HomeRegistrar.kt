package com.kurtz.homerecord.core.sync

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.PUT
import retrofit2.http.Path

/** `PUT /homes/:id` (backend/src/routes/tenants.ts): creates the home with the caller as owner, or confirms membership. */
interface HomeRegistrationApi {
    suspend fun register(homeId: String, name: String): ApiResult<Unit>
}

@Serializable data class RegisterHomeRequest(val name: String)

@Serializable data class RegisteredHome(val id: String, val role: String)

interface HomeRegistrationService {
    @PUT("homes/{id}") suspend fun register(@Path("id") id: String, @Body body: RegisterHomeRequest): RegisteredHome
}

class RetrofitHomeRegistrationApi(private val service: HomeRegistrationService) : HomeRegistrationApi {
    override suspend fun register(homeId: String, name: String): ApiResult<Unit> =
        try {
            service.register(homeId, RegisterHomeRequest(name))
            ApiResult.Ok(Unit)
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

        fun create(baseUrl: String, client: Call.Factory): RetrofitHomeRegistrationApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .callFactory(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitHomeRegistrationApi(retrofit.create(HomeRegistrationService::class.java))
        }
    }
}

/**
 * Makes sure the server knows House Manual's home before a sync pass (AC-H15). The home is made offline with its own
 * id, so the first pass after sign-in registers it; later passes skip the call. A home that has no name yet is
 * registered as [fallbackName]; the pushed home row carries the real name. Company tenants pass straight through.
 */
class HomeRegistrar(
    private val api: HomeRegistrationApi,
    private val nameOf: suspend (homeId: String) -> String?,
    private val fallbackName: String = "Home",
) {
    private val registered = ConcurrentHashMap.newKeySet<String>()

    suspend fun ensure(tenant: TenantRef): ApiResult<Unit> {
        if (tenant.kind != TenantKind.Home || tenant.id in registered) return ApiResult.Ok(Unit)
        val name = nameOf(tenant.id)?.takeIf { it.isNotBlank() } ?: fallbackName
        val result = api.register(tenant.id, name)
        if (result is ApiResult.Ok) registered += tenant.id
        return result
    }

    /** Forget what was registered, so the next pass asks again (sign-out, or another account on this phone). */
    fun reset() {
        registered.clear()
    }
}
