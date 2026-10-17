package com.kurtz.homerecord.housemanual.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.billing.Entitlements
import com.kurtz.homerecord.core.billing.OffersResult
import com.kurtz.homerecord.core.billing.Plan
import com.kurtz.homerecord.core.billing.PurchaseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PaywallViewModel(private val entitlements: Entitlements) : ViewModel() {
    private val _state = MutableStateFlow(PaywallUiState(isPro = entitlements.isPro.value))
    val state: StateFlow<PaywallUiState> = _state

    init {
        viewModelScope.launch { entitlements.isPro.collect { pro -> _state.update { it.copy(isPro = pro) } } }
        loadOffers()
    }

    fun onEvent(event: PaywallEvent) {
        when (event) {
            PaywallEvent.Retry -> loadOffers()
            is PaywallEvent.Buy -> buy(event.plan)
            PaywallEvent.Close -> Unit // handled by the route
        }
    }

    private fun loadOffers() {
        _state.update { it.copy(loading = true, purchaseFailed = false) }
        viewModelScope.launch {
            val offers = (entitlements.loadOffers() as? OffersResult.Loaded)?.offers.orEmpty()
            _state.update { it.copy(loading = false, offers = offers) }
        }
    }

    private fun buy(plan: Plan) {
        if (_state.value.purchasing) return
        _state.update { it.copy(purchasing = true, purchaseFailed = false) }
        viewModelScope.launch {
            val result = entitlements.purchase(plan)
            _state.update { it.copy(purchasing = false, purchaseFailed = result == PurchaseResult.Failed) }
        }
    }
}
