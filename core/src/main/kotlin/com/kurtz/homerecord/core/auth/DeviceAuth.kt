package com.kurtz.homerecord.core.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/** Session token in EncryptedSharedPreferences (AC-B1). */
class EncryptedTokenStore(context: Context) : TokenStore {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun read(): String? = prefs.getString(KEY, null)
    override fun write(token: String) = prefs.edit().putString(KEY, token).apply()
    override fun clear() = prefs.edit().remove(KEY).apply()

    private companion object { const val KEY = "token" }
}

/** Credential Manager "Sign in with Google"; needs an Activity context to show the sheet. */
class CredentialManagerGoogleSource(
    private val activityContext: () -> Context?,
    private val webClientId: String,
) : GoogleCredentialSource {
    override suspend fun idToken(): String? {
        val context = activityContext() ?: return null
        if (webClientId.isBlank()) return null
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetGoogleIdOption.Builder().setServerClientId(webClientId).setFilterByAuthorizedAccounts(false).build())
            .build()
        return try {
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleIdTokenCredential.createFrom(credential.data).idToken
            } else {
                null
            }
        } catch (e: GetCredentialException) {
            null
        }
    }
}
