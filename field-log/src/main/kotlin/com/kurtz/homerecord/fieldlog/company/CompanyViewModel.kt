package com.kurtz.homerecord.fieldlog.company

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.company.ApiResult
import com.kurtz.homerecord.core.company.CompanyApi
import com.kurtz.homerecord.core.company.CompanyProfile
import com.kurtz.homerecord.core.company.CompanyProfileUpdate
import com.kurtz.homerecord.core.company.CompanySettings
import com.kurtz.homerecord.core.company.OvertimeRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * FL-Company settings form (Owner and Office; the server returns 404 to anyone else). Unsaved edits
 * are kept in [saved] and laid over the loaded profile, so they survive process death.
 */
class CompanyViewModel(
    private val api: CompanyApi,
    private val companyId: String,
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val onSaved: (String) -> Unit = {},
) : ViewModel() {
    private val _state = MutableStateFlow(CompanyUiState())
    val state: StateFlow<CompanyUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: CompanyEvent) {
        when (event) {
            is CompanyEvent.Name -> edit { copy(name = event.value) }
            is CompanyEvent.Trade -> edit { copy(trade = event.value) }
            is CompanyEvent.Phone -> edit { copy(phone = event.value) }
            is CompanyEvent.Email -> edit { copy(email = event.value) }
            is CompanyEvent.Address -> edit { copy(address = event.value) }
            is CompanyEvent.Overtime -> edit { copy(overtimeRule = event.rule) }
            is CompanyEvent.BreakRule -> edit { copy(breakRule = event.on) }
            is CompanyEvent.RequireGps -> edit { copy(requireGps = event.on) }
            is CompanyEvent.PunchDistance -> edit { copy(punchDistanceText = event.value.filter(Char::isDigit).take(5)) }
            is CompanyEvent.PhotoOnPunch -> edit { copy(photoOnPunch = event.on) }
            CompanyEvent.Save -> save()
            CompanyEvent.Retry -> load()
        }
    }

    private fun load() {
        _state.update { it.copy(status = TeamStatus.Loading, failure = null) }
        viewModelScope.launch {
            when (val r = api.profile(companyId)) {
                is ApiResult.Ok -> _state.update { s ->
                    val loaded = fill(s, r.value)
                    saved.get<String>(DRAFT)?.let(::decode)?.applyTo(loaded) ?: loaded
                }
                is ApiResult.Err -> _state.update { it.copy(status = TeamStatus.Failed, failure = r.failure) }
            }
        }
    }

    private fun fill(s: CompanyUiState, p: CompanyProfile) = s.copy(
        status = TeamStatus.Ready,
        loaded = p,
        name = p.name,
        trade = p.trade.orEmpty(),
        phone = p.phone.orEmpty(),
        email = p.email.orEmpty(),
        address = p.address.orEmpty(),
        overtimeRule = p.settings.overtimeRule,
        breakRule = p.settings.breakRule,
        requireGps = p.settings.requireGps,
        punchDistanceText = p.settings.punchDistanceFt.toString(),
        photoOnPunch = p.settings.photoOnPunch,
    )

    private fun save() {
        val s = _state.value
        val distance = s.punchDistanceFt
        if (!s.canSave || distance == null) return
        _state.update { it.copy(saving = true, saveFailure = null) }
        val update = CompanyProfileUpdate(
            name = s.name.trim(),
            trade = s.trade.trim(),
            phone = s.phone.trim(),
            email = s.email.trim(),
            address = s.address.trim(),
            settings = CompanySettings(s.overtimeRule, s.breakRule, s.requireGps, distance, s.photoOnPunch),
        )
        viewModelScope.launch {
            when (val r = api.updateProfile(companyId, update)) {
                is ApiResult.Ok -> {
                    _state.update { it.copy(saving = false, saved = true) }
                    onSaved(update.name.orEmpty())
                }
                is ApiResult.Err -> _state.update { it.copy(saving = false, saveFailure = r.failure) }
            }
        }
    }

    private fun edit(f: CompanyUiState.() -> CompanyUiState) {
        _state.update { f(it).copy(saveFailure = null) }
        saved[DRAFT] = json.encodeToString(CompanyDraft.serializer(), CompanyDraft.of(_state.value))
    }

    private fun decode(raw: String): CompanyDraft? = runCatching { json.decodeFromString(CompanyDraft.serializer(), raw) }.getOrNull()

    /** The editable fields of [CompanyUiState], as typed. */
    @Serializable
    private data class CompanyDraft(
        val name: String,
        val trade: String,
        val phone: String,
        val email: String,
        val address: String,
        val overtimeRule: String,
        val breakRule: Boolean,
        val requireGps: Boolean,
        val punchDistanceText: String,
        val photoOnPunch: Boolean,
    ) {
        fun applyTo(s: CompanyUiState) = s.copy(
            name = name, trade = trade, phone = phone, email = email, address = address,
            overtimeRule = OvertimeRule.fromWire(overtimeRule), breakRule = breakRule, requireGps = requireGps,
            punchDistanceText = punchDistanceText, photoOnPunch = photoOnPunch,
        )

        companion object {
            fun of(s: CompanyUiState) = CompanyDraft(
                s.name, s.trade, s.phone, s.email, s.address, s.overtimeRule.wire, s.breakRule, s.requireGps,
                s.punchDistanceText, s.photoOnPunch,
            )
        }
    }

    private companion object {
        const val DRAFT = "companyDraft"
        val json = Json { ignoreUnknownKeys = true }
    }
}
