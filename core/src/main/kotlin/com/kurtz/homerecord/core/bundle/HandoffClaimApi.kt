package com.kurtz.homerecord.core.bundle

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.net.apiCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Streaming
import java.io.File
import java.io.IOException

/** The claim link of a handoff (AC-B40): `homerecord://handoff/<token>` from the email page, or the https link itself. */
object HandoffLink {
    private val TOKEN = Regex("^[A-Za-z0-9_-]{32,64}$")

    /** The token of [link], or null when it is not a handoff link. */
    fun token(link: String?): String? {
        val s = link?.trim().orEmpty()
        val rest = when {
            s.startsWith("homerecord://handoff/") -> s.removePrefix("homerecord://handoff/")
            s.startsWith("https://") || s.startsWith("http://") ->
                s.substringAfter("://").substringAfter('/', "").takeIf { it.startsWith("handoff/") }?.removePrefix("handoff/")
            else -> null
        } ?: return null
        val token = rest.substringBefore('?').substringBefore('#').trimEnd('/')
        return token.takeIf { TOKEN.matches(it) }
    }
}

/** A handoff this account now owns: whose home it is and who sent it. */
data class ClaimedHandoff(val homeName: String, val companyName: String, val size: Long)

/** Why a claim or its download did not work. */
enum class HandoffClaimFailure {
    /** No network; a retry may work. */
    Offline,
    /** The link is wrong or the handoff was removed. */
    NotFound,
    /** Older than 30 days. */
    Expired,
    /** Another account claimed it first. */
    ClaimedByOther,
    /** The session ended; sign in again. */
    SignedOut,
    Failed,
}

sealed interface HandoffClaimResult {
    data class Claimed(val handoff: ClaimedHandoff) : HandoffClaimResult
    data class Failed(val failure: HandoffClaimFailure) : HandoffClaimResult
}

sealed interface HandoffDownloadResult {
    data class Saved(val file: File) : HandoffDownloadResult
    data class Failed(val failure: HandoffClaimFailure) : HandoffDownloadResult
}

/** House Manual's side of a handoff (AC-H17, AC-B40): claim the link for this account, then download the bundle. */
interface HandoffClaimApi {
    suspend fun claim(token: String): HandoffClaimResult
    suspend fun download(token: String, dest: File): HandoffDownloadResult
}

@Serializable
internal data class ClaimResponseBody(
    val homeName: String = "",
    val companyName: String = "",
    val size: Long = 0,
    val expiresAt: Long = 0,
)

internal interface HandoffClaimService {
    @POST("handoff/{token}/claim")
    suspend fun claim(@Path("token") token: String): ClaimResponseBody

    @Streaming
    @GET("handoff/{token}/bundle")
    suspend fun bundle(@Path("token") token: String): ResponseBody
}

class RetrofitHandoffClaimApi internal constructor(private val service: HandoffClaimService) : HandoffClaimApi {
    override suspend fun claim(token: String): HandoffClaimResult {
        var status: HandoffClaimFailure? = null
        return when (val r = apiCall({ e -> status = failureFor(e.code()); ApiFailure.Failed }) { service.claim(token) }) {
            is ApiResult.Ok -> HandoffClaimResult.Claimed(ClaimedHandoff(r.value.homeName, r.value.companyName, r.value.size))
            is ApiResult.Err -> HandoffClaimResult.Failed(status ?: offlineOrFailed(r.failure))
        }
    }

    override suspend fun download(token: String, dest: File): HandoffDownloadResult {
        var status: HandoffClaimFailure? = null
        val part = File(dest.path + ".part")
        val r = apiCall({ e -> status = failureFor(e.code()); ApiFailure.Failed }) {
            service.bundle(token).use { body ->
                dest.parentFile?.mkdirs()
                body.byteStream().use { input -> part.outputStream().use { input.copyTo(it) } }
                if (!part.renameTo(dest)) throw IOException("could not save the handoff")
                dest
            }
        }
        return when (r) {
            is ApiResult.Ok -> HandoffDownloadResult.Saved(r.value)
            is ApiResult.Err -> {
                part.delete()
                HandoffDownloadResult.Failed(status ?: offlineOrFailed(r.failure))
            }
        }
    }

    private fun failureFor(code: Int): HandoffClaimFailure = when (code) {
        401 -> HandoffClaimFailure.SignedOut
        404 -> HandoffClaimFailure.NotFound
        409 -> HandoffClaimFailure.ClaimedByOther
        410 -> HandoffClaimFailure.Expired
        else -> HandoffClaimFailure.Failed
    }

    private fun offlineOrFailed(f: ApiFailure): HandoffClaimFailure =
        if (f == ApiFailure.Offline) HandoffClaimFailure.Offline else HandoffClaimFailure.Failed

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** [client] must add the session token (the server answers 401 otherwise). */
        fun create(baseUrl: String, client: Call.Factory): RetrofitHandoffClaimApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .callFactory(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitHandoffClaimApi(retrofit.create(HandoffClaimService::class.java))
        }
    }
}
