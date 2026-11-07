package com.kurtz.homerecord.core.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import java.util.logging.Level
import java.util.logging.Logger

/**
 * A list's load that can fail and be retried (AC-U4: every list has an error state). [wrap] runs the
 * upstream flow; when it throws (a Room query on a damaged database, a bad row a mapper chokes on) the
 * screen gets [failed] instead of the ViewModel scope dying with the exception, and [retry] subscribes
 * to a fresh upstream. [onFailure] gets the cause; by default it goes to logcat (java.util.logging reaches
 * logcat on Android and stays a plain JVM call in unit tests) under [what].
 */
class Reloadable(
    what: String,
    private val onFailure: (Throwable) -> Unit = { Logger.getLogger("HomeRecord").log(Level.WARNING, "$what failed to load", it) },
) {
    private val attempt = MutableStateFlow(0)

    fun retry() = attempt.update { it + 1 }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> wrap(failed: T, upstream: () -> Flow<T>): Flow<T> = attempt.flatMapLatest {
        upstream().catch { e ->
            if (e is CancellationException) throw e
            onFailure(e)
            emit(failed)
        }
    }
}
