package com.kurtz.homerecord.fieldlog.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.kurtz.homerecord.core.push.PushTokenSource
import com.kurtz.homerecord.fieldlog.BuildConfig
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** The Firebase project values baked in at build time (field-log/build.gradle.kts); blank until Nate creates the project. */
data class FirebaseConfig(val appId: String, val apiKey: String, val projectId: String, val senderId: String) {
    val complete: Boolean get() = listOf(appId, apiKey, projectId, senderId).all { it.isNotBlank() }

    companion object {
        fun fromBuild() = FirebaseConfig(
            BuildConfig.FIREBASE_APP_ID, BuildConfig.FIREBASE_API_KEY, BuildConfig.FIREBASE_PROJECT_ID, BuildConfig.FIREBASE_SENDER_ID,
        )
    }
}

/**
 * Firebase is used for Cloud Messaging only (AC-B30), set up by hand from [FirebaseConfig] so no google-services
 * plugin or json is needed. Returns false, and push stays off, when the config is blank or Firebase refuses it.
 */
object FirebasePush {
    fun init(context: Context, config: FirebaseConfig): Boolean {
        if (!config.complete) return false
        return runCatching {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId(config.appId)
                    .setApiKey(config.apiKey)
                    .setProjectId(config.projectId)
                    .setGcmSenderId(config.senderId)
                    .build()
                FirebaseApp.initializeApp(context, options)
            }
            true
        }.getOrDefault(false)
    }
}

/** Asks FCM for this phone's token; null when push is off or FCM cannot be reached (no Play services, offline). */
class FirebaseTokenSource(private val enabled: Boolean) : PushTokenSource {
    override suspend fun token(): String? {
        if (!enabled) return null
        return suspendCancellableCoroutine { cont ->
            runCatching {
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    cont.resume(if (task.isSuccessful) task.result?.takeIf { it.isNotBlank() } else null)
                }
            }.onFailure { cont.resume(null) }
        }
    }
}
