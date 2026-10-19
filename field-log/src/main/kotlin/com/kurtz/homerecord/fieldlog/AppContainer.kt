package com.kurtz.homerecord.fieldlog

import android.app.Application
import android.content.Context
import com.kurtz.homerecord.core.auth.AuthRepository
import com.kurtz.homerecord.core.auth.CredentialManagerGoogleSource
import com.kurtz.homerecord.core.auth.EncryptedTokenStore
import com.kurtz.homerecord.core.auth.RetrofitAuthApi
import com.kurtz.homerecord.core.auth.SessionInterceptor
import com.kurtz.homerecord.core.company.CompanyRepository
import com.kurtz.homerecord.core.company.PrefsCompanyCache
import com.kurtz.homerecord.core.company.RetrofitCompanyApi
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient

/** Manual DI: one container per process, created in [HomeRecordApplication.onCreate]. */
class AppContainer(app: Application) {
    /** Set by MainActivity while it is resumed so Credential Manager can show its sheet. */
    @Volatile var activityContext: Context? = null

    private val tokens = EncryptedTokenStore(app)
    private val authApi = RetrofitAuthApi.create(BuildConfig.API_BASE_URL)
    val auth = AuthRepository(authApi, tokens, CredentialManagerGoogleSource({ activityContext }, BuildConfig.GOOGLE_WEB_CLIENT_ID))

    /** Client for authenticated calls (sync, T010): adds the bearer token and handles refresh and 401. */
    val http: OkHttpClient = OkHttpClient.Builder().addInterceptor(SessionInterceptor(auth, tokens)).build()

    val companyApi = RetrofitCompanyApi.create(BuildConfig.API_BASE_URL, http)
    val company = CompanyRepository(companyApi, PrefsCompanyCache(app)).also { repo ->
        // Sign out drops cached company data (AC-F4).
        auth.addSignOutListener(repo::clear)
    }

    /** Invite code from a tapped join link, held until the person has signed in and reaches FL-Welcome. */
    val pendingJoinCode = MutableStateFlow<String?>(null)

    fun inviteLink(code: String): String = "${BuildConfig.API_BASE_URL.trimEnd('/')}/join/$code"
}

class HomeRecordApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
