package com.kurtz.homerecord.fieldlog.qa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kurtz.homerecord.core.auth.AuthResult
import com.kurtz.homerecord.fieldlog.BuildConfig
import com.kurtz.homerecord.fieldlog.HomeRecordApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Debug-only: `adb shell am broadcast -a com.kurtz.homerecord.fieldlog.qa.SEED -p <pkg> --es scenario regression|reset`.
 * Needs the dev backend (`ENVIRONMENT=dev`). `regression` seeds "QA Electric" with an Owner and a Crew member there and
 * signs this device in as the owner; `reset` signs out and wipes the backend. Result code 1 means done.
 */
class SeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as HomeRecordApplication).container
        val scenario = intent.getStringExtra("scenario") ?: "regression"
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            val ok = try {
                when (scenario) {
                    "regression" -> {
                        val client = OkHttpClient()
                        val base = BuildConfig.API_BASE_URL.trimEnd('/')
                        container.auth.signOut()
                        post(client, "$base/dev/seed", """{"scenario":"regression"}""")
                        val email = OWNER_EMAIL
                        container.auth.requestEmailCode(email)
                        val code = get(client, "$base/dev/last-code?email=$email").getString("code")
                        container.auth.verifyEmailCode(email, code) == AuthResult.Success
                    }
                    "reset" -> {
                        container.auth.signOut()
                        post(OkHttpClient(), "${BuildConfig.API_BASE_URL.trimEnd('/')}/dev/reset", "{}")
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                false
            }
            pending.setResult(if (ok) 1 else 0, scenario, null)
            pending.finish()
        }
    }

    private fun post(client: OkHttpClient, url: String, json: String) {
        val req = Request.Builder().url(url).post(json.toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { check(it.isSuccessful) { "POST $url ${it.code}" } }
    }

    private fun get(client: OkHttpClient, url: String): JSONObject =
        client.newCall(Request.Builder().url(url).build()).execute().use {
            check(it.isSuccessful) { "GET $url ${it.code}" }
            JSONObject(it.body?.string().orEmpty())
        }

    private companion object {
        const val OWNER_EMAIL = "owner@qa.test"
    }
}
