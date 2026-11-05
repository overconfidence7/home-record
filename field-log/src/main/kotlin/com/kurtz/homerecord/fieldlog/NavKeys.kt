package com.kurtz.homerecord.fieldlog

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object TeamKey : NavKey

@Serializable data object CompanyKey : NavKey

/** FL-Paywall from More, Owner only (AC-F56). */
@Serializable data object PlanKey : NavKey

/** FL-Timesheets; [week] (ISO Monday) opens that week instead of this one, as a timesheet-approved notification does. */
@Serializable data class TimesheetsKey(val week: String? = null) : NavKey

@Serializable data object ReportsKey : NavKey

/** FL-Clients (AC-F45). */
@Serializable data object ClientsKey : NavKey

/** FL-Materials: receipts by job with totals (AC-F44). */
@Serializable data object MaterialsKey : NavKey
/** Add ([materialId] null) or edit a receipt (AC-F44). */
@Serializable data class MaterialFormKey(val materialId: String? = null) : NavKey

/** More > Sync: sync status and the conflict log (AC-F52, AC-F53). */
@Serializable data object SyncLogKey : NavKey

/** More > Settings: units, notifications, storage on this phone (T065a). */
@Serializable data object SettingsKey : NavKey

/** More > About (T065a). */
@Serializable data object AboutKey : NavKey

@Serializable data class JobEditKey(val jobId: String? = null) : NavKey

/** A job; [tab] (a `JobTab` name) and [day] (ISO yyyy-MM-dd, for the Log tab) open it somewhere other than Overview. */
@Serializable data class JobKey(val jobId: String, val tab: String? = null, val day: String? = null) : NavKey

@Serializable data class JobPlanKey(val homeId: String, val title: String, val floorId: String? = null, val roomId: String? = null, val editShape: Boolean = false, val overlayId: String? = null) : NavKey

@Serializable data class JobFloorsKey(val homeId: String, val title: String) : NavKey

/** The job's house in 3D, opening on [floorId] (the floor the House tab shows) at the Rough-in stage (AC-C20). */
@Serializable data class JobHouse3DKey(val homeId: String, val title: String, val floorId: String? = null, val roomId: String? = null) : NavKey

@Serializable data class JobRoomKey(val roomId: String) : NavKey

/** FL-Item: an item installed on the job (water heater, panel, fixture) with its warranty (AC-F50). */
@Serializable data class JobItemKey(val homeId: String, val itemId: String) : NavKey
/** FL-Handoff: the summary and `.homerecord` export of a job's house (AC-F51). */
@Serializable data class JobHandoffKey(val homeId: String, val title: String) : NavKey

/** The item form for the job's house (shared with House Manual); [itemId] null adds one in [roomId], [scanPlate] opens the data plate camera first. */
@Serializable data class JobItemEditKey(val homeId: String, val itemId: String? = null, val roomId: String? = null, val scanPlate: Boolean = false) : NavKey

/** Place on plan for an installed item (AC-C30 position). */
@Serializable data class JobItemPlaceKey(val homeId: String, val itemId: String) : NavKey

/** FL-BehindWall: the job's rooms, then a room's walls, then the camera; the photo is saved at Rough-in, then Finished (AC-F49). */
@Serializable data class JobBehindWallKey(val homeId: String, val title: String) : NavKey

/** The outline screen for a new "behind the wall" photo (AC-C17, AC-C18); [stage] is a `WallStage` name. */
@Serializable data class JobWallOutlineKey(val roomId: String, val wallId: String, val stage: String, val photoPath: String) : NavKey
/** FL wall: draw and edit overlays on one wall of the job's house (AC-C25..C28). */
@Serializable data class JobWallKey(val roomId: String, val wallId: String, val stage: String, val overlayId: String? = null) : NavKey

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

/** The change order form for [jobId]; [changeOrderId] null writes a new one (AC-F37). */
@Serializable data class ChangeOrderKey(val jobId: String, val changeOrderId: String? = null) : NavKey
/** Sign on phone for a Draft change order (AC-F38). */
@Serializable data class ChangeOrderSignKey(val jobId: String, val changeOrderId: String) : NavKey

/**
 * Full-screen editors and the camera: the tab bar is hidden while one is on top, so Close/Save
 * (and its discard prompt) or back is the only way out (ui-standards rule 3, rule 30).
 */
fun isFullScreen(key: NavKey): Boolean = when (key) {
    is MarkupKey, is AnnotateKey, is CaptureKey, is LogWeatherKey, is JobEditKey, is TaskFormKey, is ChangeOrderKey, is ChangeOrderSignKey, is MaterialFormKey, is JobWallOutlineKey, is JobWallKey, is JobItemEditKey, CompanyKey, PlanKey -> true
    else -> false
}
