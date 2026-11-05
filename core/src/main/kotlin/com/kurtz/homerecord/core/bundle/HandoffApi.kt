package com.kurtz.homerecord.core.bundle

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.net.apiCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.File

/** A handoff the server accepted (AC-B40): the claim link went to [email] and works until [expiresAt]. */
data class SentHandoff(val email: String, val expiresAt: Long)

/** Why "Send to homeowner" did not go out. */
enum class HandoffSendFailure { Offline, TooLarge, Failed }

/** "Send to homeowner" (AC-F51, AC-B40): uploads the `.homerecord` and the server emails the claim link. */
fun interface HandoffApi {
    suspend fun send(companyId: String, jobId: String?, email: String, homeName: String, bundle: File): HandoffSendResult
}

sealed interface HandoffSendResult {
    data class Sent(val handoff: SentHandoff) : HandoffSendResult
    data class Failed(val failure: HandoffSendFailure) : HandoffSendResult
}

/** The address rule the screen applies before sending: something@something.tld, no spaces. */
object HandoffEmail {
    private val shape = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    fun normalize(raw: String): String = raw.trim().lowercase()
    fun isValid(raw: String): Boolean = normalize(raw).let { it.length <= 254 && shape.matches(it) }
}

@Serializable
internal data class HandoffResponseBody(val id: String, val email: String, val expiresAt: Long, val size: Long = 0)

internal interface HandoffService {
    @POST("handoff")
    suspend fun send(
        @Query("companyId") companyId: String,
        @Query("jobId") jobId: String?,
        @Query("email") email: String,
        @Query("homeName") homeName: String,
        @Body body: RequestBody,
    ): HandoffResponseBody
}

class RetrofitHandoffApi internal constructor(private val service: HandoffService) : HandoffApi {
    override suspend fun send(companyId: String, jobId: String?, email: String, homeName: String, bundle: File): HandoffSendResult {
        // 413: above the server's 100 MB request cap. Anything else the server refuses is not something a retry fixes
        // either, but the person can still Export the file, so it reads as a plain failure.
        val result = apiCall({ e -> if (e.code() == 413) ApiFailure.Invalid else ApiFailure.Failed }) {
            service.send(companyId, jobId, HandoffEmail.normalize(email), homeName.trim(), bundle.asRequestBody(ZIP))
        }
        return when (result) {
            is ApiResult.Ok -> HandoffSendResult.Sent(SentHandoff(result.value.email, result.value.expiresAt))
            is ApiResult.Err -> HandoffSendResult.Failed(
                when (result.failure) {
                    ApiFailure.Offline -> HandoffSendFailure.Offline
                    ApiFailure.Invalid -> HandoffSendFailure.TooLarge
                    else -> HandoffSendFailure.Failed
                },
            )
        }
    }

    companion object {
        private val ZIP = "application/zip".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }

        /** [client] must add the session token (the server answers 401 otherwise). */
        fun create(baseUrl: String, client: Call.Factory): RetrofitHandoffApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .callFactory(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitHandoffApi(retrofit.create(HandoffService::class.java))
        }
    }
}
