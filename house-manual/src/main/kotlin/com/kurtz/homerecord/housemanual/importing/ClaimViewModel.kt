package com.kurtz.homerecord.housemanual.importing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.auth.AuthState
import com.kurtz.homerecord.core.bundle.HandoffClaimApi
import com.kurtz.homerecord.core.bundle.HandoffClaimFailure
import com.kurtz.homerecord.core.bundle.HandoffClaimResult
import com.kurtz.homerecord.core.bundle.HandoffDownloadResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * HM-Claim (AC-H17, AC-B40): waits for a signed-in account (the sign-in form shows meanwhile), claims the link for it,
 * downloads the bundle to [dest] and then reports [ClaimPhase.Ready] so the import preview opens on [dest].
 * Process death starts over: claiming again with the same account is allowed by the server.
 */
class ClaimViewModel(
    private val token: String,
    private val auth: Flow<AuthState>,
    private val api: HandoffClaimApi,
    val dest: File,
) : ViewModel() {
    private val _state = MutableStateFlow(ClaimUiState())
    val state: StateFlow<ClaimUiState> = _state.asStateFlow()
    private var signedIn = false

    init {
        viewModelScope.launch {
            auth.collect { a ->
                signedIn = a is AuthState.SignedIn
                when (a) {
                    AuthState.Restoring -> Unit
                    AuthState.SignedOut -> if (_state.value.phase != ClaimPhase.Failed) {
                        _state.update { it.copy(phase = ClaimPhase.SignIn, failure = null) }
                    }
                    is AuthState.SignedIn -> if (_state.value.phase == ClaimPhase.Checking || _state.value.phase == ClaimPhase.SignIn) run()
                }
            }
        }
    }

    fun onEvent(event: ClaimEvent) {
        when (event) {
            ClaimEvent.Retry -> if (_state.value.phase == ClaimPhase.Failed && _state.value.canRetry) {
                if (signedIn) run() else _state.update { it.copy(phase = ClaimPhase.SignIn, failure = null) }
            }
            ClaimEvent.Close -> Unit
        }
    }

    private fun run() {
        _state.update { it.copy(phase = ClaimPhase.Claiming, failure = null) }
        viewModelScope.launch {
            when (val claim = api.claim(token)) {
                is HandoffClaimResult.Failed -> return@launch fail(claim.failure)
                is HandoffClaimResult.Claimed -> _state.update {
                    it.copy(phase = ClaimPhase.Downloading, companyName = claim.handoff.companyName, homeName = claim.handoff.homeName)
                }
            }
            when (val download = api.download(token, dest)) {
                is HandoffDownloadResult.Failed -> fail(download.failure)
                is HandoffDownloadResult.Saved -> _state.update { it.copy(phase = ClaimPhase.Ready) }
            }
        }
    }

    private fun fail(failure: HandoffClaimFailure) {
        _state.update {
            if (failure == HandoffClaimFailure.SignedOut) it.copy(phase = ClaimPhase.SignIn, failure = null)
            else it.copy(phase = ClaimPhase.Failed, failure = failure)
        }
    }
}
