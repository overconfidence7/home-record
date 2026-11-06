package com.kurtz.homerecord.housemanual.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A made backup: the share URI and the file name shown on screen. */
data class MadeBackup(val uri: String, val fileName: String)

/** Writes the home's encrypted backup and returns it, or null when it could not be written (AC-C48). */
fun interface BackupExporter {
    suspend fun export(passphrase: CharArray): MadeBackup?
}

/**
 * HM-Backup (AC-C48): checks the passphrase ([BackupPassphrase]), asks [exporter] for the sealed file and hands its
 * URI to the route for the share sheet. The passphrase lives only in memory: it is not put in SavedStateHandle, so
 * after process death the fields are empty and the person types it again.
 */
class BackupViewModel(homeName: String, private val exporter: BackupExporter) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState(homeName = homeName))
    val state: StateFlow<BackupUiState> = _state.asStateFlow()
    private var tried = false

    fun onEvent(event: BackupEvent) {
        when (event) {
            is BackupEvent.PassphraseChanged -> _state.update { it.copy(passphrase = event.value).checked() }
            is BackupEvent.ConfirmChanged -> _state.update { it.copy(confirm = event.value).checked() }
            BackupEvent.Make -> make()
            BackupEvent.ShareHandled -> _state.update { it.copy(uri = null) }
            BackupEvent.Back, BackupEvent.Restore -> Unit
        }
    }

    private fun BackupUiState.checked(): BackupUiState = copy(problem = if (tried) BackupPassphrase.problem(passphrase, confirm) else null)

    private fun make() {
        val s = _state.value
        if (s.busy) return
        tried = true
        val problem = BackupPassphrase.problem(s.passphrase, s.confirm)
        if (problem != null) {
            _state.update { it.copy(problem = problem) }
            return
        }
        _state.update { it.copy(problem = null, busy = true, failed = false, uri = null) }
        viewModelScope.launch {
            val made = exporter.export(s.passphrase.toCharArray())
            _state.update {
                if (made == null) it.copy(busy = false, failed = true) else it.copy(busy = false, uri = made.uri, made = made.fileName)
            }
        }
    }
}
