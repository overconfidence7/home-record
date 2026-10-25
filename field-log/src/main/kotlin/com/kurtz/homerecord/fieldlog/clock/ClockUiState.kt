package com.kurtz.homerecord.fieldlog.clock

import com.kurtz.homerecord.core.punch.ClockStatus

data class ClockJobOption(val id: String, val name: String, val address: String?, val lat: Double? = null, val lng: Double? = null)

/** The far-from-job reason sheet (AC-F9). */
data class FarReasonSheet(val distanceFt: Int, val choice: String? = null, val other: String = "") {
    val reason: String? get() = if (choice == OTHER) other.trim().takeIf { it.isNotEmpty() } else choice
    val canSave: Boolean get() = reason != null

    companion object {
        const val OTHER = "Other"
        val choices = listOf("Picking up materials", "Supply house", "Shop", OTHER)
    }
}

/** One teammate a lead can clock in; [onClock] people are shown but cannot be picked (AC-F11). */
data class CrewPick(val id: String, val name: String, val onClock: Boolean = false)

/** What the last crew clock-in did, for the confirmation line (AC-F11). */
data class CrewNotice(val punched: Int, val skippedNames: List<String>)

data class ClockUiState(
    val loading: Boolean = true,
    val status: ClockStatus = ClockStatus.Out,
    val jobs: List<ClockJobOption> = emptyList(),
    val selectedJobId: String? = null,
    val note: String = "",
    /** Wall clock for the elapsed ticker. */
    val now: Long = 0L,
    /** Clock in is waiting on a location fix (at most 5 s, AC-F8). */
    val gettingLocation: Boolean = false,
    /** Explain location before the system prompt, first clock in only (AC-F18). */
    val explainLocation: Boolean = false,
    val requestPermission: Boolean = false,
    val farReason: FarReasonSheet? = null,
    /** The company requires GPS and there is no fix; true when the permission is the missing piece (AC-F18). */
    val gpsRequired: Boolean? = null,
    val savedWithoutLocation: Boolean = false,
    val sheet: WeekSheet? = null,
    /** Leads (and Owner/Office) can clock in the crew from their own phone (AC-F11). */
    val canLeadCrew: Boolean = false,
    val crew: List<CrewPick> = emptyList(),
    val crewSelected: Set<String> = emptySet(),
    val crewNotice: CrewNotice? = null,
) {
    val crewCount: Int get() = crewSelected.size
    val selectedJob: ClockJobOption? get() = jobs.firstOrNull { it.id == selectedJobId }
    val currentJob: ClockJobOption? get() = when (val s = status) {
        is ClockStatus.Working -> jobs.firstOrNull { it.id == s.jobId }
        is ClockStatus.OnBreak -> jobs.firstOrNull { it.id == s.jobId }
        ClockStatus.Out -> null
    }
    val shiftStartAt: Long? get() = when (val s = status) {
        is ClockStatus.Working -> s.shiftStartAt
        is ClockStatus.OnBreak -> s.shiftStartAt
        ClockStatus.Out -> null
    }
    val elapsedMillis: Long get() = shiftStartAt?.let { (now - it).coerceAtLeast(0) } ?: 0L
}

sealed interface ClockEvent {
    data class SelectJob(val id: String) : ClockEvent
    data class Note(val text: String) : ClockEvent
    data object ClockIn : ClockEvent
    data object StartBreak : ClockEvent
    data object EndBreak : ClockEvent
    data object SwitchJob : ClockEvent
    data object ClockOut : ClockEvent
    data object ExplainContinue : ClockEvent
    data object ExplainSkip : ClockEvent
    data class PermissionResult(val granted: Boolean) : ClockEvent
    data class PickReason(val choice: String) : ClockEvent
    data class OtherReason(val text: String) : ClockEvent
    data object SaveReason : ClockEvent
    data object CancelReason : ClockEvent
    data object DismissGps : ClockEvent
    data object OpenSettings : ClockEvent
    data object DismissNotice : ClockEvent
    data class ToggleCrew(val id: String) : ClockEvent
    data object ClockInCrew : ClockEvent
    data object DismissCrewNotice : ClockEvent
}
