package com.kurtz.homerecord.core.auth

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

private fun client(handler: (okhttp3.Request) -> Response) =
    OkHttpClient.Builder().addInterceptor(Interceptor { handler(it.request()) }).build()

private fun reply(req: okhttp3.Request, code: Int, body: String, vararg headers: Pair<String, String>) =
    Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
        .apply { headers.forEach { (k, v) -> header(k, v) } }
        .body(body.toResponseBody("application/json".toMediaType())).build()

class RetrofitAuthApiTest {
    @Test fun verifyReturnsToken() = runTest {
        var path = ""
        val api = RetrofitAuthApi.create("http://x.test", client { path = it.url.encodedPath; reply(it, 200, """{"token":"jwt-1"}""") })
        assertThat(api.verifyEmailCode("a@b.com", "123456")).isEqualTo(TokenOrError("jwt-1", AuthResult.Success))
        assertThat(path).isEqualTo("/auth/email/verify")
    }

    @Test fun startSucceeds() = runTest {
        val api = RetrofitAuthApi.create("http://x.test", client { reply(it, 200, """{"ok":true}""") })
        assertThat(api.startEmailCode("a@b.com")).isEqualTo(AuthResult.Success)
    }

    @Test fun errorCodesMap() = runTest {
        suspend fun result(body: String) = RetrofitAuthApi.create("http://x.test", client { reply(it, 401, body) }).verifyEmailCode("a@b.com", "123456").error
        assertThat(result("""{"error":"invalid_code"}""")).isEqualTo(AuthResult.InvalidCode)
        assertThat(result("""{"error":"expired"}""")).isEqualTo(AuthResult.CodeExpired)
        assertThat(result("""{"error":"locked"}""")).isEqualTo(AuthResult.TooManyAttempts)
        assertThat(result("""{"error":"rate_limited"}""")).isEqualTo(AuthResult.RateLimited) // AC-B1
        assertThat(result("not json")).isEqualTo(AuthResult.Failed)
    }

    @Test fun networkFailureIsOffline() = runTest {
        val api = RetrofitAuthApi.create("http://x.test", client { throw IOException("down") })
        assertThat(api.signInWithGoogle("id-token-123").error).isEqualTo(AuthResult.Offline)
        assertThat(api.startEmailCode("a@b.com")).isEqualTo(AuthResult.Offline)
    }

    @Test fun sessionInterceptorStoresRefreshAndSignsOutOn401() = runTest {
        val store = object : TokenStore {
            var t: String? = "old"
            override fun read() = t
            override fun write(token: String) { t = token }
            override fun clear() { t = null }
        }
        val repo = AuthRepository(RetrofitAuthApi.create("http://x.test", client { reply(it, 200, "{}") }), store, object : GoogleCredentialSource { override suspend fun idToken() = null })
        var auth: String? = null
        var next = reply(okhttp3.Request.Builder().url("http://x.test/").build(), 200, "{}", "X-Refreshed-Token" to "new")
        val http = OkHttpClient.Builder().addInterceptor(SessionInterceptor(repo, store))
            .addInterceptor(Interceptor { auth = it.request().header("Authorization"); next.newBuilder().request(it.request()).build() }).build()
        http.newCall(okhttp3.Request.Builder().url("http://x.test/me").build()).execute().close()
        assertThat(auth).isEqualTo("Bearer old")
        assertThat(store.t).isEqualTo("new")
        next = next.newBuilder().code(401).removeHeader("X-Refreshed-Token").build()
        http.newCall(okhttp3.Request.Builder().url("http://x.test/me").build()).execute().close()
        assertThat(store.t).isNull()
        assertThat(repo.state.value).isEqualTo(AuthState.SignedOut)
    }

    // REVIEW: a failed sign-in call leaves a logcat trace (call and status), never the email or code
    @Test fun failedCallsAreLoggedWithoutTheEmailOrCode() = runTest {
        val logged = mutableListOf<String>()
        val previous = com.kurtz.homerecord.core.net.NetLog.sink
        com.kurtz.homerecord.core.net.NetLog.sink = { message, _ -> logged += message }
        try {
            RetrofitAuthApi.create("http://x.test", client { throw IOException("no route to host") }).verifyEmailCode("a@b.com", "123456")
            RetrofitAuthApi.create("http://x.test", client { reply(it, 429, """{"error":"rate_limited"}""") }).startEmailCode("a@b.com")
        } finally {
            com.kurtz.homerecord.core.net.NetLog.sink = previous
        }
        assertThat(logged).hasSize(2)
        assertThat(logged[0]).contains("auth/email/verify failed")
        assertThat(logged[0]).contains("IOException: no route to host")
        assertThat(logged[1]).contains("auth/email/start failed: HTTP 429")
        assertThat(logged.joinToString()).doesNotContain("a@b.com")
        assertThat(logged.joinToString()).doesNotContain("123456")
    }
}
