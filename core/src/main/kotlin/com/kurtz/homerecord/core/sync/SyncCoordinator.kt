package com.kurtz.homerecord.core.sync

import com.kurtz.homerecord.core.company.ApiResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** One sync pass for a tenant; [SyncEngine] is the real one, tests pass a counting stub. */
fun interface SyncRunner {
    suspend fun sync(tenant: TenantRef): ApiResult<*>
}

/** Whether the device has a usable network, emitting on every change so a reconnect can trigger a sync. */
interface ConnectivityMonitor {
    val online: Flow<Boolean>
}

/**
 * Decides when to sync (AC-F53): on sign-in or when the tenant appears, shortly after each local write
 * ([requestSync]), when the network returns, and every [intervalMs] while the app is in the foreground.
 * Nothing runs while signed out or without a tenant; local writes keep queuing in the outbox meanwhile.
 */
class SyncCoordinator(
    private val scope: CoroutineScope,
    private val runner: SyncRunner,
    private val signedIn: Flow<Boolean>,
    private val tenant: Flow<TenantRef?>,
    private val connectivity: ConnectivityMonitor,
    private val intervalMs: Long = 30_000,
    private val writeDebounceMs: Long = 1_000,
) {
    private val foreground = MutableStateFlow(false)
    private val kicks = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Called by the activity from onStart (true) and onStop (false). */
    fun setForeground(value: Boolean) {
        foreground.value = value
    }

    /** Ask for a sync soon, after a local write or when the person taps the status chip. */
    fun requestSync() {
        kicks.tryEmit(Unit)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun start() {
        val active = combine(signedIn, tenant) { auth, t -> t.takeIf { auth } }.distinctUntilChanged()
        val ticks = foreground.flatMapLatest { on ->
            if (on) flow { while (true) { delay(intervalMs); emit(Unit) } } else emptyFlow()
        }
        val reconnects = connectivity.online.distinctUntilChanged().drop(1).filter { it }.map { }
        val writes = kicks.debounce(writeDebounceMs)
        scope.launch {
            active.collectLatest { t ->
                if (t == null) return@collectLatest
                runner.sync(t)
                merge(ticks, reconnects, writes).collect { runner.sync(t) }
            }
        }
    }
}
