package com.kurtz.homerecord.core.lookup

import android.util.Base64
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/** What the vision lookup read off a data plate (AC-B11); any field may be missing. */
data class PlateReading(val make: String?, val model: String?, val serial: String?, val manufacturedOn: String?)

/** Fallback for when on-device OCR finds no model (AC-B11). Sends a downscaled photo; the server never stores it. */
interface DataPlateVisionApi {
    suspend fun read(path: String): ApiResult<PlateReading>
}

/** One recall from the CPSC feed (AC-B12). [modelMatch] false means the text only names the make, not that the item is safe. */
data class RecallInfo(
    val recallNumber: String,
    val title: String,
    val recallDate: String?,
    val hazard: String?,
    val remedy: String?,
    val url: String?,
    val modelMatch: Boolean,
)

interface RecallApi {
    suspend fun check(make: String, model: String?): ApiResult<List<RecallInfo>>
}

@Serializable internal data class PlateRequestBody(val imageBase64: String, val mediaType: String = "image/jpeg")

@Serializable
internal data class PlateResponseBody(
    val make: String? = null,
    val model: String? = null,
    val serial: String? = null,
    val manufacturedOn: String? = null,
)

@Serializable
internal data class RecallBody(
    val recallNumber: String = "",
    val title: String = "",
    val recallDate: String? = null,
    val hazard: String? = null,
    val remedy: String? = null,
    val url: String? = null,
    val modelMatch: Boolean = false,
)

@Serializable internal data class RecallsResponseBody(val recalls: List<RecallBody> = emptyList())

internal interface LookupService {
    @POST("lookup/dataplate") suspend fun dataPlate(@Body body: PlateRequestBody): PlateResponseBody
    @GET("recalls") suspend fun recalls(@Query("make") make: String, @Query("model") model: String?): RecallsResponseBody
}

private fun Throwable.toFailure(): ApiFailure? = when (this) {
    is HttpException -> if (code() == 400) ApiFailure.Invalid else ApiFailure.Failed
    is IOException -> ApiFailure.Offline
    else -> null
}

/**
 * Retrofit side of both lookups. [encodeJpeg] turns a stored photo into downscaled JPEG bytes (null when unreadable),
 * so the Android bitmap work stays out of this class and tests can pass bytes directly.
 */
class RetrofitLookupApi internal constructor(
    private val service: LookupService,
    private val encodeJpeg: (String) -> ByteArray?,
) : DataPlateVisionApi, RecallApi {
    override suspend fun read(path: String): ApiResult<PlateReading> {
        val bytes = encodeJpeg(path) ?: return ApiResult.Err(ApiFailure.Invalid)
        return try {
            val r = service.dataPlate(PlateRequestBody(Base64.encodeToString(bytes, Base64.NO_WRAP)))
            ApiResult.Ok(PlateReading(r.make, r.model, r.serial, r.manufacturedOn))
        } catch (e: Exception) {
            ApiResult.Err(e.toFailure() ?: throw e)
        }
    }

    override suspend fun check(make: String, model: String?): ApiResult<List<RecallInfo>> = try {
        val r = service.recalls(make.trim(), model?.trim()?.ifEmpty { null })
        ApiResult.Ok(r.recalls.map { RecallInfo(it.recallNumber, it.title, it.recallDate, it.hazard, it.remedy, it.url, it.modelMatch) })
    } catch (e: Exception) {
        ApiResult.Err(e.toFailure() ?: throw e)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String, client: OkHttpClient, encodeJpeg: (String) -> ByteArray?): RetrofitLookupApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitLookupApi(retrofit.create(LookupService::class.java), encodeJpeg)
        }
    }
}
