package com.kurtz.homerecord.core.bundle

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** AC-H17 / AC-B40: House Manual claims the handoff link for its account and downloads the bundle. */
@AcceptanceCriterion("AC-H17")
class RetrofitHandoffClaimApiTest {
    @get:Rule val tmp = TemporaryFolder()
    private val requests = mutableListOf<Request>()
    private val token = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcd"

    private fun api(
        code: Int = 200,
        body: ByteArray = "{}".toByteArray(),
        type: String = "application/json",
        fail: Boolean = false,
        io: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
    ) =
        RetrofitHandoffClaimApi.create(
            "http://x.test",
            OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                if (fail) throw IOException("offline")
                val req = chain.request()
                requests += req
                Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                    .body(body.toResponseBody(type.toMediaType())).build()
            }).build(),
            io,
        )

    @Test fun claimPostsTheTokenAndReadsWhoSentIt() = runTest { // AC-H17
        val r = api(body = """{"homeName":"Ortiz house","companyName":"QA Electric","size":5,"claimedAt":1,"expiresAt":2,"bundlePath":"/x"}""".toByteArray())
            .claim(token)
        assertThat(r).isEqualTo(HandoffClaimResult.Claimed(ClaimedHandoff("Ortiz house", "QA Electric", 5)))
        assertThat(requests.single().method).isEqualTo("POST")
        assertThat(requests.single().url.encodedPath).isEqualTo("/handoff/$token/claim")
    }

    @Test fun claimFailuresAreTold() = runTest { // AC-H17, AC-B40
        assertThat(api(code = 404).claim(token)).isEqualTo(HandoffClaimResult.Failed(HandoffClaimFailure.NotFound))
        assertThat(api(code = 410).claim(token)).isEqualTo(HandoffClaimResult.Failed(HandoffClaimFailure.Expired))
        assertThat(api(code = 409).claim(token)).isEqualTo(HandoffClaimResult.Failed(HandoffClaimFailure.ClaimedByOther))
        assertThat(api(code = 401).claim(token)).isEqualTo(HandoffClaimResult.Failed(HandoffClaimFailure.SignedOut))
        assertThat(api(code = 500).claim(token)).isEqualTo(HandoffClaimResult.Failed(HandoffClaimFailure.Failed))
        assertThat(api(fail = true).claim(token)).isEqualTo(HandoffClaimResult.Failed(HandoffClaimFailure.Offline))
    }

    @Test fun downloadSavesTheBundleToTheFile() = runTest { // AC-H17
        val dest = tmp.root.resolve("handoff/claimed.homerecord")
        val bytes = byteArrayOf(0x50, 0x4b, 3, 4, 7, 8)
        val r = api(body = bytes, type = "application/zip").download(token, dest)
        assertThat(r).isEqualTo(HandoffDownloadResult.Saved(dest))
        assertThat(dest.readBytes()).isEqualTo(bytes)
        assertThat(requests.single().method).isEqualTo("GET")
        assertThat(requests.single().url.encodedPath).isEqualTo("/handoff/$token/bundle")
    }

    @Test fun downloadReadsTheBundleOnTheIoDispatcher() = runTest { // AC-H17 (QA auto: NetworkOnMainThreadException on claim)
        val dest = tmp.root.resolve("handoff/claimed.homerecord")
        var ranOnIo = 0
        val io = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                ranOnIo++
                kotlinx.coroutines.Dispatchers.IO.dispatch(context, block)
            }
        }
        val r = api(body = byteArrayOf(1, 2, 3), type = "application/zip", io = io).download(token, dest)
        assertThat(r).isEqualTo(HandoffDownloadResult.Saved(dest))
        assertThat(ranOnIo).isGreaterThan(0)
    }

    @Test fun failedDownloadLeavesNoPartialFile() = runTest { // AC-H17
        val dest = tmp.root.resolve("handoff/claimed.homerecord")
        assertThat(api(code = 410).download(token, dest)).isEqualTo(HandoffDownloadResult.Failed(HandoffClaimFailure.Expired))
        assertThat(api(fail = true).download(token, dest)).isEqualTo(HandoffDownloadResult.Failed(HandoffClaimFailure.Offline))
        assertThat(dest.exists()).isFalse()
        assertThat(java.io.File(dest.path + ".part").exists()).isFalse()
    }

    @Test fun linkTokenComesFromTheAppLinkOrTheEmailLink() { // AC-H17
        assertThat(HandoffLink.token("homerecord://handoff/$token")).isEqualTo(token)
        assertThat(HandoffLink.token("https://api.homerecord.app/handoff/$token")).isEqualTo(token)
        assertThat(HandoffLink.token("https://api.homerecord.app/handoff/$token/")).isEqualTo(token)
        assertThat(HandoffLink.token("homerecord://handoff/$token?x=1")).isEqualTo(token)
        assertThat(HandoffLink.token("homerecord://handoff/short")).isNull()
        assertThat(HandoffLink.token("https://api.homerecord.app/other/$token")).isNull()
        assertThat(HandoffLink.token("content://downloads/1")).isNull()
        assertThat(HandoffLink.token(null)).isNull()
    }
}
