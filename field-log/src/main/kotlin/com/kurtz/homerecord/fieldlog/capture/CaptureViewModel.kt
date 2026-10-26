package com.kurtz.homerecord.fieldlog.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.JobDao
import com.kurtz.homerecord.core.data.RoomDao
import com.kurtz.homerecord.core.location.LocationProvider
import com.kurtz.homerecord.core.photo.CaptureTags
import com.kurtz.homerecord.core.photo.JobPhotos
import com.kurtz.homerecord.core.photo.LensChoice
import com.kurtz.homerecord.core.photo.LensLimiter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class CaptureDeps(
    val jobId: String,
    val jobs: JobDao,
    val rooms: RoomDao,
    val photos: JobPhotos,
    val location: LocationProvider,
    /** Room and phase last used on this job, so the chips stay put between shots (AC-F24). */
    val lastTags: () -> Pair<String?, String?>,
    val rememberTags: (roomId: String?, phase: String?) -> Unit,
    val hasCameraPermission: () -> Boolean,
    val fixTimeoutMs: Long = 3_000,
)

/** FL-Capture: tags, flash, lens and saving every shot with job, room, phase, time and fix (AC-F24, AC-F29). */
class CaptureViewModel(private val d: CaptureDeps, private val saved: SavedStateHandle) : ViewModel() {
    private var hasBack = true
    private var hasFront = false
    private var wantFront = false

    private val _state = MutableStateFlow(
        CaptureUiState(
            roomId = saved["room"] ?: d.lastTags().first,
            phase = saved["phase"] ?: d.lastTags().second,
            access = if (d.hasCameraPermission()) CameraAccess.Granted else CameraAccess.Unknown,
        ),
    )
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val job = d.jobs.get(d.jobId)
            if (job == null) {
                _state.update { it.copy(loading = false, message = CaptureMessage.JobGone) }
                return@launch
            }
            val rooms = job.homeId?.let { home -> d.rooms.listForHome(home) }.orEmpty().map { CaptureRoom(it.id, it.name) }
            val phases = runCatching { Json.decodeFromString(ListSerializer(String.serializer()), job.phasesJson) }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    loading = false, jobName = job.name, rooms = rooms, phases = phases,
                    roomId = it.roomId?.takeIf { id -> rooms.any { r -> r.id == id } },
                    phase = it.phase?.takeIf { p -> p in phases },
                )
            }
        }
    }

    fun onEvent(event: CaptureEvent) {
        when (event) {
            is CaptureEvent.SelectRoom -> { saved["room"] = event.id; _state.update { it.copy(roomId = event.id) }; remember() }
            is CaptureEvent.SelectPhase -> { saved["phase"] = event.phase; _state.update { it.copy(phase = event.phase) }; remember() }
            CaptureEvent.CycleFlash -> _state.update { it.copy(flash = FlashMode.entries[(it.flash.ordinal + 1) % FlashMode.entries.size]) }
            CaptureEvent.Flip -> { wantFront = !wantFront; applyLens() }
            is CaptureEvent.PermissionResult ->
                _state.update { it.copy(access = if (event.granted) CameraAccess.Granted else CameraAccess.Denied) }
            is CaptureEvent.CameraInfo -> { hasBack = event.hasBack; hasFront = event.hasFront; applyLens() }
            is CaptureEvent.Captured -> save(listOf(event.path))
            is CaptureEvent.Imported -> save(event.paths)
            CaptureEvent.CaptureFailed -> _state.update { it.copy(message = CaptureMessage.SaveFailed) }
            CaptureEvent.DismissMessage -> _state.update { it.copy(message = null) }
            CaptureEvent.Shutter, CaptureEvent.OpenSettings -> Unit
        }
    }

    private fun remember() = _state.value.let { d.rememberTags(it.roomId, it.phase) }

    private fun applyLens() {
        val lens = LensLimiter.choose(hasBack, hasFront, wantFront)
        _state.update {
            it.copy(
                lens = lens, canFlip = LensLimiter.canFlip(hasBack, hasFront),
                message = if (lens == LensChoice.None) CaptureMessage.NoCamera else it.message?.takeIf { m -> m != CaptureMessage.NoCamera },
            )
        }
    }

    private fun save(paths: List<String>) {
        val tags = _state.value.let { CaptureTags(d.jobId, it.roomId, it.phase) }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val fix = if (d.location.hasPermission()) d.location.currentFix(d.fixTimeoutMs) else null
            var ok = 0
            var gone = false
            paths.forEach { path ->
                if (d.photos.save(path, tags, fix) == null) gone = true else ok++
            }
            _state.update {
                it.copy(
                    saving = false, savedCount = it.savedCount + ok,
                    message = if (gone) CaptureMessage.JobGone else null,
                )
            }
        }
    }
}
