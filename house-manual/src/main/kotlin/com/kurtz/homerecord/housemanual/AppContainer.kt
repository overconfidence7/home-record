package com.kurtz.homerecord.housemanual

import android.app.Application
import android.content.Context
import com.kurtz.homerecord.core.auth.AuthRepository
import com.kurtz.homerecord.core.auth.CredentialManagerGoogleSource
import com.kurtz.homerecord.core.auth.EncryptedTokenStore
import com.kurtz.homerecord.core.auth.RetrofitAuthApi
import com.kurtz.homerecord.core.auth.SessionInterceptor
import androidx.room.Room
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.housemodel.HomeRepository
import okhttp3.OkHttpClient

/** Manual DI: one container per process, created in [HomeRecordApplication.onCreate]. */
class AppContainer(app: Application) {
    /** Set by MainActivity while it is resumed so Credential Manager can show its sheet. */
    @Volatile var activityContext: Context? = null

    private val db = Room.databaseBuilder(app, HomeRecordDatabase::class.java, "homerecord.db").build()
    val homes = HomeRepository(db)

    private val tokens = EncryptedTokenStore(app)
    private val authApi = RetrofitAuthApi.create(BuildConfig.API_BASE_URL)
    val auth = AuthRepository(authApi, tokens, CredentialManagerGoogleSource({ activityContext }, BuildConfig.GOOGLE_WEB_CLIENT_ID))

    /** Client for authenticated calls (sync, T010): adds the bearer token and handles refresh and 401. */
    val http: OkHttpClient = OkHttpClient.Builder().addInterceptor(SessionInterceptor(auth, tokens)).build()
}

class HomeRecordApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
