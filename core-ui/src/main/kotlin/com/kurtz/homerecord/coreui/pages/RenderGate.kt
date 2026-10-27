package com.kurtz.homerecord.coreui.pages

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One-at-a-time access to a resource that a screen may close while a render is still running
 * (leaving the viewer mid-page). [close] waits for the running block, so the resource is never
 * closed under it, and later blocks get null instead of touching a closed resource.
 */
class RenderGate(io: CoroutineDispatcher = Dispatchers.IO) {
    private val lock = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + io)
    private var closed = false // guarded by lock

    suspend fun <T> use(block: suspend () -> T): T? = lock.withLock { if (closed) null else block() }

    /** Runs [release] once, after any block in [use] has finished; safe to call from onCleared. */
    fun close(release: () -> Unit) {
        scope.launch(NonCancellable) {
            lock.withLock {
                if (!closed) {
                    closed = true
                    runCatching(release)
                }
            }
        }
    }
}
