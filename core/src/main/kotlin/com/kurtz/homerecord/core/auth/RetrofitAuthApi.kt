package com.kurtz.homerecord.core.auth

import java.io.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.kurtz.homerecord.core.net.LazyCallFactory
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable data class EmailBody(val email: String)

@Serializable data class VerifyBody(val email: String, val code: String)

@Serializable data class GoogleBody(val idToken: String)

@Serializable data class TokenBody(val token: String)

@Serializable data class ErrorBody(val error: String? = null)

interface AuthService {
    @POST("auth/email/start") suspend fun start(@Body body: EmailBody)

    @POST("auth/email/verify") suspend fun verify(@Body body: VerifyBody): TokenBody

    @POST("auth/google") suspend fun google(@Body body: GoogleBody): TokenBody
}

/** Maps the backend's auth endpoints (AC-B1) onto [AuthResult]. */
class RetrofitAuthApi(private val service: AuthService) : AuthApi {
    override suspend fun startEmailCode(email: String): AuthResult =
        call { service.start(EmailBody(email)); null }.error

    override suspend fun verifyEmailCode(email: String, code: String): TokenOrError =
        call { service.verify(VerifyBody(email, code)).token }

    override suspend fun signInWithGoogle(idToken: String): TokenOrError =
        call { service.google(GoogleBody(idToken)).token }

    private suspend fun call(block: suspend () -> String?): TokenOrError =
        try {
            TokenOrError(block(), AuthResult.Success)
        } catch (e: HttpException) {
            TokenOrError(null, mapError(e))
        } catch (e: IOException) {
            TokenOrError(null, AuthResult.Offline)
        }

    private fun mapError(e: HttpException): AuthResult {
        val code = e.response()?.errorBody()?.string()?.let { runCatching { json.decodeFromString<ErrorBody>(it).error }.getOrNull() }
        return when (code) {
            "invalid_code" -> AuthResult.InvalidCode
            "expired" -> AuthResult.CodeExpired
            "locked", "too_many_attempts" -> AuthResult.TooManyAttempts
            "rate_limited" -> AuthResult.RateLimited
            else -> AuthResult.Failed
        }
    }

    companion object {
        internal val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String, client: Call.Factory = LazyCallFactory.plain): RetrofitAuthApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .callFactory(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitAuthApi(retrofit.create(AuthService::class.java))
        }
    }
}

/** Reads `X-Refreshed-Token` and 401s on authenticated calls and reports them to the repository. */
class SessionInterceptor(private val repo: AuthRepository, private val tokens: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokens.read()
        val request = if (token != null) chain.request().newBuilder().header("Authorization", "Bearer $token").build() else chain.request()
        val response = chain.proceed(request)
        if (token != null) {
            response.header("X-Refreshed-Token")?.let(repo::onTokenRefreshed)
            if (response.code == 401) repo.onUnauthorized()
        }
        return response
    }
}
