package com.kurtz.homerecord.fieldlog.capture

import com.kurtz.homerecord.core.photo.LensChoice

enum class CameraAccess { Unknown, Granted, Denied }

enum class FlashMode { Off, Auto, On }

data class CaptureRoom(val id: String, val name: String)

/** The Before shot drawn at 30% over the preview so the After lines up (AC-F25). */
data class GhostShot(val photoId: String, val path: String)

/** What a shot or import told the person; shown as one line over the shutter row. */
enum class CaptureMessage { SaveFailed, JobGone, NoCamera, NoteSaved, MemoSaved, MemoFailed, MicDenied }

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
    val noteOpen: Boolean = false,
    val noteText: String = "",
    val recording: Boolean = false,
    val recordedSeconds: Int = 0,
    val ghost: GhostShot? = null,
    /** Off until the person turns it on, so a shot is only paired as an After on purpose (cr:fl-ghost-autopair). */
    val ghostOn: Boolean = false,
)

sealed interface CaptureEvent {
    data class SelectRoom(val id: String?) : CaptureEvent
    data class SelectPhase(val phase: String?) : CaptureEvent
    data object CycleFlash : CaptureEvent
    data object Flip : CaptureEvent
    data class PermissionResult(val granted: Boolean) : CaptureEvent

    /** The screen came back to the front, maybe from Settings: re-read the camera permission (AC-F29). */
    data object Resumed : CaptureEvent
    data class CameraInfo(val hasBack: Boolean, val hasFront: Boolean) : CaptureEvent

    /** Shutter button or volume key. The route takes the picture and answers with [Captured] or [CaptureFailed]. */
    data object Shutter : CaptureEvent
    data class Captured(val path: String) : CaptureEvent
    data object CaptureFailed : CaptureEvent
    data class Imported(val paths: List<String>) : CaptureEvent
    data object ToggleGhost : CaptureEvent
    data object OpenNote : CaptureEvent
    data object CloseNote : CaptureEvent
    data class NoteText(val text: String) : CaptureEvent
    data object SaveNote : CaptureEvent

    /** Voice button: starts a memo, or stops and saves the one running. The route asks for the microphone first. */
    data object VoiceTap : CaptureEvent
    data object MicDenied : CaptureEvent
    data object OpenSettings : CaptureEvent
    data object DismissMessage : CaptureEvent
}
