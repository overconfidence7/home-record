package com.kurtz.homerecord.core.lookup

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.model.LogWeather
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
class RetrofitWeatherApiTest {
    private var url = ""
    private fun api(code: Int = 200, body: String = "{}", fail: Boolean = false) = RetrofitWeatherApi.create(
        "http://x.test",
        OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
            if (fail) throw IOException("offline")
            val req = chain.request()
            url = req.url.toString()
            Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }).build(),
        now = { 1_000L },
    )

    @AcceptanceCriterion("AC-F31")
    @Test fun readsTheDayAndStampsTheFetch() = runTest {
        val r = api(body = """{"date":"2026-10-30","highF":57.6,"lowF":41,"precipIn":0.12,"code":61,"conditions":"rain","cached":true}""")
            .day(40.06, -75.4, "2026-10-30") as ApiResult.Ok
        assertThat(url).isEqualTo("http://x.test/weather?lat=40.06&lng=-75.4&date=2026-10-30")
        assertThat(r.value).isEqualTo(LogWeather(highF = 58, lowF = 41, precipIn = 0.12, conditions = "rain", fetchedAt = 1_000L))
    }

    @AcceptanceCriterion("AC-F31")
    @Test fun missingValuesStayMissing() = runTest {
        val r = api(body = """{"date":"2026-10-30","highF":null,"lowF":null,"precipIn":null,"code":null,"conditions":"unknown"}""")
            .day(40.0, -75.0, "2026-10-30") as ApiResult.Ok
        assertThat(r.value.highF).isNull()
        assertThat(r.value.conditions).isEqualTo("unknown")
    }

    @AcceptanceCriterion("AC-F31")
    @Test fun failuresMapToOfflineNotYetAndFailed() = runTest {
        assertThat(api(fail = true).day(40.0, -75.0, "2026-10-30")).isEqualTo(ApiResult.Err(ApiFailure.Offline))
        assertThat(api(code = 404, body = """{"error":"not_available"}""").day(40.0, -75.0, "2027-01-01")).isEqualTo(ApiResult.Err(ApiFailure.NotFound))
        assertThat(api(code = 502, body = """{"error":"lookup_failed"}""").day(40.0, -75.0, "2026-10-30")).isEqualTo(ApiResult.Err(ApiFailure.Failed))
    }
}
