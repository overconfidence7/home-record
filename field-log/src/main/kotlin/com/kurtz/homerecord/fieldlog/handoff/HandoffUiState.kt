package com.kurtz.homerecord.fieldlog.handoff

import com.kurtz.homerecord.core.bundle.HandoffSummary

/** FL-Handoff: what the `.homerecord` for this job's house will carry, and the export (AC-F51). */
data class HandoffUiState(
    val loading: Boolean = true,
    val jobName: String = "",
    val summary: HandoffSummary? = null,
    /** The job's house is gone (deleted on another phone). */
    val missing: Boolean = false,
    val exporting: Boolean = false,
    /** The finished file, waiting for the route to open the share sheet. */
    val sharePath: String? = null,
    val failed: Boolean = false,
)

sealed interface HandoffEvent {
    data object Back : HandoffEvent
    data object Export : HandoffEvent
    data object ShareHandled : HandoffEvent
}
