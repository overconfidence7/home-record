package com.kurtz.homerecord.core.bundle

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** AC-F51 / AC-B40: "Send to homeowner" uploads the bundle as the request body with who it is for in the query. */
@AcceptanceCriterion("AC-F51")
class RetrofitHandoffApiTest {
    @get:Rule val tmp = TemporaryFolder()
    private var request: Request? = null
    private var bodyBytes = ByteArray(0)

    private fun api(code: Int = 201, body: String = "{}", fail: Boolean = false) = RetrofitHandoffApi.create(
        "http://x.test",
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            if (fail) throw IOException("offline")
            val req = chain.request()
            request = req
            bodyBytes = req.body?.let { Buffer().also { b -> it.writeTo(b) }.readByteArray() } ?: ByteArray(0)
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build(),
    )

    private val file by lazy { tmp.newFile("Hale kitchen 2026-11-05.homerecord").apply { writeBytes(byteArrayOf(0x50, 0x4b, 3, 4, 9)) } }
    private fun bundle(): File = file

    @Test fun postsTheFileWithCompanyJobEmailAndHomeName() = runTest { // AC-F51
        val r = api(body = """{"id":"h1","email":"dana@example.com","expiresAt":1765000000000,"size":5}""")
            .send("co1", "j1", " Dana@Example.com ", " 12 Elm St ", bundle())
        assertThat(r).isEqualTo(HandoffSendResult.Sent(SentHandoff("dana@example.com", 1765000000000)))
        val req = request ?: error("no request")
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.url.encodedPath).isEqualTo("/handoff")
        assertThat(req.url.queryParameter("companyId")).isEqualTo("co1")
        assertThat(req.url.queryParameter("jobId")).isEqualTo("j1")
        assertThat(req.url.queryParameter("email")).isEqualTo("dana@example.com")
        assertThat(req.url.queryParameter("homeName")).isEqualTo("12 Elm St")
        assertThat(req.body?.contentType().toString()).isEqualTo("application/zip")
        assertThat(req.body?.contentLength()).isEqualTo(5L)
        assertThat(bodyBytes).isEqualTo(byteArrayOf(0x50, 0x4b, 3, 4, 9))
    }

    @Test fun noJobLeavesTheParameterOut() = runTest { // AC-F51
        api(body = """{"id":"h1","email":"a@b.co","expiresAt":1}""").send("co1", null, "a@b.co", "Home", bundle())
        assertThat(request?.url?.queryParameterNames).doesNotContain("jobId")
    }

    @Test fun offlineTooLargeAndServerErrorsAreTold() = runTest { // AC-F51
        assertThat(api(fail = true).send("co1", null, "a@b.co", "Home", bundle()))
            .isEqualTo(HandoffSendResult.Failed(HandoffSendFailure.Offline))
        assertThat(api(code = 413, body = """{"error":"too_large"}""").send("co1", null, "a@b.co", "Home", bundle()))
            .isEqualTo(HandoffSendResult.Failed(HandoffSendFailure.TooLarge))
        assertThat(api(code = 502, body = """{"error":"send_failed"}""").send("co1", null, "a@b.co", "Home", bundle()))
            .isEqualTo(HandoffSendResult.Failed(HandoffSendFailure.Failed))
        assertThat(api(code = 403, body = """{"error":"forbidden"}""").send("co1", null, "a@b.co", "Home", bundle()))
            .isEqualTo(HandoffSendResult.Failed(HandoffSendFailure.Failed))
    }

    @Test fun emailRuleAcceptsAddressesAndRefusesTheRest() { // AC-F51
        assertThat(HandoffEmail.isValid(" Dana.Hale@Example.com ")).isTrue()
        assertThat(HandoffEmail.isValid("dana@example")).isFalse()
        assertThat(HandoffEmail.isValid("dana hale@example.com")).isFalse()
        assertThat(HandoffEmail.isValid("")).isFalse()
        assertThat(HandoffEmail.normalize(" Dana@Example.COM ")).isEqualTo("dana@example.com")
    }
}
