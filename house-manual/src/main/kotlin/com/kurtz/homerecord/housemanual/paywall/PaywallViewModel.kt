package com.kurtz.homerecord.housemanual.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.billing.LimitedFeature
import com.kurtz.homerecord.core.billing.ProBilling
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PaywallViewModel(private val billing: ProBilling, feature: LimitedFeature?) : ViewModel() {
    private val _state = MutableStateFlow(PaywallUiState(feature = feature, isPro = billing.isPro.value))
    val state: StateFlow<PaywallUiState> = _state

    init {
        viewModelScope.launch { billing.isPro.collect { pro -> _state.update { it.copy(isPro = pro) } } }
        loadPlans()
    }

    fun onEvent(event: PaywallEvent) {
        when (event) {
            PaywallEvent.Back -> Unit
            PaywallEvent.Retry -> loadPlans()
            is PaywallEvent.Buy -> run(PaywallFailure.Purchase) { billing.purchase(event.planId) }
            PaywallEvent.Restore -> run(PaywallFailure.NothingToRestore) { billing.restore() }
        }
    }

    private fun loadPlans() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            billing.plans().fold(
                onSuccess = { plans -> _state.update { it.copy(loading = false, plans = plans) } },
                onFailure = { _state.update { it.copy(loading = false, loadFailed = true) } },
            )
        }
    }

    private fun run(failure: PaywallFailure, block: suspend () -> Result<Unit>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, actionFailed = null) }
        viewModelScope.launch {
            val result = block()
            _state.update { it.copy(busy = false, actionFailed = if (result.isFailure) failure else null) }
        }
    }
}
