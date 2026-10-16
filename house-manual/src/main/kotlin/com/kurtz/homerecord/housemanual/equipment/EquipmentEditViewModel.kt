package com.kurtz.homerecord.housemanual.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.repo.EquipmentRepository
import com.kurtz.homerecord.core.repo.HomeRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Add (null [equipmentId]) or edit one piece of equipment. Label photos taken but never saved are deleted. */
class EquipmentEditViewModel(
    private val equipmentId: String?,
    private val homes: HomeRepository,
    private val equipment: EquipmentRepository,
    private val media: MediaFileStore,
) : ViewModel() {
    private val _state = MutableStateFlow(EquipmentEditUiState())
    val state: StateFlow<EquipmentEditUiState> = _state

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    private var existing: Equipment? = null
    private var homeId: String? = null
    private var finished = false

    init {
        viewModelScope.launch {
            val home = homes.getHome()
            homeId = home?.id
            val rooms = home?.let { homes.getRooms(it.id) }.orEmpty().sortedBy { it.name.lowercase() }
            val found = equipmentId?.let { equipment.get(it) }
            existing = found
            _state.value = EquipmentEditUiState(
                loaded = true,
                isNew = found == null,
                category = found?.category,
                roomId = found?.roomId,
                make = found?.make.orEmpty(),
                model = found?.model.orEmpty(),
                serial = found?.serial.orEmpty(),
                manufacturedOn = found?.manufacturedOn.orEmpty(),
                installedOn = found?.installedOn.orEmpty(),
                warrantyEnds = found?.warrantyEnds.orEmpty(),
                notes = found?.notes.orEmpty(),
                labelPhotoPath = found?.labelPhotoPath,
                rooms = rooms,
            )
        }
    }

    fun onEvent(event: EquipmentEditEvent) {
        when (event) {
            is EquipmentEditEvent.CategoryChanged -> _state.update { it.copy(category = event.category) }
            is EquipmentEditEvent.RoomChanged -> _state.update { it.copy(roomId = event.roomId) }
            is EquipmentEditEvent.MakeChanged -> _state.update { it.copy(make = event.value) }
            is EquipmentEditEvent.ModelChanged -> _state.update { it.copy(model = event.value) }
            is EquipmentEditEvent.SerialChanged -> _state.update { it.copy(serial = event.value) }
            is EquipmentEditEvent.ManufacturedOnChanged -> _state.update { it.copy(manufacturedOn = event.value) }
            is EquipmentEditEvent.InstalledOnChanged -> _state.update { it.copy(installedOn = event.value) }
            is EquipmentEditEvent.WarrantyEndsChanged -> _state.update { it.copy(warrantyEnds = event.value) }
            is EquipmentEditEvent.NotesChanged -> _state.update { it.copy(notes = event.value) }
            EquipmentEditEvent.RemoveLabelPhoto -> replacePhoto(null)
            EquipmentEditEvent.TakeLabelPhoto -> Unit // handled by the route (camera)
            EquipmentEditEvent.Save -> save()
            EquipmentEditEvent.AskDelete -> _state.update { it.copy(confirmingDelete = true) }
            EquipmentEditEvent.CancelDelete -> _state.update { it.copy(confirmingDelete = false) }
            EquipmentEditEvent.ConfirmDelete -> delete()
        }
    }

    fun onPhotoCaptured(path: String) = replacePhoto(path)

    /** Drops an unsaved photo file when the user leaves without saving. */
    override fun onCleared() {
        val current = _state.value.labelPhotoPath
        if (current != null && current != existing?.labelPhotoPath && !finished) media.delete(current)
    }

    private fun replacePhoto(path: String?) {
        val old = _state.value.labelPhotoPath
        // Only an unsaved photo may be deleted here; the saved one is removed by the repository on save.
        if (old != null && old != existing?.labelPhotoPath) media.delete(old)
        _state.update { it.copy(labelPhotoPath = path) }
    }

    private fun save() {
        val s = _state.value
        val category = s.category
        val home = homeId
        if (!s.canSave || category == null || home == null) return
        viewModelScope.launch {
            fun String.orNull() = trim().ifEmpty { null }
            val base = existing
            val record = (base ?: Equipment(
                id = "", homeId = home, category = category, createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL,
            )).copy(
                category = category,
                roomId = s.roomId,
                make = s.make.orNull(),
                model = s.model.orNull(),
                serial = s.serial.orNull(),
                manufacturedOn = s.manufacturedOn.orNull(),
                installedOn = s.installedOn.orNull(),
                warrantyEnds = s.warrantyEnds.orNull(),
                notes = s.notes.orNull(),
                labelPhotoPath = s.labelPhotoPath,
            )
            equipment.save(record)
            finished = true
            _done.send(Unit)
        }
    }

    private fun delete() {
        val id = existing?.id ?: return
        viewModelScope.launch {
            media.delete(_state.value.labelPhotoPath?.takeIf { it != existing?.labelPhotoPath })
            equipment.delete(id)
            finished = true
            _done.send(Unit)
        }
    }
}
