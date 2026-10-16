package com.kurtz.homerecord.fieldlog.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.repo.LogEntryRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaptureUiState(
    val busy: Boolean = false,
    val photosThisSession: Int = 0,
    val failed: Boolean = false,
    val noteOpen: Boolean = false,
    val noteText: String = "",
    val notesThisSession: Int = 0,
    val recording: Boolean = false,
    val elapsedMs: Long = 0L,
    val voiceThisSession: Int = 0,
    val voiceFailed: Boolean = false,
) {
    val canSaveNote: Boolean get() = noteText.isNotBlank()
}

sealed interface CaptureEvent {
    data object Shutter : CaptureEvent
    data object OpenNote : CaptureEvent
    data object DismissNote : CaptureEvent
    data class NoteChanged(val text: String) : CaptureEvent
    data object SaveNote : CaptureEvent
    data object ToggleVoice : CaptureEvent
}

class CaptureViewModel(
    private val jobId: String,
    private val entries: LogEntryRepository,
    private val media: MediaFileStore,
    private val capturer: PhotoCapturer,
    private val recorder: VoiceRecorder = NoVoiceRecorder,
) : ViewModel() {
    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

    private var voicePath: String? = null
    private var ticker: Job? = null

    fun onEvent(event: CaptureEvent) {
        when (event) {
            CaptureEvent.Shutter -> onShutter()
            CaptureEvent.OpenNote -> _state.update { it.copy(noteOpen = true) }
            CaptureEvent.DismissNote -> _state.update { it.copy(noteOpen = false, noteText = "") }
            is CaptureEvent.NoteChanged -> _state.update { it.copy(noteText = event.text) }
            CaptureEvent.SaveNote -> saveNote()
            CaptureEvent.ToggleVoice -> if (_state.value.recording) stopVoice() else startVoice()
        }
    }

    /** AC-56: take a photo and record it as a PHOTO entry; ignored while one is in flight. */
    fun onShutter() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, failed = false) }
        viewModelScope.launch {
            val path = media.newPhotoPath()
            val file = media.resolveForWrite(path)
            val saved = file != null && try {
                capturer.capture(file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false
            }
            if (saved) {
                entries.addPhoto(jobId, path)
            } else {
                media.delete(path)
            }
            _state.update {
                it.copy(busy = false, failed = !saved, photosThisSession = it.photosThisSession + if (saved) 1 else 0)
            }
        }
    }

    /** AC-58: empty notes are not saved. */
    private fun saveNote() {
        val text = _state.value.noteText
        if (text.isBlank()) return
        viewModelScope.launch {
            entries.addNote(jobId, text)
            _state.update { it.copy(noteOpen = false, noteText = "", notesThisSession = it.notesThisSession + 1) }
        }
    }

    private fun startVoice() {
        val path = media.newAudioPath()
        val file = media.resolveForWrite(path)
        if (file == null || !recorder.start(file)) {
            media.delete(path)
            _state.update { it.copy(voiceFailed = true) }
            return
        }
        voicePath = path
        _state.update { it.copy(recording = true, elapsedMs = 0L, voiceFailed = false) }
        ticker = viewModelScope.launch {
            while (!VoiceCap.reached(_state.value.elapsedMs)) {
                delay(VoiceCap.TICK_MS)
                _state.update { it.copy(elapsedMs = VoiceCap.clamp(it.elapsedMs + VoiceCap.TICK_MS)) }
            }
            finishVoice()
        }
    }

    private fun stopVoice() {
        ticker?.cancel()
        viewModelScope.launch { finishVoice() }
    }

    /** AC-59: stop the recorder and create the VOICE entry with its duration. */
    private suspend fun finishVoice() {
        val path = voicePath ?: return
        voicePath = null
        val duration = VoiceCap.clamp(_state.value.elapsedMs)
        val ok = recorder.stop()
        if (ok && duration > 0L) {
            entries.addVoice(jobId, path, duration)
        } else {
            media.delete(path)
        }
        _state.update {
            it.copy(
                recording = false,
                elapsedMs = 0L,
                voiceFailed = !ok,
                voiceThisSession = it.voiceThisSession + if (ok && duration > 0L) 1 else 0,
            )
        }
    }

    /** Leaving the screen keeps what was recorded so far. */
    fun onLeave() {
        if (_state.value.recording) stopVoice()
    }
}

/** Used where no microphone is wired (previews, photo-only tests). */
private object NoVoiceRecorder : VoiceRecorder {
    override fun start(file: java.io.File): Boolean = false
    override fun stop(): Boolean = false
}
