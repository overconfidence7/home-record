package com.kurtz.homerecord.fieldlog.capture

import com.kurtz.homerecord.core.photo.LensChoice

enum class CameraAccess { Unknown, Granted, Denied }

enum class FlashMode { Off, Auto, On }

data class CaptureRoom(val id: String, val name: String)

/** What a shot or import told the person; shown as one line over the shutter row. */
enum class CaptureMessage { SaveFailed, JobGone, NoCamera }

data class CaptureUiState(
    val loading: Boolean = true,
    val jobName: String = "",
    val rooms: List<CaptureRoom> = emptyList(),
    val phases: List<String> = emptyList(),
    val roomId: String? = null,
    val phase: String? = null,
    val flash: FlashMode = FlashMode.Off,
    val lens: LensChoice = LensChoice.Back,
    val canFlip: Boolean = false,
    val access: CameraAccess = CameraAccess.Unknown,
    val saving: Boolean = false,
    val savedCount: Int = 0,
    val message: CaptureMessage? = null,
)

sealed interface CaptureEvent {
    data class SelectRoom(val id: String?) : CaptureEvent
    data class SelectPhase(val phase: String?) : CaptureEvent
    data object CycleFlash : CaptureEvent
    data object Flip : CaptureEvent
    data class PermissionResult(val granted: Boolean) : CaptureEvent
    data class CameraInfo(val hasBack: Boolean, val hasFront: Boolean) : CaptureEvent

    /** Shutter button or volume key. The route takes the picture and answers with [Captured] or [CaptureFailed]. */
    data object Shutter : CaptureEvent
    data class Captured(val path: String) : CaptureEvent
    data object CaptureFailed : CaptureEvent
    data class Imported(val paths: List<String>) : CaptureEvent
    data object OpenSettings : CaptureEvent
    data object DismissMessage : CaptureEvent
}
