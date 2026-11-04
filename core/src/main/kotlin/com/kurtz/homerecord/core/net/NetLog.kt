package com.kurtz.homerecord.core.net

import android.util.Log

/**
 * A logcat trace for backend calls that fail (no network, an error status, a body this app cannot read), so QA and
 * developers can tell from device logs why sign-in or sync said "offline" or "try again". Never logs request bodies,
 * emails, codes or tokens: only which call, the status and the exception class and message. [sink] is swapped in tests.
 */
object NetLog {
    const val TAG = "HomeRecordNet"

    @Volatile
    var sink: (message: String, error: Throwable?) -> Unit = { message, error ->
        // Plain JVM tests run against the stub android.jar, where Log throws; logging must never fail a call.
        runCatching { Log.w(TAG, message, error) }
    }

    fun failed(call: String, error: Throwable) {
        val status = (error as? retrofit2.HttpException)?.code()?.let { " HTTP $it" }.orEmpty()
        sink("$call failed:$status ${error.javaClass.simpleName}: ${error.message.orEmpty()}", error)
    }
}
