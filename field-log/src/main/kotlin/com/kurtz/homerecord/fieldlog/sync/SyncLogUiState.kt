package com.kurtz.homerecord.fieldlog.sync

import com.kurtz.homerecord.core.sync.ConflictEntry
import com.kurtz.homerecord.core.sync.SyncPhase

/**
 * FL-Sync (More > Sync, AC-F52, AC-F53): where sync stands on this phone and the conflict log, newest first.
 * [loading] holds until the log has been read once.
 */
data class SyncLogUiState(
    val phase: SyncPhase = SyncPhase.Idle,
    val pending: Int = 0,
    val entries: List<ConflictEntry> = emptyList(),
    val loading: Boolean = true,
    /** The log or the outbox could not be read; the screen shows what failed and Retry (AC-U4). */
    val loadFailed: Boolean = false,
)

sealed interface SyncLogEvent {
    data object SyncNow : SyncLogEvent
    data object Clear : SyncLogEvent
    data object Back : SyncLogEvent
    data object Retry : SyncLogEvent
}
