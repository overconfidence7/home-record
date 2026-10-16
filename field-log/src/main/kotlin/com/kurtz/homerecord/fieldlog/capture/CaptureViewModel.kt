package com.kurtz.homerecord.fieldlog.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.repo.LogEntryRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CaptureUiState(
    val busy: Boolean = false,
    val photosThisSession: Int = 0,
    val failed: Boolean = false,
)

class CaptureViewModel(
    private val jobId: String,
    private val entries: LogEntryRepository,
    private val media: MediaFileStore,
    private val capturer: PhotoCapturer,
) : ViewModel() {
    private val _state = MutableStateFlow(CaptureUiState())
    val state: StateFlow<CaptureUiState> = _state.asStateFlow()

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
}
