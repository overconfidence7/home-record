package com.kurtz.homerecord.fieldlog.widget

import com.kurtz.homerecord.core.punch.ClockStatus

/** What the 2x2 clock widget shows (AC-F17). */
sealed interface ClockWidgetState {
    /** Nobody is signed in, or no company yet: the button opens the app. */
    data object SignedOut : ClockWidgetState
    data object Out : ClockWidgetState
    data class Working(val jobName: String?, val sinceAt: Long) : ClockWidgetState
    data class OnBreak(val jobName: String?, val sinceAt: Long) : ClockWidgetState
}

object ClockWidgetStateBuilder {
    fun build(status: ClockStatus, jobNames: Map<String, String>): ClockWidgetState = when (status) {
        ClockStatus.Out -> ClockWidgetState.Out
        is ClockStatus.Working -> ClockWidgetState.Working(status.jobId?.let(jobNames::get), status.shiftStartAt)
        is ClockStatus.OnBreak -> ClockWidgetState.OnBreak(status.jobId?.let(jobNames::get), status.breakStartAt)
    }
}
