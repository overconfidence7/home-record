package com.kurtz.homerecord.housemanual.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.auth.AuthState
import com.kurtz.homerecord.core.company.ApiFailure
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.share.AcceptResult
import com.kurtz.homerecord.core.share.HomeShareApi
import com.kurtz.homerecord.core.share.InviteFailure
import com.kurtz.homerecord.core.sync.CloudRestoreResult
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * HM-Share (AC-H16): who is on [homeId] and the owner's open links. Inviting makes a link on the server and hands it
 * to [links] for the share sheet; Revoke and Remove go straight to the server and reload the list.
 */
class ShareViewModel(
    private val homeId: String,
    private val api: HomeShareApi,
    signedIn: Flow<Boolean>,
) : ViewModel() {
    private val _state = MutableStateFlow(ShareUiState())
    val state: StateFlow<ShareUiState> = _state.asStateFlow()
    private val _links = Channel<String>(Channel.BUFFERED)
    /** Each new invite link, once, for the share sheet. */
    val links: Flow<String> = _links.receiveAsFlow()

    init {
        viewModelScope.launch {
            signedIn.distinctUntilChanged().collect { isIn ->
                if (isIn) load() else _state.value = ShareUiState(SharePhase.SignedOut)
            }
        }
    }

    fun onEvent(event: ShareEvent) {
        when (event) {
            ShareEvent.Back, ShareEvent.SignIn -> Unit
            ShareEvent.Retry -> viewModelScope.launch { load() }
            is ShareEvent.Invite -> if (_state.value.creating == null && _state.value.isOwner) {
                _state.update { it.copy(creating = event.role, actionFailed = false) }
                viewModelScope.launch {
                    when (val r = api.invite(homeId, event.role)) {
                        is ApiResult.Ok -> {
                            _links.send(r.value.link)
                            _state.update { it.copy(creating = null) }
                            load(quiet = true)
                        }
                        is ApiResult.Err -> _state.update { it.copy(creating = null, actionFailed = true, offline = r.failure == ApiFailure.Offline) }
                    }
                }
            }
            is ShareEvent.Revoke -> act { api.revoke(homeId, event.inviteId) }
            is ShareEvent.Remove -> _state.update { it.copy(confirmRemove = event.person) }
            ShareEvent.DismissRemove -> _state.update { it.copy(confirmRemove = null) }
            ShareEvent.ConfirmRemove -> {
                val person = _state.value.confirmRemove ?: return
                _state.update { it.copy(confirmRemove = null) }
                act { api.remove(homeId, person.userId) }
            }
        }
    }

    private fun act(call: suspend () -> ApiResult<Unit>) {
        _state.update { it.copy(actionFailed = false) }
        viewModelScope.launch {
            when (val r = call()) {
                is ApiResult.Ok -> load(quiet = true)
                is ApiResult.Err -> _state.update { it.copy(actionFailed = true, offline = r.failure == ApiFailure.Offline) }
            }
        }
    }

    private suspend fun load(quiet: Boolean = false) {
        if (!quiet) _state.update { it.copy(phase = SharePhase.Loading) }
        when (val r = api.people(homeId)) {
            is ApiResult.Ok -> _state.update {
                it.copy(phase = SharePhase.Loaded, people = r.value.people, invites = r.value.invites, offline = false)
            }
            is ApiResult.Err -> _state.update {
                when {
                    quiet -> it.copy(actionFailed = true, offline = r.failure == ApiFailure.Offline)
                    r.failure == ApiFailure.NotFound -> it.copy(phase = SharePhase.NotUploaded)
                    else -> it.copy(phase = SharePhase.Failed, offline = r.failure == ApiFailure.Offline)
                }
            }
        }
    }
}

/**
 * Opening an invite link (AC-H16): waits for a signed-in account (the sign-in form shows meanwhile), accepts the link,
 * then, on a phone with no home, pulls the shared home down ([restore]); the home arriving swaps this screen for the
 * tabs. A phone already holding that home is done at once; one holding another home keeps it and says so.
 */
class JoinViewModel(
    private val token: String,
    auth: Flow<AuthState>,
    private val accept: suspend (String) -> AcceptResult,
    private val restore: suspend (String) -> CloudRestoreResult,
    private val localHomeId: suspend () -> String?,
) : ViewModel() {
    private val _state = MutableStateFlow(JoinUiState())
    val state: StateFlow<JoinUiState> = _state.asStateFlow()
    private var signedIn = false

    init {
        viewModelScope.launch {
            auth.collect { a ->
                signedIn = a is AuthState.SignedIn
                when (a) {
                    AuthState.Restoring -> Unit
                    AuthState.SignedOut -> if (_state.value.phase != JoinPhase.Failed && _state.value.phase != JoinPhase.Done) {
                        _state.update { it.copy(phase = JoinPhase.SignIn, failure = null) }
                    }
                    is AuthState.SignedIn -> if (_state.value.phase == JoinPhase.Checking || _state.value.phase == JoinPhase.SignIn) run()
                }
            }
        }
    }

    fun onEvent(event: JoinEvent) {
        when (event) {
            JoinEvent.Retry -> if (_state.value.phase == JoinPhase.Failed && _state.value.canRetry) {
                if (signedIn) run() else _state.update { it.copy(phase = JoinPhase.SignIn, failure = null, restoreFailed = false) }
            }
            JoinEvent.Close -> Unit
        }
    }

    private fun run() {
        _state.update { it.copy(phase = JoinPhase.Joining, failure = null, restoreFailed = false) }
        viewModelScope.launch {
            val joined = when (val r = accept(token)) {
                is AcceptResult.Failed -> {
                    _state.update {
                        if (r.failure == InviteFailure.SignedOut) it.copy(phase = JoinPhase.SignIn)
                        else it.copy(phase = JoinPhase.Failed, failure = r.failure)
                    }
                    return@launch
                }
                is AcceptResult.Joined -> r.home
            }
            _state.update { it.copy(homeName = joined.homeName, role = joined.role) }
            val local = localHomeId()
            when {
                local == joined.homeId -> _state.update { it.copy(phase = JoinPhase.Done) }
                local != null -> _state.update { it.copy(phase = JoinPhase.OtherHome) }
                else -> {
                    _state.update { it.copy(phase = JoinPhase.Restoring) }
                    _state.update {
                        when (restore(joined.homeId)) {
                            is CloudRestoreResult.Restored, CloudRestoreResult.AlreadyHasHome -> it.copy(phase = JoinPhase.Done)
                            CloudRestoreResult.NoHome, is CloudRestoreResult.Failed -> it.copy(phase = JoinPhase.Failed, restoreFailed = true)
                        }
                    }
                }
            }
        }
    }
}
