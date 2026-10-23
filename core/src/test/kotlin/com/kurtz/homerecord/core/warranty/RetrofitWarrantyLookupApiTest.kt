package com.kurtz.homerecord.core.warranty

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.testing.AcceptanceCriterion
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test

class RetrofitWarrantyLookupApiTest {
    private var sent = ""
    private fun api(code: Int = 200, body: String = "{}", fail: Boolean = false) = RetrofitWarrantyLookupApi.create(
        "http://x.test",
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            if (fail) throw IOException("offline")
            val req = chain.request()
            sent = req.url.encodedPath + " " + req.body?.let { Buffer().also { b -> it.writeTo(b) }.readUtf8() }.orEmpty()
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build(),
    )

    @AcceptanceCriterion("AC-C34")
    @Test fun convertsYearsToMonthsAndUppercasesConfidence() = runTest {
        val r = api(body = """{"partsYears":6,"laborYears":1.5,"specialTerms":["tank 12 years"],"registrationRequired":true,"sourceUrl":"https://rheem.com/w","confidence":"high","summary":"Six years.","cached":true}""")
            .lookup(" Rheem ", "XG50", "water_heater", "") as ApiResult.Ok
        assertThat(sent).contains("/lookup/warranty")
        assertThat(sent).contains("\"make\":\"Rheem\"")
        assertThat(r.value.partsMonths).isEqualTo(72)
        assertThat(r.value.laborMonths).isEqualTo(18)
        assertThat(r.value.confidence).isEqualTo("HIGH")
        assertThat(r.value.registrationRequired).isTrue()
    }

    @AcceptanceCriterion("AC-C34")
    @Test fun networkErrorIsOfflineAndServerErrorIsFailed() = runTest {
        assertThat(api(fail = true).lookup("a", "b", null, null)).isEqualTo(ApiResult.Err(ApiFailure.Offline))
        assertThat(api(code = 503, body = """{"error":"unavailable"}""").lookup("a", "b", null, null)).isEqualTo(ApiResult.Err(ApiFailure.Failed))
    }
}
