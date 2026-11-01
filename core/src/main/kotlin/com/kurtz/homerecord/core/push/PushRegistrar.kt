package com.kurtz.homerecord.core.push

import com.kurtz.homerecord.core.company.ApiResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** This phone's push token as the backend knows it: registered for [userId] (AC-B30). */
data class PushRegistration(val token: String, val userId: String)

/** Network side: `PUT` and `DELETE /me/push-token` (backend/src/routes/me.ts). */
interface PushTokenApi {
    suspend fun register(token: String, app: String): ApiResult<Unit>
    suspend fun unregister(token: String): ApiResult<Unit>
}

/** The phone's FCM token, or null when push is not set up on this build (no Firebase config) or not reachable. */
fun interface PushTokenSource {
    suspend fun token(): String?
}

/** Remembers what was last registered, so a restart does not send the same token again. */
interface PushRegistrationStore {
    fun read(): PushRegistration?
    fun write(registration: PushRegistration?)
}

/**
 * Keeps the backend's token for this phone in step with who is signed in (AC-B30, AC-F36, AC-F42): registers on sign
 * in and whenever FCM hands out a new token, forgets it on sign out. A failed register (offline) is not remembered, so
 * the next [onSession] (back online, next start) tries again.
 */
class PushRegistrar(
    private val api: PushTokenApi,
    private val source: PushTokenSource,
    private val store: PushRegistrationStore,
    private val app: String,
) {
    private val lock = Mutex()
    private var userId: String? = null

    /** The signed-in user changed, or the phone came back online; null means signed out. */
    suspend fun onSession(userId: String?) = lock.withLock {
        this.userId = userId
        if (userId == null) {
            // The session is gone, so the server cannot be told; a later sign in on this phone moves the token.
            store.write(null)
            return@withLock
        }
        val token = source.token() ?: return@withLock
        registerLocked(token, userId)
    }

    /** FCM rotated the token. */
    suspend fun onNewToken(token: String) = lock.withLock {
        val user = userId ?: return@withLock
        registerLocked(token, user)
    }

    /** Call before an explicit sign out, while the session still answers, so this phone stops getting the account's pushes. */
    suspend fun unregister() = lock.withLock {
        val saved = store.read()
        if (saved != null) api.unregister(saved.token)
        store.write(null)
        userId = null
    }

    private suspend fun registerLocked(token: String, user: String) {
        val next = PushRegistration(token, user)
        if (store.read() == next) return
        if (api.register(token, app) is ApiResult.Ok) store.write(next)
    }
}
