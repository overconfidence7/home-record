package com.kurtz.homerecord.core.push

import android.content.SharedPreferences

/** The last registration in plain prefs; the token is not a secret (it only lets our own server address this phone). */
class PrefsPushRegistrationStore(private val prefs: SharedPreferences) : PushRegistrationStore {
    override fun read(): PushRegistration? {
        val token = prefs.getString(TOKEN, null) ?: return null
        val user = prefs.getString(USER, null) ?: return null
        return PushRegistration(token, user)
    }

    override fun write(registration: PushRegistration?) {
        prefs.edit().apply {
            if (registration == null) clear() else putString(TOKEN, registration.token).putString(USER, registration.userId)
        }.apply()
    }

    private companion object {
        const val TOKEN = "token"
        const val USER = "user"
    }
}
