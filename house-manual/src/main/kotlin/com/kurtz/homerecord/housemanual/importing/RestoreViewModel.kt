package com.kurtz.homerecord.housemanual.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * HM-Restore (AC-C48): [open] tries the passphrase on the picked backup; a wrong one stays on this screen with the
 * field kept for another try, an opened backup goes to [opened] and the route shows its import preview. The passphrase
 * is not saved anywhere (see docs/decisions.md 2026-11-06).
 */
class RestoreViewModel(private val open: suspend (CharArray) -> ImportRead) : ViewModel() {
    private val _state = MutableStateFlow(RestoreUiState())
    val state: StateFlow<RestoreUiState> = _state.asStateFlow()
    private val _opened = MutableStateFlow<ReadImport?>(null)
    val opened: StateFlow<ReadImport?> = _opened.asStateFlow()

    fun onEvent(event: RestoreEvent) {
        when (event) {
            is RestoreEvent.PassphraseChanged -> _state.update { it.copy(passphrase = event.value, error = null) }
            RestoreEvent.Open -> openNow()
            RestoreEvent.Back -> Unit
        }
    }

    private fun openNow() {
        val s = _state.value
        if (s.opening || s.passphrase.isEmpty()) return
        _state.update { it.copy(opening = true, error = null) }
        viewModelScope.launch {
            when (val r = open(s.passphrase.toCharArray())) {
                is ImportRead.Ok -> {
                    _state.update { it.copy(opening = false, passphrase = "") }
                    _opened.value = r.read
                }
                is ImportRead.Failed -> _state.update { it.copy(opening = false, error = r.error) }
            }
        }
    }
}
