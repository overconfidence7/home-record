package com.kurtz.homerecord.core.company

import com.google.common.truth.Truth.assertThat
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
import org.junit.Test

private class Recorder(val code: Int = 200, val body: String = "{}") {
    var method = ""
    var path = ""
    var sent = ""
    val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        val req: Request = chain.request()
        method = req.method
        path = req.url.encodedPath
        sent = req.body?.let { Buffer().also { b -> it.writeTo(b) }.readUtf8() }.orEmpty()
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }).build()
    fun api() = RetrofitCompanyApi.create("http://x.test", client)
}

class RetrofitCompanyApiTest {
    @Test fun meKeepsOnlyCompanyMemberships() = runTest {
        val r = Recorder(body = """{"user":{"id":"u","email":"a@b.com"},"memberships":[
            {"kind":"home","id":"h1","role":"owner","name":"Home"},
            {"kind":"company","id":"c1","role":"lead","name":"Kurtz Builders","trialEndsAt":5}]}""")
        val result = r.api().me() as ApiResult.Ok
        assertThat(result.value).containsExactly(CompanyMembership("c1", "Kurtz Builders", CompanyRole.Lead, 5, null))
    }

    @Test fun createSendsTrimmedFieldsAndOmitsBlanks() = runTest {
        val r = Recorder(body = """{"id":"c9","role":"owner"}""")
        assertThat(r.api().create(" Kurtz ", "", "555")).isEqualTo(ApiResult.Ok("c9"))
        assertThat(r.path).isEqualTo("/companies")
        assertThat(r.sent).isEqualTo("""{"name":"Kurtz","phone":"555"}""")
    }

    @Test fun badCodeMapsToInvalidCode() = runTest {
        val r = Recorder(404, """{"error":"invalid_code"}""")
        assertThat(r.api().join("ZZZZZZZZ")).isEqualTo(ApiResult.Err(ApiFailure.InvalidCode))
    }

    @Test fun statusCodesMap() = runTest {
        assertThat(Recorder(404, """{"error":"not_found"}""").api().members("c")).isEqualTo(ApiResult.Err(ApiFailure.NotFound))
        assertThat(Recorder(403, """{"error":"forbidden"}""").api().remove("c", "u")).isEqualTo(ApiResult.Err(ApiFailure.Forbidden))
        assertThat(Recorder(400, """{"error":"invalid"}""").api().invite("c")).isEqualTo(ApiResult.Err(ApiFailure.Invalid))
        assertThat(Recorder(500, "boom").api().invite("c")).isEqualTo(ApiResult.Err(ApiFailure.Failed))
    }

    @Test fun networkFailureIsOffline() = runTest {
        val client = OkHttpClient.Builder().addInterceptor(Interceptor { throw IOException("down") }).build()
        assertThat(RetrofitCompanyApi.create("http://x.test", client).invite("c")).isEqualTo(ApiResult.Err(ApiFailure.Offline))
    }

    @Test fun membersMapRolesAndRates() = runTest {
        val r = Recorder(body = """{"members":[{"userId":"u1","name":null,"email":"o@x.com","role":"owner","rateCents":null},
            {"userId":"u2","name":"Sam","email":"s@x.com","role":"crew","rateCents":3500}]}""")
        val list = (r.api().members("c1") as ApiResult.Ok).value
        assertThat(list.map { it.role }).containsExactly(CompanyRole.Owner, CompanyRole.Crew).inOrder()
        assertThat(list[1].rateCents).isEqualTo(3500L)
        assertThat(r.path).isEqualTo("/companies/c1/members")
    }

    @Test fun setRoleAndRateUsePatchOnTheMember() = runTest {
        val r = Recorder()
        r.api().setRole("c1", "u2", CompanyRole.Lead)
        assertThat(r.method).isEqualTo("PATCH")
        assertThat(r.path).isEqualTo("/companies/c1/members/u2")
        assertThat(r.sent).isEqualTo("""{"role":"lead"}""")
        r.api().setRate("c1", "u2", 4200)
        assertThat(r.sent).isEqualTo("""{"rateCents":4200}""")
    }

    @Test fun profileReadsSettingsWithDefaults() = runTest {
        val r = Recorder(body = """{"id":"c1","name":"Kurtz","phone":"555","settings":{"otRule":"weekly40","requireGps":true,"punchDistanceFt":300}}""")
        val p = (r.api().profile("c1") as ApiResult.Ok).value
        assertThat(p.settings).isEqualTo(CompanySettings(OvertimeRule.Weekly40, breakRule = false, requireGps = true, punchDistanceFt = 300, photoOnPunch = false))
        assertThat(r.path).isEqualTo("/companies/c1/profile")
    }

    @Test fun updateProfileSendsSettingsWireNames() = runTest {
        val r = Recorder()
        r.api().updateProfile("c1", CompanyProfileUpdate(email = "o@k.com", settings = CompanySettings(OvertimeRule.Daily8, requireGps = true)))
        assertThat(r.sent).contains(""""otRule":"daily8"""")
        assertThat(r.sent).contains(""""email":"o@k.com"""")
    }
}
