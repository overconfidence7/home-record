package com.kurtz.homerecord.fieldlog.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.billing.CompanyBilling
import com.kurtz.homerecord.core.billing.FieldLogPlan
import com.kurtz.homerecord.core.billing.FieldLogPlanKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * FL-Paywall for the Owner (AC-F56): store prices for Solo and Crew, purchase and restore as `company:<id>`. After the
 * store says yes, [refresh] re-reads `/me` so the plan the backend granted from the webhook (AC-B50) takes over.
 */
class PlanViewModel(
    private val billing: CompanyBilling,
    private val companyId: String,
    plan: FieldLogPlan,
    lapsed: Boolean,
    private val refresh: suspend () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow(PlanUiState(plan = plan, lapsed = lapsed))
    val state: StateFlow<PlanUiState> = _state

    init {
        loadPlans()
    }

    fun onEvent(event: PlanEvent) {
        when (event) {
            PlanEvent.Back, PlanEvent.SignOut -> Unit
            PlanEvent.Retry -> loadPlans()
            is PlanEvent.Buy -> run(PlanFailure.Purchase) { billing.purchase(companyId, event.planId) }
            PlanEvent.Restore -> run(PlanFailure.NothingToRestore) { billing.restore(companyId) }
        }
    }

    /** The plan `/me` reports now; the route passes it in whenever the membership changes. */
    fun onPlan(plan: FieldLogPlan, lapsed: Boolean) = _state.update { it.copy(plan = plan, lapsed = lapsed) }

    private fun loadPlans() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            billing.plans(companyId).fold(
                onSuccess = { offers -> _state.update { it.copy(loading = false, offers = offers) } },
                onFailure = { _state.update { it.copy(loading = false, loadFailed = true) } },
            )
        }
    }

    private fun run(failure: PlanFailure, block: suspend () -> Result<FieldLogPlanKind>) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, actionFailed = null) }
        viewModelScope.launch {
            val result = block()
            _state.update { it.copy(busy = false, actionFailed = if (result.isFailure) failure else null, bought = result.getOrNull() ?: it.bought) }
            if (result.isSuccess) runCatching { refresh() }
        }
    }
}
