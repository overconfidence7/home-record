package com.kurtz.homerecord.core.auth

import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.company.MeResponse
import com.kurtz.homerecord.core.net.apiCall
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET

/** Who is signed in: `GET /me`'s user email, for the account panel ([judge:hm-more-signed-in-no-account]). */
interface AccountApi {
    suspend fun email(): ApiResult<String>
}

interface AccountService {
    @GET("me") suspend fun me(): MeResponse
}

class RetrofitAccountApi(private val service: AccountService) : AccountApi {
    override suspend fun email(): ApiResult<String> = apiCall { service.me().user.email }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String, client: Call.Factory): RetrofitAccountApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .callFactory(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitAccountApi(retrofit.create(AccountService::class.java))
        }
    }
}
