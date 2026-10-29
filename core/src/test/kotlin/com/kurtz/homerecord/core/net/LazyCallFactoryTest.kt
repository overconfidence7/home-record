package com.kurtz.homerecord.core.net

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.sync.RetrofitSyncApi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

// AC-U11: the app containers build their API clients in Application.onCreate; the OkHttp client (trust store, TLS
// provider) must wait for the first request so it stays off the cold-start path.
class LazyCallFactoryTest {
    private var built = 0

    private fun factory() = LazyCallFactory {
        built++
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("{}".toResponseBody()).build()
        }).build()
    }

    @Test
    fun `wiring an API does not build the client`() {
        val http = factory()
        RetrofitSyncApi.create("http://10.0.2.2:8787", http)
        assertThat(built).isEqualTo(0)
    }

    @Test
    fun `first call builds the client once and later calls reuse it`() {
        val http = factory()
        repeat(3) {
            http.newCall(Request.Builder().url("http://10.0.2.2:8787/me").build()).execute().use { r ->
                assertThat(r.code).isEqualTo(200)
            }
        }
        assertThat(built).isEqualTo(1)
    }
}
