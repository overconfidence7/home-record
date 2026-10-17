package com.kurtz.homerecord.housemanual.equipment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.billing.LimitGate
import com.kurtz.homerecord.core.billing.LimitResult
import com.kurtz.homerecord.core.billing.LimitedAction
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.ocr.DataPlateParser
import com.kurtz.homerecord.core.ocr.TextRecognizer
import com.kurtz.homerecord.core.repo.EquipmentRepository
import com.kurtz.homerecord.core.repo.HomeRepository
import com.kurtz.homerecord.core.repo.MaintenanceRepository
import kotlinx.coroutines.CancellationException
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
    private val recognizer: TextRecognizer,
    private val maintenance: MaintenanceRepository,
    private val limits: LimitGate,
    private val reminderTitle: (Equipment) -> String,
) : ViewModel() {
    private val _state = MutableStateFlow(EquipmentEditUiState())
    val state: StateFlow<EquipmentEditUiState> = _state

    private val _done = Channel<Boolean>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    /** Fires when a new item would exceed the free limit (AC-92); the route opens the paywall. */
    private val _paywall = Channel<Unit>(Channel.BUFFERED)
    val paywall = _paywall.receiveAsFlow()

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
            val reminder = found?.let { maintenance.getForEquipment(it.id) }
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
                remindMonths = reminder?.intervalMonths?.toString().orEmpty(),
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
            is EquipmentEditEvent.RemindMonthsChanged -> _state.update { it.copy(remindMonths = event.value.filter(Char::isDigit).take(2)) }
            is EquipmentEditEvent.NotesChanged -> _state.update { it.copy(notes = event.value) }
            EquipmentEditEvent.RemoveLabelPhoto -> replacePhoto(null)
            EquipmentEditEvent.TakeLabelPhoto, EquipmentEditEvent.ScanDataPlate -> Unit // handled by the route (camera)
            is EquipmentEditEvent.ScanMakeChanged -> updateScan { it.copy(make = event.value) }
            is EquipmentEditEvent.ScanModelChanged -> updateScan { it.copy(model = event.value) }
            is EquipmentEditEvent.ScanSerialChanged -> updateScan { it.copy(serial = event.value) }
            EquipmentEditEvent.ApplyScan -> applyScan()
            EquipmentEditEvent.DismissScan -> _state.update { it.copy(scan = ScanState.None) }
            EquipmentEditEvent.Save -> save()
            EquipmentEditEvent.AskDelete -> _state.update { it.copy(confirmingDelete = true) }
            EquipmentEditEvent.CancelDelete -> _state.update { it.copy(confirmingDelete = false) }
            EquipmentEditEvent.ConfirmDelete -> delete()
        }
    }

    fun onPhotoCaptured(path: String) = replacePhoto(path)

    /** A data-plate photo was taken: keep it as the label photo (AC-48) and read it on-device. */
    fun onDataPlateCaptured(path: String) {
        replacePhoto(path)
        _state.update { it.copy(scan = ScanState.Reading) }
        viewModelScope.launch {
            val lines = try {
                media.resolve(path)?.let { recognizer.recognize(it) }.orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList() // AC-47: a recognizer failure behaves like "no text found".
            }
            val fields = DataPlateParser.parse(lines)
            _state.update {
                it.copy(scan = ScanState.Result(lines, fields.make.orEmpty(), fields.model.orEmpty(), fields.serial.orEmpty()))
            }
        }
    }

    private fun updateScan(change: (ScanState.Result) -> ScanState.Result) =
        _state.update { s -> (s.scan as? ScanState.Result)?.let { s.copy(scan = change(it)) } ?: s }

    /** Fills only the fields the user confirmed (non-blank); blank proposals leave existing values alone. */
    private fun applyScan() = _state.update { s ->
        val r = s.scan as? ScanState.Result ?: return@update s
        s.copy(
            make = r.make.trim().ifEmpty { s.make },
            model = r.model.trim().ifEmpty { s.model },
            serial = r.serial.trim().ifEmpty { s.serial },
            scan = ScanState.None,
        )
    }

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
        val base0 = existing
        if (!s.canSave || category == null || home == null) return
        viewModelScope.launch {
            if (base0 == null && limits.check(home, LimitedAction.ADD_EQUIPMENT) == LimitResult.SHOW_PAYWALL) {
                _paywall.send(Unit)
                return@launch
            }
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
            val saved = equipment.save(record)
            val months = s.remindMonthsValue
            // AC-81: blank removes the reminder, 1-24 creates or updates it.
            maintenance.setEquipmentReminder(saved, months, reminderTitle(saved))
            finished = true
            _done.send(months != null)
        }
    }

    private fun delete() {
        val id = existing?.id ?: return
        viewModelScope.launch {
            media.delete(_state.value.labelPhotoPath?.takeIf { it != existing?.labelPhotoPath })
            equipment.delete(id)
            finished = true
            _done.send(false)
        }
    }
}
