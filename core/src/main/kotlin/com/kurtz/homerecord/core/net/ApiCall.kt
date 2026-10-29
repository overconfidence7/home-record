package com.kurtz.homerecord.core.net

import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

/** The usual meaning of an HTTP error status. 413: larger than the server allows; trying again will not help. */
fun failureForStatus(e: HttpException): ApiFailure = when (e.code()) {
    404 -> ApiFailure.NotFound
    403 -> ApiFailure.Forbidden
    400, 413 -> ApiFailure.Invalid
    else -> ApiFailure.Failed
}

/**
 * Runs one backend call: an HTTP error maps through [onHttp], no network is Offline, and a response body this app
 * cannot read (a newer or broken server) is Failed instead of a crash. Cancellation still propagates.
 */
suspend fun <T> apiCall(onHttp: (HttpException) -> ApiFailure = ::failureForStatus, block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Ok(block())
    } catch (e: HttpException) {
        ApiResult.Err(onHttp(e))
    } catch (e: IOException) {
        ApiResult.Err(ApiFailure.Offline)
    } catch (e: SerializationException) {
        ApiResult.Err(ApiFailure.Failed)
    }

/** For the lookups: a request the server refuses as malformed is Invalid; any other status is Failed. */
fun invalidOrFailed(e: HttpException): ApiFailure = if (e.code() == 400) ApiFailure.Invalid else ApiFailure.Failed
