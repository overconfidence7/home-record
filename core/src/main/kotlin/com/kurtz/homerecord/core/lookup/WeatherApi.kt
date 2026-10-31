package com.kurtz.homerecord.core.lookup

import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.model.LogWeather
import com.kurtz.homerecord.core.net.apiCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * The day's weather at a job from the backend's Open-Meteo proxy (AC-F31, AC-B32). Offline when there is no network;
 * NotFound when the day is too far ahead for a forecast; Failed when the upstream lookup failed (502).
 */
fun interface WeatherApi {
    suspend fun day(lat: Double, lng: Double, date: String): ApiResult<LogWeather>
}

@Serializable
internal data class WeatherBody(
    val date: String = "",
    val highF: Double? = null,
    val lowF: Double? = null,
    val precipIn: Double? = null,
    val conditions: String = "unknown",
)

internal interface WeatherService {
    @GET("weather") suspend fun weather(@Query("lat") lat: Double, @Query("lng") lng: Double, @Query("date") date: String): WeatherBody
}

class RetrofitWeatherApi internal constructor(
    private val service: WeatherService,
    private val now: () -> Long,
) : WeatherApi {
    override suspend fun day(lat: Double, lng: Double, date: String): ApiResult<LogWeather> = apiCall {
        val r = service.weather(lat, lng, date)
        LogWeather(
            highF = r.highF?.let { Math.round(it).toInt() },
            lowF = r.lowF?.let { Math.round(it).toInt() },
            precipIn = r.precipIn,
            conditions = r.conditions.ifBlank { "unknown" },
            fetchedAt = now(),
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String, client: Call.Factory, now: () -> Long = System::currentTimeMillis): RetrofitWeatherApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .callFactory(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitWeatherApi(retrofit.create(WeatherService::class.java), now)
        }
    }
}
