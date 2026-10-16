package com.kurtz.homerecord.fieldlog.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.EntryKind
import com.kurtz.homerecord.core.model.LogEntry
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.repo.JobRepository
import com.kurtz.homerecord.core.repo.LogEntryRepository
import com.kurtz.homerecord.fieldlog.playback.AudioPlayer
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EntryUiState(
    val loaded: Boolean = false,
    val entry: LogEntry? = null,
    val text: String = "",
    val roomId: String? = null,
    val rooms: List<Room> = emptyList(),
    val playing: Boolean = false,
    val finished: Boolean = false,
) {
    /** A note cannot be saved blank; photo/voice captions may be empty. */
    val canSave: Boolean get() = entry != null && (entry.kind != EntryKind.NOTE || text.isNotBlank())
}

sealed interface EntryEvent {
    data class TextChanged(val text: String) : EntryEvent
    data class RoomChosen(val roomId: String?) : EntryEvent
    data object Save : EntryEvent
    data object Delete : EntryEvent
    data object TogglePlay : EntryEvent
}

class EntryViewModel(
    private val entries: LogEntryRepository,
    private val jobs: JobRepository,
    private val media: MediaFileStore,
    private val player: AudioPlayer,
    private val entryId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(EntryUiState())
    val state: StateFlow<EntryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val entry = entries.get(entryId)
            if (entry == null) {
                _state.value = EntryUiState(loaded = true)
                return@launch
            }
            val rooms = jobs.getJob(entry.jobId)?.let { jobs.getRooms(it) }.orEmpty()
            _state.value = EntryUiState(true, entry, entry.text.orEmpty(), entry.roomId, rooms)
        }
    }

    fun file(): File? = _state.value.entry?.mediaPath?.let { media.resolve(it) }

    fun onEvent(event: EntryEvent) {
        when (event) {
            is EntryEvent.TextChanged -> _state.update { it.copy(text = event.text) }
            is EntryEvent.RoomChosen -> _state.update { it.copy(roomId = event.roomId) }
            EntryEvent.Save -> save()
            EntryEvent.Delete -> delete()
            EntryEvent.TogglePlay -> togglePlay()
        }
    }

    private fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            entries.update(entryId, s.text, s.roomId)
            _state.update { it.copy(finished = true) }
        }
    }

    private fun delete() {
        viewModelScope.launch {
            player.stop()
            entries.delete(entryId)?.let { media.delete(it.mediaPath) }
            _state.update { it.copy(finished = true) }
        }
    }

    private fun togglePlay() {
        if (_state.value.playing) {
            player.stop()
            _state.update { it.copy(playing = false) }
            return
        }
        val f = file()?.takeIf { it.isFile } ?: return
        val started = player.play(f) { _state.update { it.copy(playing = false) } }
        _state.update { it.copy(playing = started) }
    }

    override fun onCleared() = player.stop()
}
