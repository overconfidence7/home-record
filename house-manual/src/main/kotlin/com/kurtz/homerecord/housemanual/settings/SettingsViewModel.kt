package com.kurtz.homerecord.housemanual.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.repo.HomeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Side-effect seam: writes the encrypted backup and returns the cache-relative path (fake in tests). */
interface BackupExporter {
    suspend fun export(homeId: String, passphrase: String): String?
}

/** HM-Settings (AC-88): passphrase-protected backup export. */
class SettingsViewModel(
    private val homes: HomeRepository,
    private val exporter: BackupExporter,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _share = Channel<String>(Channel.BUFFERED)
    /** Cache-relative path of a finished backup, ready for the share sheet. */
    val share = _share.receiveAsFlow()

    init {
        viewModelScope.launch { homes.observeHome().collect { home -> _state.update { it.copy(hasHome = home != null) } } }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.PassphraseChanged -> _state.update { it.copy(passphrase = event.value, exportFailed = false) }
            is SettingsEvent.ConfirmChanged -> _state.update { it.copy(confirm = event.value, exportFailed = false) }
            SettingsEvent.Export -> export()
        }
    }

    private fun export() {
        val current = _state.value
        if (!current.canExport) return
        _state.update { it.copy(exporting = true, exportFailed = false) }
        viewModelScope.launch {
            val path = try {
                homes.getHome()?.let { exporter.export(it.id, current.passphrase) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                null
            }
            if (path == null) {
                _state.update { it.copy(exporting = false, exportFailed = true) }
            } else {
                _state.update { it.copy(exporting = false, passphrase = "", confirm = "") }
                _share.send(path)
            }
        }
    }
}
