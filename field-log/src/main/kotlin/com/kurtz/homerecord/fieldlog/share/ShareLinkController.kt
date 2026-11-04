package com.kurtz.homerecord.fieldlog.share

import com.kurtz.homerecord.core.share.ShareTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A job's share links (AC-B35) as the screens use them: the live URL, make one, turn it off, hand it to the share sheet. */
interface JobLinks {
    fun observe(target: ShareTarget): Flow<String?>
    suspend fun make(target: ShareTarget): String?
    suspend fun turnOff(target: ShareTarget): Boolean
    /** Opens the share sheet with the link; [detail] is the log's day for a log link. False when it could not open. */
    suspend fun share(target: ShareTarget, url: String, detail: String?): Boolean
}

/** The Share link control on the job's Photos tab and on a sent daily log (AC-F28 gallery link, AC-B35). */
data class ShareLinkUi(
    /** Owner or Office, and there is something to link to; otherwise the control is not shown. */
    val allowed: Boolean = false,
    /** The live link, or null while there is none. */
    val url: String? = null,
    val working: Boolean = false,
    /** The link could not be made (the job is gone) or the share sheet could not open. */
    val failed: Boolean = false,
    val confirmOff: Boolean = false,
) {
    val canShare: Boolean get() = allowed && !working
}

sealed interface ShareLinkEvent {
    /** Makes the link if there is none, then opens the share sheet with it. */
    data object Share : ShareLinkEvent
    data object AskOff : ShareLinkEvent
    data object DismissOff : ShareLinkEvent
    /** Turn off link: the page stops working for anyone who has it; the next Share link makes a new one. */
    data object TurnOff : ShareLinkEvent
}

private data class Transient(val working: Boolean = false, val failed: Boolean = false, val confirmOff: Boolean = false)

/**
 * The share-link logic both screens share, kept out of the composables: [target] is null while there is nothing to link
 * (no photos yet, the log not sent), [allowed] is Owner or Office (the backend refuses anyone else). A change of target
 * (another day's log) clears the last failure and any open confirm.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ShareLinkController(
    private val scope: CoroutineScope,
    private val links: JobLinks,
    allowed: Flow<Boolean>,
    target: Flow<ShareTarget?>,
    private val detail: () -> String? = { null },
) {
    private val current: StateFlow<ShareTarget?> = target.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)
    private val transient = MutableStateFlow(Transient())

    val state: StateFlow<ShareLinkUi> = combine(
        allowed.distinctUntilChanged(),
        current,
        current.flatMapLatest { t -> t?.let(links::observe) ?: flowOf(null) },
        transient,
    ) { ok, t, url, tr ->
        ShareLinkUi(allowed = ok && t != null, url = url.takeIf { t != null }, working = tr.working, failed = tr.failed, confirmOff = tr.confirmOff)
    }.stateIn(scope, SharingStarted.Eagerly, ShareLinkUi())

    init {
        scope.launch { current.collect { transient.update { it.copy(failed = false, confirmOff = false) } } }
    }

    fun onEvent(e: ShareLinkEvent) {
        when (e) {
            ShareLinkEvent.Share -> share()
            ShareLinkEvent.AskOff -> if (state.value.allowed && state.value.url != null) transient.update { it.copy(confirmOff = true) }
            ShareLinkEvent.DismissOff -> transient.update { it.copy(confirmOff = false) }
            ShareLinkEvent.TurnOff -> turnOff()
        }
    }

    private fun share() {
        val t = current.value ?: return
        if (!state.value.canShare) return
        transient.update { it.copy(working = true, failed = false) }
        scope.launch {
            val url = links.make(t)
            val shared = url != null && links.share(t, url, detail())
            transient.update { it.copy(working = false, failed = !shared) }
        }
    }

    private fun turnOff() {
        val t = current.value ?: return
        if (!state.value.allowed) return
        transient.update { it.copy(confirmOff = false, working = true) }
        scope.launch {
            links.turnOff(t)
            transient.update { it.copy(working = false, failed = false) }
        }
    }
}
