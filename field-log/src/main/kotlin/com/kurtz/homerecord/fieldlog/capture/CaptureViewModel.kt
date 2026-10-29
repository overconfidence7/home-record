package com.kurtz.homerecord.fieldlog.capture

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.JobDao
import com.kurtz.homerecord.core.data.RoomDao
import com.kurtz.homerecord.core.location.LocationProvider
import com.kurtz.homerecord.core.photo.CaptureTags
import com.kurtz.homerecord.core.photo.GhostPicker
import com.kurtz.homerecord.core.photo.JobNotes
import com.kurtz.homerecord.core.photo.JobPhotos
import com.kurtz.homerecord.core.photo.LensChoice
import com.kurtz.homerecord.core.photo.LensLimiter
import com.kurtz.homerecord.core.photo.VoiceRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collect
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
    val notes: JobNotes,
    val recorder: VoiceRecorder,
    /** Where the next voice memo is written. */
    val newMemoPath: () -> String,
    /**
     * Outlives this screen: photo rows, memos and notes are written here so leaving Capture right after a shot,
     * or while recording, never loses it (AC-F24, AC-F27).
     */
    val appScope: CoroutineScope,
    val fixTimeoutMs: Long = 3_000,
    /** Whether a path's bytes are on this phone; the Before to line up with may have been taken on another phone. */
    val isOnPhone: suspend (String) -> Boolean = { true },
    /** Fetches that Before by its R2 key ([com.kurtz.homerecord.core.media.MediaDownloads.photo]). */
    val downloadPhoto: suspend (String) -> com.kurtz.homerecord.core.photo.OnPhone = { com.kurtz.homerecord.core.photo.OnPhone.Ready },
)

/** FL-Capture: tags, flash, lens and saving every shot with job, room, phase, time and fix (AC-F24, AC-F29). */
class CaptureViewModel(private val d: CaptureDeps, private val saved: SavedStateHandle) : ViewModel() {
    private var hasBack = true
    private var hasFront = false
    private var wantFront = false
    private var ticker: Job? = null
    private var memoPath: String? = null

    private val _state = MutableStateFlow(
        CaptureUiState(
            roomId = saved["room"] ?: d.lastTags().first,
            phase = saved["phase"] ?: d.lastTags().second,
            access = if (d.hasCameraPermission()) CameraAccess.Granted else CameraAccess.Unknown,
        ),
    )
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var jobPhotos = emptyList<com.kurtz.homerecord.core.data.PhotoEntity>()

