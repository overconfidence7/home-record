package com.kurtz.homerecord.fieldlog.jobs

import com.kurtz.homerecord.core.model.JobStatus

/** The tabs of FL-Job, in display order. */
enum class JobTab { OVERVIEW, PHOTOS, LOG, FILES, TASKS, TIME, HOUSE, CHANGES }

data class JobOverviewUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val name: String = "",
    val clientName: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val status: JobStatus = JobStatus.ACTIVE,
    val startDate: String? = null,
    val endDate: String? = null,
    val phases: List<String> = emptyList(),
    val hoursThisWeek: Double = 0.0,
    val photoCount: Int = 0,
    val openTasks: Int = 0,
    /** Next scheduled day (today or later), ISO. */
    val nextDay: String? = null,
    val crew: List<String> = emptyList(),
    val today: String = "",
    /** The job's own house model; null until the first room or the House tab creates it (AC-F48). */
    val homeId: String? = null,
    /** Edit job is Owner and Office only (AC-F3). */
    val canEdit: Boolean = true,
)

sealed interface JobOverviewEvent {
    data class OpenTab(val tab: JobTab) : JobOverviewEvent
    data object OpenEdit : JobOverviewEvent
}
