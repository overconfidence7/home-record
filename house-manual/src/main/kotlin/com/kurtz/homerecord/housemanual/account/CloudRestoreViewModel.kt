package com.kurtz.homerecord.housemanual.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.sync.CloudRestoreResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Sign in from onboarding (AC-H15): once [signedIn] turns true, [restore] pulls the account's home to this phone. The
 * home arriving swaps onboarding for the tabs on its own; with no home on the account, or a failure, the screen says so.
 */
class CloudRestoreViewModel(
    private val restore: suspend () -> CloudRestoreResult,
    signedIn: Flow<Boolean>,
) : ViewModel() {
    private val _state = MutableStateFlow(CloudRestoreUiState())
    val state: StateFlow<CloudRestoreUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            signedIn.distinctUntilChanged().collect { isIn ->
                if (isIn) run() else _state.value = CloudRestoreUiState(CloudRestorePhase.SignedOut)
            }
        }
    }

    fun onEvent(event: CloudRestoreEvent) {
        if (event == CloudRestoreEvent.Retry && _state.value.phase == CloudRestorePhase.Failed) viewModelScope.launch { run() }
    }

    private suspend fun run() {
        _state.value = CloudRestoreUiState(CloudRestorePhase.Working)
        _state.value = when (val result = restore()) {
            // Restored, or a home turned up some other way: the tabs take over; keep the spinner until they do.
            is CloudRestoreResult.Restored, CloudRestoreResult.AlreadyHasHome -> CloudRestoreUiState(CloudRestorePhase.Working)
            CloudRestoreResult.NoHome -> CloudRestoreUiState(CloudRestorePhase.NoHome)
            is CloudRestoreResult.Failed -> CloudRestoreUiState(CloudRestorePhase.Failed, offline = result.failure == ApiFailure.Offline)
        }
    }
}
