package com.kurtz.homerecord.core.net

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * An OkHttp client built on its first call instead of when the app container is created. Building a client loads the
 * platform trust store and TLS provider, which cost tens of milliseconds on the main thread during cold start (AC-U11);
 * the first request runs on a background dispatcher, so the cost moves there.
 */
class LazyCallFactory(create: () -> OkHttpClient) : Call.Factory {
    private val client by lazy(create)

    override fun newCall(request: Request): Call = client.newCall(request)

    companion object {
        /** One shared client without session headers, for calls to presigned URLs and sign-in. */
        val plain: Call.Factory = LazyCallFactory(::OkHttpClient)
    }
}
