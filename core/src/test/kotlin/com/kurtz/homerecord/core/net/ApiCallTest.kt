package com.kurtz.homerecord.core.net

import com.google.common.truth.Truth.assertThat
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertThrows
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

// cr:api-call-dup: one error mapping for every Retrofit client
class ApiCallTest {
    private fun http(code: Int) = HttpException(Response.error<Unit>(code, "{}".toResponseBody()))

    private fun failureOf(block: suspend () -> Unit, onHttp: (HttpException) -> ApiFailure = ::failureForStatus) =
        (runBlocking { apiCall(onHttp, block) } as ApiResult.Err).failure

    @Test fun okWrapsTheValue() {
        assertThat(runBlocking { apiCall { 42 } }).isEqualTo(ApiResult.Ok(42))
    }

    @Test fun statusesMapToFailures() {
        assertThat(failureOf({ throw http(404) })).isEqualTo(ApiFailure.NotFound)
        assertThat(failureOf({ throw http(403) })).isEqualTo(ApiFailure.Forbidden)
        assertThat(failureOf({ throw http(400) })).isEqualTo(ApiFailure.Invalid)
        assertThat(failureOf({ throw http(413) })).isEqualTo(ApiFailure.Invalid)
        assertThat(failureOf({ throw http(500) })).isEqualTo(ApiFailure.Failed)
        assertThat(failureOf({ throw http(404) }, ::invalidOrFailed)).isEqualTo(ApiFailure.Failed)
        assertThat(failureOf({ throw http(400) }, ::invalidOrFailed)).isEqualTo(ApiFailure.Invalid)
    }

    @Test fun noNetworkIsOfflineAndAnUnreadableBodyIsFailed() {
        assertThat(failureOf({ throw IOException("no route") })).isEqualTo(ApiFailure.Offline)
        assertThat(failureOf({ throw SerializationException("unexpected field") })).isEqualTo(ApiFailure.Failed)
    }

    @Test fun cancellationIsNotSwallowed() {
        assertThrows(CancellationException::class.java) { runBlocking { apiCall { throw CancellationException("left") } } }
    }
}
