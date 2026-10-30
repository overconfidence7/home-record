package com.kurtz.homerecord.fieldlog.photos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.JobDao
import com.kurtz.homerecord.core.data.PhotoDao
import com.kurtz.homerecord.core.data.RoomDao
import com.kurtz.homerecord.core.photo.AnnotationCodec
import com.kurtz.homerecord.core.photo.OnPhone
import com.kurtz.homerecord.core.photo.PhotoEdits
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class PhotoDetailDeps(
    val photoId: String,
    val photos: PhotoDao,
    val jobs: JobDao,
    val rooms: RoomDao,
    val edits: PhotoEdits,
    /** Who may do what; delete needs [RoleGates.canDeletePhoto] for the photo's job (AC-F3). */
    val gates: kotlinx.coroutines.flow.Flow<com.kurtz.homerecord.core.company.RoleGates>,
    /** Whether a path's bytes are on this phone; checked off Main by the caller's implementation (S7). */
    val isOnPhone: suspend (String) -> Boolean = { true },
    /** Fetches a photo pulled from another phone by its R2 key ([com.kurtz.homerecord.core.media.MediaDownloads.photo]). */
    val download: suspend (String) -> OnPhone = { OnPhone.Ready },
)

/** FL-Photo: caption, room and phase tags, the Before mark (its own tag, cr:fl-before-phase) and delete for one job photo (AC-F25). */
class PhotoDetailViewModel(private val d: PhotoDetailDeps) : ViewModel() {
    private val _state = MutableStateFlow(PhotoDetailUiState())
    val state: StateFlow<PhotoDetailUiState> = _state.asStateFlow()

    /** Edits land in the order they were made, so a quick room then phase tap cannot be overwritten by the older write. */
    private val writes = Mutex()

    @Volatile private var jobId: String? = null
    @Volatile private var gates: com.kurtz.homerecord.core.company.RoleGates? = null

    init {
        viewModelScope.launch { d.gates.collect { g -> gates = g; _state.update { it.copy(canDelete = g.canDeletePhoto(jobId)) } } }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val p = d.photos.get(d.photoId)
        if (p == null) {
            _state.update { it.copy(loading = false, missing = true) }
            return
        }
        jobId = p.jobId
        val job = p.jobId?.let { d.jobs.get(it) }
        val rooms = (job?.homeId ?: p.homeId).let { d.rooms.listForHome(it) }.map { PhotoRoomOption(it.id, it.name) }
        val onPhone = d.isOnPhone(p.path)
        val phases = job?.let { runCatching { Json.decodeFromString(ListSerializer(String.serializer()), it.phasesJson) }.getOrNull() }.orEmpty()
        _state.update {
            PhotoDetailUiState(
                loading = false, imagePath = if (onPhone) p.path else "", image = if (onPhone) PhotoImage.Ready else PhotoImage.Downloading, caption = p.caption.orEmpty(), rooms = rooms, roomId = p.roomId,
                phases = phases, phase = p.stage, takenAt = p.takenAt, annotated = AnnotationCodec.decode(p.annotationsJson).isNotEmpty(),
                isBefore = p.pairRole == PhotoEdits.BEFORE, canDelete = gates?.canDeletePhoto(p.jobId) ?: false,
            )
        }
        if (!onPhone) download()
    }

    private suspend fun download() {
        _state.update { it.copy(image = PhotoImage.Downloading) }
        val result = d.download(d.photoId)
        val path = if (result == OnPhone.Ready) d.photos.get(d.photoId)?.path.orEmpty() else ""
        _state.update {
            it.copy(
                imagePath = path,
                image = when (result) {
                    OnPhone.Ready -> PhotoImage.Ready
                    OnPhone.NoNetwork -> PhotoImage.NoNetwork
                    OnPhone.NotUploaded -> PhotoImage.NotUploaded
                    OnPhone.Failed -> PhotoImage.Failed
                },
            )
        }
    }

    fun onEvent(event: PhotoDetailEvent) {
        when (event) {
            is PhotoDetailEvent.Caption -> _state.update { it.copy(caption = event.text) }
            PhotoDetailEvent.SaveCaption -> viewModelScope.launch { val text = _state.value.caption; writes.withLock { d.edits.setCaption(d.photoId, text) } }
            is PhotoDetailEvent.Room -> {
                _state.update { it.copy(roomId = event.id) }
                saveTags()
            }
            is PhotoDetailEvent.Phase -> {
                _state.update { it.copy(phase = event.phase) }
                saveTags()
            }
            PhotoDetailEvent.ToggleBefore -> {
                val before = !_state.value.isBefore
                _state.update { it.copy(isBefore = before) }
                viewModelScope.launch { writes.withLock { d.edits.setBefore(d.photoId, before) } }
            }
            PhotoDetailEvent.AskDelete -> _state.update { it.copy(confirmDelete = it.canDelete) }
            PhotoDetailEvent.CancelDelete -> _state.update { it.copy(confirmDelete = false) }
            PhotoDetailEvent.RetryDownload -> if (_state.value.image != PhotoImage.Downloading) viewModelScope.launch { download() }
            PhotoDetailEvent.ConfirmDelete -> if (_state.value.canDelete) viewModelScope.launch {
                d.edits.delete(d.photoId)
                _state.update { it.copy(confirmDelete = false, deleted = true) }
            }
            PhotoDetailEvent.Share, PhotoDetailEvent.Annotate, PhotoDetailEvent.Back -> Unit
        }
    }

    private fun saveTags() {
        val s = _state.value
        viewModelScope.launch { writes.withLock { d.edits.setTags(d.photoId, s.roomId, s.phase) } }
    }
}
