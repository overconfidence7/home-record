package com.kurtz.homerecord.core.auth

import java.util.Base64

/** Reads the `sub` claim (the user id) from a session token without verifying it; the server does that. */
object JwtSubject {
    private val sub = Regex("\"sub\"\\s*:\\s*\"([^\"]+)\"")

    fun of(token: String): String? {
        val payload = token.split('.').getOrNull(1) ?: return null
        val json = runCatching { String(Base64.getUrlDecoder().decode(payload)) }.getOrNull() ?: return null
        return sub.find(json)?.groupValues?.get(1)
    }
}
