package com.kurtz.homerecord.fieldlog.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.media.MediaFileStore
import com.kurtz.homerecord.core.repo.ReportRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val loaded: Boolean = false,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val logoPath: String? = null,
) {
    val canSave: Boolean get() = name.isNotBlank()
}

sealed interface SettingsEvent {
    data class NameChanged(val value: String) : SettingsEvent
    data class PhoneChanged(val value: String) : SettingsEvent
    data class EmailChanged(val value: String) : SettingsEvent
    data object TakeLogo : SettingsEvent
    data object RemoveLogo : SettingsEvent
    data object Save : SettingsEvent
}

/** FL-Settings: company details for the report header (single row). */
class SettingsViewModel(
    private val reports: ReportRepository,
    private val media: MediaFileStore,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done = _done.receiveAsFlow()

    private var savedLogo: String? = null

    init {
        viewModelScope.launch {
            val p = reports.getProfile()
            savedLogo = p?.logoPath
            _state.value = SettingsUiState(
                loaded = true, name = p?.name.orEmpty(), phone = p?.phone.orEmpty(),
                email = p?.email.orEmpty(), logoPath = p?.logoPath,
            )
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.NameChanged -> _state.update { it.copy(name = event.value) }
            is SettingsEvent.PhoneChanged -> _state.update { it.copy(phone = event.value) }
            is SettingsEvent.EmailChanged -> _state.update { it.copy(email = event.value) }
            SettingsEvent.RemoveLogo -> replaceLogo(null)
            SettingsEvent.Save -> save()
            SettingsEvent.TakeLogo -> Unit // handled by the route (camera)
        }
    }

    fun onLogoCaptured(path: String) = replaceLogo(path)

    /** Unsaved replacement photos are deleted as they are superseded; the saved one only on Save. */
    private fun replaceLogo(path: String?) {
        val old = _state.value.logoPath
        if (old != null && old != savedLogo) media.delete(old)
        _state.update { it.copy(logoPath = path) }
    }

    private fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            reports.saveProfile(s.name, s.phone, s.email, s.logoPath)
            if (savedLogo != null && savedLogo != s.logoPath) media.delete(savedLogo)
            savedLogo = s.logoPath
            _done.send(Unit)
        }
    }
}
