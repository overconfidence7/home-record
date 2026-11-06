package com.kurtz.homerecord.core.share

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
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Test

/** AC-H16: HM-Share's calls to backend/src/routes/homeShare.ts, and reading invite links. */
@AcceptanceCriterion("AC-H16")
class RetrofitHomeShareApiTest {
    private val requests = mutableListOf<Request>()
    private val token = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcd"

    private fun api(code: Int = 200, body: String = "{}", fail: Boolean = false) =
        RetrofitHomeShareApi.create(
            "http://x.test",
            OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
                if (fail) throw IOException("offline")
                val req = chain.request()
                requests += req
                Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
                    .body(body.toResponseBody("application/json".toMediaType())).build()
            }).build(),
        )

    @Test fun inviteLinksAreRecognised() { // AC-H16
        assertThat(HomeInviteLink.token("homerecord://invite/$token")).isEqualTo(token)
        assertThat(HomeInviteLink.token("https://api.homerecord.app/invite/$token?utm=x")).isEqualTo(token)
        assertThat(HomeInviteLink.token("homerecord://handoff/$token")).isNull()
        assertThat(HomeInviteLink.token("homerecord://invite/short")).isNull()
        assertThat(HomeInviteLink.token(null)).isNull()
    }

    @Test fun peopleAndOpenLinksAreRead() = runTest { // AC-H16
        val r = api(
            body = """{"people":[{"userId":"u1","name":"Dana","email":"d@x.com","role":"owner","you":true},
                {"userId":"u2","name":null,"email":"s@x.com","role":"member"},{"userId":"u3","email":"v@x.com","role":"viewer"}],
                "invites":[{"id":"i1","role":"viewer","createdAt":1,"expiresAt":2}]}""",
        ).people("h1")
        val people = (r as ApiResult.Ok).value
        assertThat(people.people.map { it.role }).containsExactly(HomeRole.Owner, HomeRole.Editor, HomeRole.Viewer).inOrder()
        assertThat(people.people.first().you).isTrue()
        assertThat(people.invites.single()).isEqualTo(OpenInvite("i1", HomeRole.Viewer, 1, 2))
        assertThat(requests.single().url.encodedPath).isEqualTo("/homes/h1/people")
    }

    @Test fun inviteSendsTheRoleAndReturnsTheLink() = runTest { // AC-H16
        val r = api(body = """{"id":"i9","role":"editor","link":"https://x.test/invite/$token","token":"$token","expiresAt":5}""")
            .invite("h1", HomeRole.Editor)
        assertThat(r).isEqualTo(ApiResult.Ok(NewInvite("i9", HomeRole.Editor, "https://x.test/invite/$token", 5)))
        val req = requests.single()
        assertThat(req.method).isEqualTo("POST")
        assertThat(req.url.encodedPath).isEqualTo("/homes/h1/invites")
        val sent = Buffer().also { req.body?.writeTo(it) }.readUtf8()
        assertThat(sent).isEqualTo("""{"role":"editor"}""")
    }

    @Test fun revokeAndRemoveDelete() = runTest { // AC-H16
        assertThat(api(body = """{"ok":true}""").revoke("h1", "i1")).isEqualTo(ApiResult.Ok(Unit))
        assertThat(api(body = """{"ok":true}""").remove("h1", "u2")).isEqualTo(ApiResult.Ok(Unit))
        assertThat(requests.map { it.method to it.url.encodedPath }).containsExactly("DELETE" to "/homes/h1/invites/i1", "DELETE" to "/homes/h1/people/u2").inOrder()
        assertThat(api(fail = true).remove("h1", "u2")).isEqualTo(ApiResult.Err(ApiFailure.Offline))
    }

    @Test fun acceptJoinsOrSaysWhyNot() = runTest { // AC-H16
        assertThat(api(body = """{"homeId":"h1","homeName":"Maple St","role":"viewer"}""").accept(token))
            .isEqualTo(AcceptResult.Joined(JoinedHome("h1", "Maple St", HomeRole.Viewer)))
        assertThat(requests.single().url.encodedPath).isEqualTo("/invite/$token/accept")
        assertThat(api(code = 409).accept(token)).isEqualTo(AcceptResult.Failed(InviteFailure.Used))
        assertThat(api(code = 410).accept(token)).isEqualTo(AcceptResult.Failed(InviteFailure.Expired))
        assertThat(api(code = 404).accept(token)).isEqualTo(AcceptResult.Failed(InviteFailure.NotFound))
        assertThat(api(code = 401).accept(token)).isEqualTo(AcceptResult.Failed(InviteFailure.SignedOut))
        assertThat(api(code = 500).accept(token)).isEqualTo(AcceptResult.Failed(InviteFailure.Failed))
        assertThat(api(fail = true).accept(token)).isEqualTo(AcceptResult.Failed(InviteFailure.Offline))
    }

    @Test fun onlyAViewerIsReadOnlyAndUnknownRolesAreNeverOwner() { // AC-H16
        assertThat(HomeRole.parse("viewer").canEdit).isFalse()
        assertThat(HomeRole.parse("editor").canEdit).isTrue()
        assertThat(HomeRole.parse("something-new")).isEqualTo(HomeRole.Editor)
        assertThat(HomeRole.parse(null)).isEqualTo(HomeRole.Editor)
    }
}
