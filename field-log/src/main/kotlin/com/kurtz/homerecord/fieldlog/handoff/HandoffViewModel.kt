package com.kurtz.homerecord.fieldlog.handoff

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.bundle.ExportedHome
import com.kurtz.homerecord.core.bundle.HandoffEmail
import com.kurtz.homerecord.core.bundle.HandoffSendFailure
import com.kurtz.homerecord.core.bundle.HandoffSendResult
import com.kurtz.homerecord.core.bundle.HandoffSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Counts the job's house for the handoff summary, writes the `.homerecord` on Export, and on Send to homeowner writes
 * it and uploads it so the server emails the claim link (AC-F51, AC-B40). The home is read again on Export and Send so
 * a photo taken while the screen was open goes in the file. The typed address and a finished send survive process death.
 */
class HandoffViewModel(
    jobName: String,
    private val load: suspend () -> ExportedHome?,
    private val write: suspend (ExportedHome) -> File?,
    private val send: suspend (email: String, bundle: File) -> HandoffSendResult = { _, _ -> HandoffSendResult.Failed(HandoffSendFailure.Failed) },
    private val saved: SavedStateHandle = SavedStateHandle(),
    prefillEmail: suspend () -> String? = { null },
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) : ViewModel() {
    private val _state = MutableStateFlow(
        HandoffUiState(
            jobName = jobName,
            email = saved.get<String>(KEY_EMAIL).orEmpty(),
            sentTo = saved.get<String>(KEY_SENT_TO),
            sentUntil = saved.get<Long>(KEY_SENT_UNTIL)?.let(LocalDate::ofEpochDay),
        ),
    )
    val state: StateFlow<HandoffUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val home = runCatching { load() }.getOrNull()
            _state.update {
                it.copy(loading = false, summary = home?.let { h -> HandoffSummary.of(h.bundle) }, missing = home == null)
            }
        }
        if (!saved.contains(KEY_EMAIL)) {
            viewModelScope.launch {
                val email = runCatching { prefillEmail() }.getOrNull()?.trim().orEmpty()
                if (email.isNotEmpty() && !saved.contains(KEY_EMAIL)) setEmail(email)
            }
        }
    }

    fun onEvent(event: HandoffEvent) {
        when (event) {
            HandoffEvent.Export -> export()
            HandoffEvent.ShareHandled -> _state.update { it.copy(sharePath = null) }
            is HandoffEvent.EmailChanged -> setEmail(event.value)
            HandoffEvent.Send -> send()
            HandoffEvent.SendAnother -> {
                saved.remove<String>(KEY_SENT_TO)
                saved.remove<Long>(KEY_SENT_UNTIL)
                _state.update { it.copy(sentTo = null, sentUntil = null) }
            }
            HandoffEvent.Back -> Unit
        }
    }

    private fun setEmail(value: String) {
        saved[KEY_EMAIL] = value
        _state.update { it.copy(email = value, emailInvalid = false, sendFailure = null) }
    }

    private fun ready(s: HandoffUiState) = !s.busy && !s.loading && !s.missing && s.summary?.isEmpty == false

    private fun export() {
        if (!ready(_state.value)) return
        _state.update { it.copy(exporting = true, failed = false) }
        viewModelScope.launch {
            val home = runCatching { load() }.getOrNull()
            val file = home?.let { runCatching { write(it) }.getOrNull() }
            _state.update {
                it.copy(
                    exporting = false,
                    summary = home?.let { h -> HandoffSummary.of(h.bundle) } ?: it.summary,
                    sharePath = file?.path,
                    failed = file == null,
                )
            }
        }
    }

    private fun send() {
        val s = _state.value
        if (!ready(s) || s.sentTo != null) return
        if (!HandoffEmail.isValid(s.email)) {
            _state.update { it.copy(emailInvalid = true) }
            return
        }
        val email = HandoffEmail.normalize(s.email)
        _state.update { it.copy(sending = true, sendFailure = null, failed = false) }
        viewModelScope.launch {
            val home = runCatching { load() }.getOrNull()
            val file = home?.let { runCatching { write(it) }.getOrNull() }
            val result = file?.let { runCatching { send(email, it) }.getOrNull() }
                ?: HandoffSendResult.Failed(HandoffSendFailure.Failed)
            file?.delete()
            when (result) {
                is HandoffSendResult.Sent -> {
                    val until = Instant.ofEpochMilli(result.handoff.expiresAt).atZone(zone()).toLocalDate()
                    saved[KEY_SENT_TO] = result.handoff.email
                    saved[KEY_SENT_UNTIL] = until.toEpochDay()
                    _state.update {
                        it.copy(
                            sending = false, sentTo = result.handoff.email, sentUntil = until,
                            summary = home?.let { h -> HandoffSummary.of(h.bundle) } ?: it.summary,
                        )
                    }
                }
                is HandoffSendResult.Failed -> _state.update { it.copy(sending = false, sendFailure = result.failure) }
            }
        }
    }

    private companion object {
        const val KEY_EMAIL = "handoff.email"
        const val KEY_SENT_TO = "handoff.sentTo"
        const val KEY_SENT_UNTIL = "handoff.sentUntil"
    }
}