    init {
        viewModelScope.launch {
            d.photos.observeForJob(d.jobId).collect { list -> jobPhotos = list; refreshGhost() }
        }
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

    private fun refreshGhost() {
        val pick = GhostPicker.pick(jobPhotos, _state.value.roomId)
        _state.update {
            val ghost = pick?.let { p -> GhostShot(p.id, p.path) }
            // A different Before (or none) starts with the overlay off again: the person chooses each pairing.
            it.copy(ghost = ghost, ghostOn = it.ghostOn && ghost?.photoId == it.ghost?.photoId)
        }
        if (pick != null) viewModelScope.launch { fetchGhost(pick.id, pick.path) }
    }

    /** Photo id whose download is running, so a burst of list changes fetches it once. */
    private var ghostFetch: String? = null

    /**
     * A Before pulled from another phone has no bytes here yet: the overlay waits with an empty path (the viewfinder
     * says it is downloading) and fills in when the download sets the row's path (cr:sync-local-columns).
     */
    private suspend fun fetchGhost(id: String, path: String) {
        if (d.isOnPhone(path)) return
        _state.update { s -> s.ghost?.takeIf { g -> g.photoId == id && g.path.isNotEmpty() }?.let { s.copy(ghost = it.copy(path = "")) } ?: s }
        if (ghostFetch == id) return
        ghostFetch = id
        if (d.downloadPhoto(id) != com.kurtz.homerecord.core.photo.OnPhone.Ready) ghostFetch = null
    }

    fun onEvent(event: CaptureEvent) {
        when (event) {
            is CaptureEvent.SelectRoom -> { saved["room"] = event.id; _state.update { it.copy(roomId = event.id) }; remember(); refreshGhost() }
            is CaptureEvent.SelectPhase -> { saved["phase"] = event.phase; _state.update { it.copy(phase = event.phase) }; remember() }
            CaptureEvent.CycleFlash -> _state.update { it.copy(flash = FlashMode.entries[(it.flash.ordinal + 1) % FlashMode.entries.size]) }
            CaptureEvent.Flip -> { wantFront = !wantFront; applyLens() }
            is CaptureEvent.PermissionResult ->
                _state.update { it.copy(access = if (event.granted) CameraAccess.Granted else CameraAccess.Denied) }
            CaptureEvent.Resumed -> _state.update {
                val now = when {
                    d.hasCameraPermission() -> CameraAccess.Granted
                    it.access == CameraAccess.Granted -> CameraAccess.Denied // revoked in Settings while away
                    else -> it.access // still asking, or still denied
                }
                it.copy(access = now)
            }
            is CaptureEvent.CameraInfo -> { hasBack = event.hasBack; hasFront = event.hasFront; applyLens() }
            is CaptureEvent.Captured -> save(listOf(event.path), pairWithGhost = true)
            CaptureEvent.ToggleGhost -> _state.update { it.copy(ghostOn = !it.ghostOn) }
            is CaptureEvent.Imported -> save(event.paths)
            CaptureEvent.CaptureFailed -> _state.update { it.copy(message = CaptureMessage.SaveFailed) }
            CaptureEvent.DismissMessage -> _state.update { it.copy(message = null) }
            CaptureEvent.OpenNote -> _state.update { it.copy(noteOpen = true) }
            CaptureEvent.CloseNote -> _state.update { it.copy(noteOpen = false, noteText = "") }
            is CaptureEvent.NoteText -> _state.update { it.copy(noteText = event.text) }
            CaptureEvent.SaveNote -> saveNote()
            CaptureEvent.VoiceTap -> if (_state.value.recording) stopMemo() else startMemo()
            CaptureEvent.MicDenied -> _state.update { it.copy(message = CaptureMessage.MicDenied) }
            CaptureEvent.Shutter, CaptureEvent.OpenSettings -> Unit
        }
    }

    private fun saveNote() {
        val text = _state.value.noteText
        d.appScope.launch {
            val id = d.notes.addText(d.jobId, text)
            _state.update {
                if (id == null && text.isNotBlank()) it.copy(noteOpen = false, noteText = "", message = CaptureMessage.JobGone)
                else if (id == null) it
                else it.copy(noteOpen = false, noteText = "", message = CaptureMessage.NoteSaved)
            }
        }
    }

    private fun startMemo() {
        val path = d.newMemoPath()
        if (!d.recorder.start(path)) {
            _state.update { it.copy(message = CaptureMessage.MemoFailed) }
            return
        }
        memoPath = path
        _state.update { it.copy(recording = true, recordedSeconds = 0, message = null) }
        ticker = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val seconds = _state.value.recordedSeconds + 1
                _state.update { it.copy(recordedSeconds = seconds) }
                if (seconds * 1_000L >= JobNotes.MAX_VOICE_MS) {
                    stopMemo()
                    return@launch
                }
            }
        }
    }

    private fun stopMemo() {
        val path = memoPath ?: return
        memoPath = null
        ticker?.cancel()
        val length = d.recorder.stop()
        _state.update { it.copy(recording = false, recordedSeconds = 0) }
        if (length == null) {
            _state.update { it.copy(message = CaptureMessage.MemoFailed) }
            return
        }
        d.appScope.launch {
            val id = d.notes.addVoice(d.jobId, path)
            _state.update { it.copy(message = if (id == null) CaptureMessage.JobGone else CaptureMessage.MemoSaved) }
        }
    }

    /** Leaving while recording keeps what was said so far (AC-F27). */
    override fun onCleared() {
        if (_state.value.recording) stopMemo()
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

    private fun save(paths: List<String>, pairWithGhost: Boolean = false) {
        val beforeOf = _state.value.takeIf { pairWithGhost && it.ghostOn }?.ghost?.photoId
        val tags = _state.value.let { CaptureTags(d.jobId, it.roomId, it.phase) }
        // One After per Before: the overlay goes off with the shot that pairs it, so a quick second shot is a plain photo.
        _state.update { it.copy(saving = true, ghostOn = it.ghostOn && beforeOf == null) }
        // The rows go in first with no fix and the fix is added after, so Back right after the shutter keeps the photo.
        d.appScope.launch {
            val ids = paths.mapNotNull { path -> d.photos.save(path, tags, fix = null, beforeOf = beforeOf) }
            _state.update {
                it.copy(
                    saving = false, savedCount = it.savedCount + ids.size,
                    message = if (ids.size < paths.size) CaptureMessage.JobGone else null,
                )
            }
            if (ids.isEmpty() || !d.location.hasPermission()) return@launch
            d.location.currentFix(d.fixTimeoutMs)?.let { d.photos.addFix(ids, it) }
        }
    }
}
