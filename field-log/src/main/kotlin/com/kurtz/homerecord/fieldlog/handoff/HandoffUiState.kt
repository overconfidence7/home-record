package com.kurtz.homerecord.fieldlog.handoff

import com.kurtz.homerecord.core.bundle.HandoffSendFailure
import com.kurtz.homerecord.core.bundle.HandoffSummary
import java.time.LocalDate

/** FL-Handoff: what the `.homerecord` for this job's house will carry, Send to homeowner, and the export (AC-F51). */
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
    /** Send to homeowner (AC-B40): the address typed (prefilled from the job's client). */
    val email: String = "",
    /** The address does not look like one; shown once Send is tapped. */
    val emailInvalid: Boolean = false,
    val sending: Boolean = false,
    val sendFailure: HandoffSendFailure? = null,
    /** The claim link went to [sentTo] and works until [sentUntil]. */
    val sentTo: String? = null,
    val sentUntil: LocalDate? = null,
) {
    val busy: Boolean get() = exporting || sending
}

sealed interface HandoffEvent {
    data object Back : HandoffEvent
    data object Export : HandoffEvent
    data object ShareHandled : HandoffEvent
    data class EmailChanged(val value: String) : HandoffEvent
    data object Send : HandoffEvent
    /** After a send: send again, to the same or another address. */
    data object SendAnother : HandoffEvent
}
