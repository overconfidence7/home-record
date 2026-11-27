package com.kurtz.homerecord.housemodel.ui

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher

/**
 * One thread that plays the device's main thread, with `Dispatchers.Main.immediate` semantics: work started on it
 * runs at once, work resumed from another thread (Room's) is queued onto it. Set it as Main and run the test body
 * on it, so a ViewModel's coroutines, its SavedStateHandle and its plain fields are only touched there, as on a
 * device. An Unconfined Main let them resume on Room's threads while the test thread sent the next event.
 */
class TestMainThread : CoroutineDispatcher(), AutoCloseable {
    @Volatile private var thread: Thread? = null
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "test-main").also { thread = it } }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean = Thread.currentThread() !== thread

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        executor.execute(block)
    }

    override fun close() {
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }
}
