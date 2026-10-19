package com.kurtz.homerecord.fieldlog.company

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.company.CompanyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Create or join a company (AC-F1, AC-F2). Entered text survives process death; success is observed through [CompanyRepository.state]. */
class WelcomeViewModel(
    private val repo: CompanyRepository,
    private val onSignOut: () -> Unit,
    private val saved: SavedStateHandle = SavedStateHandle(),
    initialCode: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        WelcomeUiState(
            mode = saved.get<String>(MODE)?.let(WelcomeMode::valueOf) ?: if (initialCode != null) WelcomeMode.Join else WelcomeMode.Create,
            name = saved.get<String>(NAME).orEmpty(),
            trade = saved.get<String>(TRADE).orEmpty(),
            phone = saved.get<String>(PHONE).orEmpty(),
            code = saved.get<String>(CODE) ?: initialCode.orEmpty(),
        ),
    )
    val state: StateFlow<WelcomeUiState> = _state.asStateFlow()

    fun onEvent(event: WelcomeEvent) {
        when (event) {
            is WelcomeEvent.ModeChanged -> update { copy(mode = event.mode, error = null) }
            is WelcomeEvent.NameChanged -> update { copy(name = event.value, error = null) }
            is WelcomeEvent.TradeChanged -> update { copy(trade = event.value) }
            is WelcomeEvent.PhoneChanged -> update { copy(phone = event.value) }
            is WelcomeEvent.CodeChanged -> update { copy(code = normalizeInviteCode(event.value), error = null) }
            WelcomeEvent.Submit -> submit()
            WelcomeEvent.SignOut -> onSignOut()
        }
    }

    private fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        update { copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = when (s.mode) {
                WelcomeMode.Create -> repo.create(s.name, s.trade, s.phone)
                WelcomeMode.Join -> repo.join(s.code)
            }
            update { copy(busy = false, error = (result as? ApiResult.Err)?.failure) }
        }
    }

    private fun update(f: WelcomeUiState.() -> WelcomeUiState) {
        _state.update(f)
        val s = _state.value
        saved[MODE] = s.mode.name
        saved[NAME] = s.name
        saved[TRADE] = s.trade
        saved[PHONE] = s.phone
        saved[CODE] = s.code
    }

    private companion object {
        const val MODE = "mode"
        const val NAME = "name"
        const val TRADE = "trade"
        const val PHONE = "phone"
        const val CODE = "code"
    }
}
