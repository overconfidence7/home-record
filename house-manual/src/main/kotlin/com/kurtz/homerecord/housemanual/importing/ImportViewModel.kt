package com.kurtz.homerecord.housemanual.importing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.bundle.ConflictChoice
import com.kurtz.homerecord.core.bundle.HomeBundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * HM-Import (AC-H17): reads the file, shows what is new per kind and the conflicts (Keep mine by default, AC-C47),
 * imports on Import. The Use imported picks survive process death; the file is read again.
 */
class ImportViewModel(
    private val read: suspend () -> ImportRead,
    private val apply: suspend (ReadImport, Map<Pair<String, String>, ConflictChoice>) -> Boolean,
    private val saved: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()
    private var current: ReadImport? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        when (val r = read()) {
            is ImportRead.Failed -> _state.value = ImportUiState(loading = false, error = r.error)
            is ImportRead.Ok -> {
                current = r.read
                val picked = saved.get<ArrayList<String>>(KEY_PICKED).orEmpty().toSet()
                val plan = r.read.preview.plan
                val counts = COUNTED.map { ImportCount(it, r.read.preview.incoming(it.type)) }.filter { it.count > 0 }
                val conflicts = plan.conflicts.map {
                    ImportConflict(it.type, it.id, ImportKind.of(it.type), nameOf(it.imported.fields, it.local?.fields), "${it.type}/${it.id}" in picked)
                }
                _state.value = ImportUiState(loading = false, homeName = r.read.homeName, counts = counts, conflicts = conflicts)
            }
        }
    }

    fun onEvent(event: ImportEvent) {
        when (event) {
            is ImportEvent.Choose -> {
                _state.update { s ->
                    s.copy(conflicts = s.conflicts.map { if (it.type == event.type && it.id == event.id) it.copy(useImported = event.useImported) else it })
                }
                saved[KEY_PICKED] = ArrayList(_state.value.conflicts.filter { it.useImported }.map { "${it.type}/${it.id}" })
            }
            ImportEvent.Import -> importNow()
            ImportEvent.Back, ImportEvent.Finish -> Unit
        }
    }

    private fun importNow() {
        val r = current ?: return
        val s = _state.value
        if (s.importing || s.done) return
        _state.update { it.copy(importing = true, error = null) }
        viewModelScope.launch {
            val choices = s.conflicts.associate {
                (it.type to it.id) to if (it.useImported) ConflictChoice.UseImported else ConflictChoice.KeepMine
            }
            val ok = apply(r, choices)
            _state.update { if (ok) it.copy(importing = false, done = true) else it.copy(importing = false, error = ImportError.Failed) }
        }
    }

    companion object {
        private const val KEY_PICKED = "import_use_imported"
        val COUNTED = listOf(
            ImportKind.Floors, ImportKind.Rooms, ImportKind.WallPhotos, ImportKind.Overlays, ImportKind.Items,
            ImportKind.Documents, ImportKind.ChangeOrders,
        )

        private fun nameOf(vararg fields: Map<String, kotlinx.serialization.json.JsonElement>?): String =
            fields.firstNotNullOfOrNull { f ->
                listOf("name", "title", "label").firstNotNullOfOrNull { k -> (f?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(String::isNotBlank) }
            }.orEmpty()

        /** The sender's home: its name or address from the bundle's home record, read before the bundle is aimed at this phone's home. */
        fun homeName(bundle: HomeBundle): String {
            val home = bundle.records["home"]?.firstOrNull()?.fields ?: return ""
            return listOf("name", "address").firstNotNullOfOrNull { k ->
                (home[k] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf(String::isNotBlank)
            }.orEmpty()
        }

    }
}
