package com.kurtz.homerecord.fieldlog

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object TeamKey : NavKey

@Serializable data object CompanyKey : NavKey

@Serializable data object TimesheetsKey : NavKey

@Serializable data object ReportsKey : NavKey

@Serializable data class JobEditKey(val jobId: String? = null) : NavKey

@Serializable data class JobKey(val jobId: String) : NavKey

@Serializable data class JobPlanKey(val homeId: String, val title: String, val floorId: String? = null, val roomId: String? = null, val editShape: Boolean = false) : NavKey

@Serializable data class JobFloorsKey(val homeId: String, val title: String) : NavKey

@Serializable data class JobRoomKey(val roomId: String) : NavKey

@Serializable data class PhotoKey(val photoId: String) : NavKey
@Serializable data class FileKey(val fileId: String) : NavKey
@Serializable data class MarkupKey(val fileId: String) : NavKey
@Serializable data class AnnotateKey(val photoId: String) : NavKey

/** The weather form for a job's daily log on [day] (ISO yyyy-MM-dd). */
@Serializable data class LogWeatherKey(val jobId: String, val day: String) : NavKey

@Serializable data class CaptureKey(val jobId: String) : NavKey

/**
 * Full-screen editors and the camera: the tab bar is hidden while one is on top, so Close/Save
 * (and its discard prompt) or back is the only way out (ui-standards rule 3, rule 30).
 */
fun isFullScreen(key: NavKey): Boolean = when (key) {
    is MarkupKey, is AnnotateKey, is CaptureKey, is LogWeatherKey, is JobEditKey, CompanyKey -> true
    else -> false
}
