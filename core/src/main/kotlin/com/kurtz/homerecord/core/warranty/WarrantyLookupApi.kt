package com.kurtz.homerecord.core.warranty

import com.kurtz.homerecord.core.net.invalidOrFailed
import com.kurtz.homerecord.core.net.apiCall
import com.kurtz.homerecord.core.company.ApiResult
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

/** Terms returned by the warranty lookup (AC-B10), in the units the Warranty row stores. */
data class WarrantyTerms(
    val partsMonths: Int? = null,
    val laborMonths: Int? = null,
    val specialTerms: List<String> = emptyList(),
    val registrationRequired: Boolean = false,
    val registrationUrl: String? = null,
    val manualUrl: String? = null,
    val sourceUrl: String? = null,
    /** `HIGH`, `MEDIUM` or `LOW`. */
    val confidence: String = "LOW",
    val summary: String = "",
)

/** Network side of the warranty lookup; [RetrofitWarrantyLookupApi] is the real one, tests use a fake. */
interface WarrantyLookupApi {
    suspend fun lookup(make: String, model: String, category: String?, serial: String?): ApiResult<WarrantyTerms>
}

@Serializable
internal data class WarrantyRequestBody(val make: String, val model: String, val category: String? = null, val serial: String? = null)

@Serializable
internal data class WarrantyResponseBody(
    val partsYears: Double? = null,
    val laborYears: Double? = null,
    val specialTerms: List<String> = emptyList(),
    val registrationRequired: Boolean = false,
    val registrationUrl: String? = null,
    val manualUrl: String? = null,
    val sourceUrl: String? = null,
    val confidence: String = "low",
    val summary: String = "",
)

internal interface WarrantyLookupService {
    @POST("lookup/warranty") suspend fun lookup(@Body body: WarrantyRequestBody): WarrantyResponseBody
}

class RetrofitWarrantyLookupApi internal constructor(private val service: WarrantyLookupService) : WarrantyLookupApi {
    override suspend fun lookup(make: String, model: String, category: String?, serial: String?): ApiResult<WarrantyTerms> =
        apiCall(::invalidOrFailed) {
            val r = service.lookup(WarrantyRequestBody(make.trim(), model.trim(), category?.trim()?.ifEmpty { null }, serial?.trim()?.ifEmpty { null }))
            WarrantyTerms(
                partsMonths = r.partsYears?.let { (it * 12).roundToInt() },
                laborMonths = r.laborYears?.let { (it * 12).roundToInt() },
                specialTerms = r.specialTerms,
                registrationRequired = r.registrationRequired,
                registrationUrl = r.registrationUrl,
                manualUrl = r.manualUrl,
                sourceUrl = r.sourceUrl,
                confidence = r.confidence.uppercase().takeIf { it in setOf("HIGH", "MEDIUM", "LOW") } ?: "LOW",
                summary = r.summary,
            )
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String, client: Call.Factory): RetrofitWarrantyLookupApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .callFactory(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitWarrantyLookupApi(retrofit.create(WarrantyLookupService::class.java))
        }
    }
}
