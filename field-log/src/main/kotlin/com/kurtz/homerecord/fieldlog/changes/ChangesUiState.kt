package com.kurtz.homerecord.fieldlog.changes

import androidx.compose.runtime.Immutable
import com.kurtz.homerecord.core.model.ChangeOrderStatus
import com.kurtz.homerecord.core.model.JobChangeOrder

/** One row on a job's Changes tab (FL-ChangeOrder, AC-F37). */
@Immutable
data class ChangeRow(
    val id: String,
    val title: String,
    val status: ChangeOrderStatus,
    val totalCents: Long,
    val clientName: String? = null,
    val lineCount: Int = 0,
)

@Immutable
data class ChangesUiState(
    val loading: Boolean = true,
    val rows: List<ChangeRow> = emptyList(),
    /** Owner, Office and the job's Lead write and open change orders (AC-F3); Crew see the list only. */
    val canManage: Boolean = false,
    /** The change orders could not be read; the tab shows what failed and Retry (AC-U4). */
    val loadFailed: Boolean = false,
) {
    /** What the drafts still waiting on the client add up to; null when there are none. */
    val draftsTotalCents: Long? get() = rows.filter { it.status == ChangeOrderStatus.DRAFT }.takeIf { it.isNotEmpty() }?.sumOf { it.totalCents }

    companion object {
        fun of(orders: List<JobChangeOrder>, canManage: Boolean) = ChangesUiState(
            loading = false, canManage = canManage,
            rows = orders.map { ChangeRow(it.id, it.title, it.status, it.totalCents, it.clientName, it.lines.size) },
        )
    }
}

sealed interface ChangesEvent {
    data class Open(val id: String) : ChangesEvent
    data object Add : ChangesEvent
    data object Retry : ChangesEvent
}
