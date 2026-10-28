package com.kurtz.homerecord.core.company

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Where the signed-in user stands with a company. */
sealed interface CompanyState {
    data object Loading : CompanyState
    data object NoCompany : CompanyState
    data class Active(val membership: CompanyMembership) : CompanyState
    data class Failed(val failure: ApiFailure) : CompanyState
}

/** The last known membership, so Field Log opens offline. Cleared on sign out (AC-F4). */
interface CompanyCache {
    fun read(): CompanyMembership?
    fun write(membership: CompanyMembership?)
}

/**
 * Field Log supports one company per account in v1 (the first membership wins).
 * Creating or joining refreshes from the server so the role shown is the one the server holds (AC-F1, AC-F2).
 */
class CompanyRepository(private val api: CompanyApi, private val cache: CompanyCache) {
    private val _state = MutableStateFlow<CompanyState>(cache.read()?.let { CompanyState.Active(it) } ?: CompanyState.Loading)
    val state: StateFlow<CompanyState> = _state.asStateFlow()

    val current: CompanyMembership? get() = (_state.value as? CompanyState.Active)?.membership

    /**
     * The company's clock rules for every role, from the cached membership (so offline too) and updated on each
     * refresh; the defaults until a company is active (AC-F9, AC-F12, AC-F13, AC-F18).
     */
    val settings: Flow<CompanySettings> =
        _state.map { (it as? CompanyState.Active)?.membership?.settings ?: CompanySettings() }.distinctUntilChanged()

    suspend fun refresh() {
        when (val r = api.me()) {
            is ApiResult.Ok -> apply(r.value.firstOrNull())
            is ApiResult.Err -> {
                // Offline with a cached company keeps working; only an empty cache shows the failure.
                if (_state.value !is CompanyState.Active) _state.value = CompanyState.Failed(r.failure)
            }
        }
    }

    suspend fun create(name: String, trade: String, phone: String): ApiResult<Unit> = enter(api.create(name, trade, phone))

    suspend fun join(code: String): ApiResult<Unit> = enter(api.join(code))

    /** Drops cached company data when the user signs out. */
    fun clear() {
        cache.write(null)
        _state.value = CompanyState.Loading
    }

    private suspend fun enter(result: ApiResult<String>): ApiResult<Unit> =
        when (result) {
            is ApiResult.Ok -> {
                refresh()
                ApiResult.Ok(Unit)
            }
            is ApiResult.Err -> result
        }

    private fun apply(membership: CompanyMembership?) {
        cache.write(membership)
        _state.value = membership?.let { CompanyState.Active(it) } ?: CompanyState.NoCompany
    }
}
