package com.kurtz.homerecord.housemanual.shutoffs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.ShutoffRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Add (blank [shutoffId]) or edit one shutoff. Photo files taken but never saved are deleted. */
class ShutoffEditViewModel(
    private val shutoffId: String?,
    private val homes: HomeRepository,
    private val shutoffs: ShutoffRepository,
    private val media: MediaFileStore,
) : ViewModel() {
    private val _state = MutableStateFlow(ShutoffEditUiState())
    val state: StateFlow<ShutoffEditUiState> = _state

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    private var existing: Shutoff? = null
    private var homeId: String? = null

    init {
        viewModelScope.launch {
            val home = homes.getHome()
            homeId = home?.id
            val rooms = home?.let { homes.getRooms(it.id) }.orEmpty().sortedBy { it.name.lowercase() }
            val found = shutoffId?.let { shutoffs.get(it) }
            existing = found
            _state.value = ShutoffEditUiState(
                loaded = true,
                isNew = found == null,
                kind = found?.kind,
                roomId = found?.roomId,
                photoPath = found?.photoPath,
                instructions = found?.instructions.orEmpty(),
                rooms = rooms,
            )
        }
    }

    fun onEvent(event: ShutoffEditEvent) {
        when (event) {
            is ShutoffEditEvent.KindChanged -> _state.update { it.copy(kind = event.kind) }
            is ShutoffEditEvent.RoomChanged -> _state.update { it.copy(roomId = event.roomId) }
            is ShutoffEditEvent.InstructionsChanged -> _state.update { it.copy(instructions = event.value) }
            ShutoffEditEvent.RemovePhoto -> replacePhoto(null)
            ShutoffEditEvent.TakePhoto -> Unit // handled by the route (camera)
            ShutoffEditEvent.Save -> save()
            ShutoffEditEvent.AskDelete -> _state.update { it.copy(confirmingDelete = true) }
            ShutoffEditEvent.CancelDelete -> _state.update { it.copy(confirmingDelete = false) }
            ShutoffEditEvent.ConfirmDelete -> delete()
        }
    }

    fun onPhotoCaptured(path: String) = replacePhoto(path)

    /** Drops an unsaved photo file when the user leaves without saving. */
    override fun onCleared() {
        val current = _state.value.photoPath
        if (current != null && current != existing?.photoPath && !saved) media.delete(current)
    }

    private var saved = false

    private fun replacePhoto(path: String?) {
        val old = _state.value.photoPath
        // Only an unsaved photo may be deleted here; the saved one is removed by the repository on save.
        if (old != null && old != existing?.photoPath) media.delete(old)
        _state.update { it.copy(photoPath = path) }
    }

    private fun save() {
        val s = _state.value
        val kind = s.kind
        val home = homeId
        if (!s.canSave || kind == null || home == null) return
        viewModelScope.launch {
            val base = existing
            val shutoff = base?.copy(kind = kind, roomId = s.roomId, photoPath = s.photoPath, instructions = s.instructions.trim())
                ?: Shutoff(
                    id = "", homeId = home, kind = kind, roomId = s.roomId, photoPath = s.photoPath,
                    instructions = s.instructions.trim(), createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL,
                )
            shutoffs.save(shutoff)
            saved = true
            _done.send(Unit)
        }
    }

    private fun delete() {
        val id = existing?.id ?: return
        viewModelScope.launch {
            val unsaved = _state.value.photoPath?.takeIf { it != existing?.photoPath }
            media.delete(unsaved)
            shutoffs.delete(id)
            saved = true
            _done.send(Unit)
        }
    }
}
