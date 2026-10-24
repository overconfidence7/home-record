package com.kurtz.homerecord.core.lookup

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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RetrofitLookupApiTest {
    private var url = ""
    private fun api(code: Int = 200, body: String = "{}", fail: Boolean = false, jpeg: ByteArray? = byteArrayOf(1, 2, 3)) = RetrofitLookupApi.create(
        "http://x.test",
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            if (fail) throw IOException("offline")
            val req = chain.request()
            url = req.url.toString()
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build(),
    ) { jpeg }

    @AcceptanceCriterion("AC-B11")
    @Test fun readsPlateFields() = runTest {
        val r = api(body = """{"make":"Rheem","model":"XG50T06","serial":null,"manufacturedOn":"2015-04-01"}""").read("/p.jpg") as ApiResult.Ok
        assertThat(url).contains("/lookup/dataplate")
        assertThat(r.value).isEqualTo(PlateReading("Rheem", "XG50T06", null, "2015-04-01"))
    }

    @AcceptanceCriterion("AC-B11")
    @Test fun unreadablePhotoIsInvalidAndNetworkIsOffline() = runTest {
        assertThat(api(jpeg = null).read("/p.jpg")).isEqualTo(ApiResult.Err(ApiFailure.Invalid))
        assertThat(api(fail = true).read("/p.jpg")).isEqualTo(ApiResult.Err(ApiFailure.Offline))
        assertThat(api(code = 429).read("/p.jpg")).isEqualTo(ApiResult.Err(ApiFailure.Failed))
    }

    @AcceptanceCriterion("AC-B12")
    @Test fun parsesRecallsAndSendsMakeAndModel() = runTest {
        val r = api(body = """{"recalls":[{"recallNumber":"24-100","title":"Rheem water heaters","recallDate":"2024-03-01","hazard":"Fire","remedy":null,"url":"https://cpsc.gov/x","modelMatch":true}],"cached":false}""")
            .check(" Rheem ", "XG50") as ApiResult.Ok
        assertThat(url).contains("recalls?make=Rheem&model=XG50")
        assertThat(r.value.single().modelMatch).isTrue()
        assertThat(r.value.single().title).isEqualTo("Rheem water heaters")
    }

    @AcceptanceCriterion("AC-B12")
    @Test fun recallFailuresMap() = runTest {
        assertThat(api(fail = true).check("a", null)).isEqualTo(ApiResult.Err(ApiFailure.Offline))
        assertThat(api(code = 502).check("a", null)).isEqualTo(ApiResult.Err(ApiFailure.Failed))
    }
}
