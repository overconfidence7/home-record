package com.kurtz.homerecord.core.auth

import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.net.apiCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.DELETE

/**
 * `DELETE /me` (backend/src/routes/me.ts, T081d): deletes the signed-in account, every home and company it owns with
 * their records and files, and its memberships elsewhere. Play requires it of apps with sign-in.
 */
fun interface AccountDeletionApi {
    suspend fun deleteAccount(): ApiResult<Unit>
}

@Serializable data class DeletedAccount(val deleted: Boolean = true, val homesRemoved: Int = 0, val companiesRemoved: Int = 0)

interface AccountDeletionService {
    @DELETE("me") suspend fun delete(): DeletedAccount
}

class RetrofitAccountDeletionApi(private val service: AccountDeletionService) : AccountDeletionApi {
    override suspend fun deleteAccount(): ApiResult<Unit> = apiCall { service.delete(); Unit }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun create(baseUrl: String, client: Call.Factory): RetrofitAccountDeletionApi {
            val retrofit = Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .callFactory(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            return RetrofitAccountDeletionApi(retrofit.create(AccountDeletionService::class.java))
        }
    }
}

/**
 * Delete account on a phone: the server deletes first; only when it answers does [afterDeleted] run (each app's own
 * clean-up: House Manual signs out and keeps the home, Field Log signs out and clears the company from the phone).
 * A failed delete changes nothing on the phone and signs no one out.
 */
class AccountDeletion(private val api: AccountDeletionApi, private val afterDeleted: suspend () -> Unit) {
    suspend fun delete(): ApiResult<Unit> {
        val result = api.deleteAccount()
        if (result is ApiResult.Ok) afterDeleted()
        return result
    }
}
