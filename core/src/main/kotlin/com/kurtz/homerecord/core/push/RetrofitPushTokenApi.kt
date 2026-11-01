package com.kurtz.homerecord.core.push

import com.kurtz.homerecord.core.net.apiCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.PUT

@Serializable
data class PushTokenBody(val token: String, val app: String)

@Serializable
data class PushTokenDelete(val token: String)

interface PushTokenService {
    @PUT("me/push-token") suspend fun register(@Body body: PushTokenBody)

    @HTTP(method = "DELETE", path = "me/push-token", hasBody = true) suspend fun unregister(@Body body: PushTokenDelete)
}

class RetrofitPushTokenApi(private val service: PushTokenService) : PushTokenApi {
    override suspend fun register(token: String, app: String) = apiCall { service.register(PushTokenBody(token, app)) }

    override suspend fun unregister(token: String) = apiCall { service.unregister(PushTokenDelete(token)) }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String, authed: Call.Factory): RetrofitPushTokenApi = RetrofitPushTokenApi(
            Retrofit.Builder()
                .baseUrl(baseUrl.trimEnd('/') + "/")
                .callFactory(authed)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(PushTokenService::class.java),
        )
    }
}
