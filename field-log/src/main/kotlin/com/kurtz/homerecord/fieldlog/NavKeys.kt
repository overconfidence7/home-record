package com.kurtz.homerecord.fieldlog

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object TeamKey : NavKey

@Serializable data object CompanyKey : NavKey

@Serializable data object TimesheetsKey : NavKey

@Serializable data object ReportsKey : NavKey

/** More > Sync: sync status and the conflict log (AC-F52, AC-F53). */
@Serializable data object SyncLogKey : NavKey

@Serializable data class JobEditKey(val jobId: String? = null) : NavKey

/** A job; [tab] (a `JobTab` name) and [day] (ISO yyyy-MM-dd, for the Log tab) open it somewhere other than Overview. */
@Serializable data class JobKey(val jobId: String, val tab: String? = null, val day: String? = null) : NavKey

@Serializable data class JobPlanKey(val homeId: String, val title: String, val floorId: String? = null, val roomId: String? = null, val editShape: Boolean = false) : NavKey

@Serializable data class JobFloorsKey(val homeId: String, val title: String) : NavKey

@Serializable data class JobRoomKey(val roomId: String) : NavKey

@Serializable data class PhotoKey(val photoId: String) : NavKey
@Serializable data class FileKey(val fileId: String) : NavKey
@Serializable data class MarkupKey(val fileId: String) : NavKey
@Serializable data class AnnotateKey(val photoId: String) : NavKey

/** The weather form for a job's daily log on [day] (ISO yyyy-MM-dd). */
@Serializable data class LogWeatherKey(val jobId: String, val day: String) : NavKey

/** The daily log's PDF for [day] (ISO yyyy-MM-dd) in the in-app viewer (AC-F33). */
@Serializable data class LogPdfKey(val jobId: String, val day: String) : NavKey

@Serializable data class CaptureKey(val jobId: String) : NavKey

/**
 * The task form for [jobId]; [taskId] null adds a new task. A new task from a file's "New task here" starts pinned on
 * [fileId] page [page] (0-based) at fractions [x], [y] (AC-F35).
 */
@Serializable data class TaskFormKey(
    val jobId: String,
    val taskId: String? = null,
    val fileId: String? = null,
    val page: Int? = null,
    val x: Float? = null,
    val y: Float? = null,
) : NavKey

/**
 * Full-screen editors and the camera: the tab bar is hidden while one is on top, so Close/Save
 * (and its discard prompt) or back is the only way out (ui-standards rule 3, rule 30).
 */
fun isFullScreen(key: NavKey): Boolean = when (key) {
    is MarkupKey, is AnnotateKey, is CaptureKey, is LogWeatherKey, is JobEditKey, is TaskFormKey, CompanyKey -> true
    else -> false
}
