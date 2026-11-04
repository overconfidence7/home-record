package com.kurtz.homerecord.fieldlog.materials

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.material.JobMaterials
import com.kurtz.homerecord.core.material.Material
import com.kurtz.homerecord.core.material.MaterialCategory
import com.kurtz.homerecord.core.material.MaterialDraft
import com.kurtz.homerecord.core.material.MaterialSaveResult
import com.kurtz.homerecord.core.material.MaterialsReport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Writes the CSV to a shareable file and opens the share sheet; false when that failed. */
fun interface MaterialsExporter {
    suspend fun export(fileName: String, csv: String): Boolean
}

class MaterialsDeps(
    val materials: Flow<List<Material>>,
    /** The company's live jobs, id to name. */
    val jobs: Flow<List<JobOption>>,
    val exporter: MaterialsExporter,
    val categoryLabel: (MaterialCategory) -> String,
    val today: () -> LocalDate = LocalDate::now,
)

/** FL-Materials (AC-F44): live from Room, grouped by job with totals; Export shares the CSV. */
class MaterialsViewModel(private val d: MaterialsDeps) : ViewModel() {
    private val _state = MutableStateFlow(MaterialsUiState())
    val state: StateFlow<MaterialsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(d.materials, d.jobs) { materials, jobs -> MaterialsReport.group(materials, jobs.associate { it.id to it.name }) }
                .collect { groups -> _state.update { it.copy(loading = false, groups = groups) } }
        }
    }

    fun export() {
        val groups = _state.value.groups
        if (groups.isEmpty()) return
        viewModelScope.launch {
            val ok = d.exporter.export(MaterialsReport.fileName(d.today()), MaterialsReport.csv(groups, d.categoryLabel))
            _state.update { it.copy(exportFailed = !ok) }
        }
    }

    fun dismissExportFailed() = _state.update { it.copy(exportFailed = false) }
}

class MaterialFormDeps(
    /** Null for a new receipt. */
    val materialId: String?,
    val materials: JobMaterials,
    val jobs: Flow<List<JobOption>>,
    val today: () -> LocalDate = LocalDate::now,
)

/** Add or edit one receipt (AC-F44); the typing survives process death. */
class MaterialFormViewModel(private val d: MaterialFormDeps, private val saved: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow(MaterialFormUiState(isNew = d.materialId == null))
    val state: StateFlow<MaterialFormUiState> = _state.asStateFlow()

    init {
        load()
    }

    private fun load() {
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val jobs = d.jobs.first()
            val existing = d.materialId?.let { d.materials.get(it) }
            if (d.materialId != null && existing == null) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            val initial = existing?.let {
                MaterialFormDraft(it.jobId, it.vendor.orEmpty(), com.kurtz.homerecord.core.units.Money.format(it.amountCents), it.category, it.date, it.note.orEmpty())
            } ?: MaterialFormDraft(jobId = jobs.singleOrNull()?.id, date = d.today().toString())
            val draft = saved.get<String>(DRAFT)?.let { runCatching { json.decodeFromString(MaterialFormDraft.serializer(), it) }.getOrNull() } ?: initial
            _state.update { it.copy(loading = false, jobs = jobs, initial = initial, draft = draft) }
        }
    }

    fun onEvent(e: MaterialFormEvent) {
        when (e) {
            MaterialFormEvent.PickJob -> _state.update { it.copy(pickingJob = true) }
            MaterialFormEvent.DismissJob -> _state.update { it.copy(pickingJob = false) }
            is MaterialFormEvent.Job -> { _state.update { it.copy(pickingJob = false) }; edit { it.copy(jobId = e.id) } }
            is MaterialFormEvent.Vendor -> edit { it.copy(vendor = e.text) }
            is MaterialFormEvent.Amount -> edit { it.copy(amount = e.text) }
            is MaterialFormEvent.Category -> edit { it.copy(category = e.category) }
            is MaterialFormEvent.Date -> edit { it.copy(date = e.iso) }
            is MaterialFormEvent.Note -> edit { it.copy(note = e.text) }
            MaterialFormEvent.Save -> save()
            MaterialFormEvent.AskDelete -> if (!_state.value.isNew) _state.update { it.copy(confirmDelete = true) }
            MaterialFormEvent.DismissDelete -> _state.update { it.copy(confirmDelete = false) }
            MaterialFormEvent.Delete -> delete()
            MaterialFormEvent.Retry -> load()
        }
    }

    private fun edit(change: (MaterialFormDraft) -> MaterialFormDraft) {
        if (_state.value.loading || _state.value.failed) return
        val next = change(_state.value.draft)
        saved[DRAFT] = json.encodeToString(MaterialFormDraft.serializer(), next)
        _state.update { it.copy(draft = next, saveFailed = false) }
    }

    private fun save() {
        val s = _state.value
        if (s.loading || s.failed) return
        if (s.jobMissing || s.amountMissing) {
            _state.update { it.copy(showErrors = true) }
            return
        }
        val dr = s.draft
        viewModelScope.launch {
            val result = d.materials.save(MaterialDraft(dr.jobId, dr.vendor, s.amountCents, dr.category, dr.date.ifBlank { d.today().toString() }, dr.note), d.materialId)
            _state.update { if (result is MaterialSaveResult.Saved) it.copy(done = true) else it.copy(saveFailed = true, showErrors = true) }
        }
    }

    private fun delete() {
        val id = d.materialId ?: return
        viewModelScope.launch {
            d.materials.delete(id)
            _state.update { it.copy(confirmDelete = false, done = true) }
        }
    }

    private companion object {
        const val DRAFT = "material_draft"
    }
}
