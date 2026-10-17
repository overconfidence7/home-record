package com.kurtz.homerecord.housemanual.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.bundle.BundleFormatException
import com.kurtz.homerecord.core.bundle.BundleReader
import com.kurtz.homerecord.core.imports.ConflictChoice
import com.kurtz.homerecord.core.imports.ImportApplier
import com.kurtz.homerecord.core.imports.ImportPlan
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Side-effect seam for opening the picked/shared file and getting scratch space (fake in tests). */
interface BundleSource {
    fun open(uri: String): InputStream?
    fun newStagingDir(): File
}

/** HM-Import (AC-75..79): read a `.homerecord`, preview counts and conflicts, then apply. */
class ImportViewModel(
    private val applier: ImportApplier,
    private val source: BundleSource,
    initialUri: String?,
    private val reader: BundleReader = BundleReader(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var plan: ImportPlan? = null
    private var staging: File? = null

    init {
        if (initialUri != null) load(initialUri)
    }

    fun onEvent(event: ImportEvent) {
        when (event) {
            is ImportEvent.FileChosen -> load(event.uri)
            is ImportEvent.Choose -> _state.update { it.copy(choices = it.choices + (event.id to event.choice)) }
            ImportEvent.Confirm -> apply()
            ImportEvent.Reset -> {
                cleanup()
                _state.value = ImportUiState()
            }
        }
    }

    private fun load(uri: String) {
        cleanup()
        _state.value = ImportUiState(phase = ImportPhase.READING)
        viewModelScope.launch {
            val next = try {
                val dir = source.newStagingDir().also { staging = it }
                val contents = withContext(io) {
                    val input = source.open(uri) ?: throw IOException("cannot open")
                    input.use { reader.read(it, dir) }
                }
                val planned = applier.plan(contents.record)
                if (planned == null) {
                    ImportUiState(phase = ImportPhase.NO_HOME)
                } else {
                    plan = planned
                    ImportUiState(
                        phase = ImportPhase.PREVIEW,
                        counts = planned.newCounts.map { it.key to it.value },
                        conflicts = planned.conflicts,
                        alreadyPresent = planned.alreadyPresent,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BundleFormatException) {
                ImportUiState(phase = ImportPhase.ERROR)
            } catch (e: IOException) {
                ImportUiState(phase = ImportPhase.ERROR)
            }
            if (next.phase != ImportPhase.PREVIEW) cleanup()
            _state.value = next
        }
    }

    private fun apply() {
        val current = _state.value
        val toApply = plan ?: return
        val dir = staging ?: return
        if (current.phase != ImportPhase.PREVIEW || current.applying) return
        _state.update { it.copy(applying = true) }
        viewModelScope.launch {
            val result = withContext(io) { applier.apply(toApply, current.choices, dir) }
            cleanup()
            _state.value = ImportUiState(phase = ImportPhase.DONE, result = result)
        }
    }

    private fun cleanup() {
        plan = null
        staging?.deleteRecursively()
        staging = null
    }

    override fun onCleared() {
        cleanup()
    }
}
