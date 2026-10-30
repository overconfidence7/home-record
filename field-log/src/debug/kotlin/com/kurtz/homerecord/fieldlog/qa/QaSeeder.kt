package com.kurtz.homerecord.fieldlog.qa

import android.content.Context
import android.util.Log
import com.kurtz.homerecord.core.auth.AuthResult
import com.kurtz.homerecord.fieldlog.AppContainer
import com.kurtz.homerecord.fieldlog.BuildConfig
import com.kurtz.homerecord.fieldlog.HomeRecordApplication
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Debug-only: the seed scenarios, shared by [SeedReceiver] (adb broadcast) and [SeedActivity] (Maestro `openLink`).
 * Needs the dev backend (`ENVIRONMENT=dev`). `regression` seeds "QA Electric" with an Owner and a Crew member there and
 * signs this device in as the owner; `long-list` does the same and adds 200 jobs on this phone; `reset` signs out and
 * wipes the backend. All three first clear this phone's Room tables, media and prefs (cr:fl-seed-reset).
 */
object QaSeeder {
    /** Runs [scenario]; false for an unknown one or a refused sign-in. Throws when the backend cannot be reached. */
    suspend fun run(context: Context, scenario: String): Boolean {
        val container = (context.applicationContext as HomeRecordApplication).container
        return when (scenario) {
            "regression" -> signInToSeededCompany(container)
            // AC-U11 scroll jank: the regression company with 200 jobs on Jobs
            "long-list" -> signInToSeededCompany(container).also { if (it) LongListSeed.seed(container.db, QA_COMPANY_ID) }
            "reset" -> {
                container.signOutAndWipe()
                post(httpClient(), "${BuildConfig.API_BASE_URL.trimEnd('/')}/dev/reset", "{}")
                true
            }
            else -> false
        }
    }

    private suspend fun signInToSeededCompany(container: AppContainer): Boolean {
        val client = httpClient()
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        container.signOutAndWipe()
        post(client, "$base/dev/seed", """{"scenario":"regression"}""")
        val email = OWNER_EMAIL
        container.auth.requestEmailCode(email)
        val code = get(client, "$base/dev/last-code?email=$email").getString("code")
        val result = container.auth.verifyEmailCode(email, code)
        if (result != AuthResult.Success) Log.e(TAG, "verify returned $result")
        return result == AuthResult.Success
    }

    private fun httpClient() = OkHttpClient.Builder()
        .callTimeout(4, TimeUnit.SECONDS)
        .build()

    private fun post(client: OkHttpClient, url: String, json: String) {
        val req = Request.Builder().url(url).post(json.toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { check(it.isSuccessful) { "POST $url ${it.code}" } }
    }

    private fun get(client: OkHttpClient, url: String): JSONObject =
        client.newCall(Request.Builder().url(url).build()).execute().use {
            check(it.isSuccessful) { "GET $url ${it.code}" }
            JSONObject(it.body?.string().orEmpty())
        }

    private const val OWNER_EMAIL = "owner@qa.test"
    private const val QA_COMPANY_ID = "qa-company" // backend/src/routes/dev.ts
    private const val TAG = "QaSeed"
}
