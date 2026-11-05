package com.kurtz.homerecord.fieldlog.billing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.billing.CompanyBilling
import com.kurtz.homerecord.core.billing.FieldLogPlan
import com.kurtz.homerecord.core.billing.FieldLogPlanKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * FL-Paywall for the Owner (AC-F56): store prices for Solo and Crew, purchase and restore as `company:<id>`. After the
 * store says yes, [refresh] re-reads `/me` so the plan the backend granted from the webhook (AC-B50) takes over. The
 * webhook usually lands a few seconds after the store, so the read is retried on [backoffMillis] until `/me` reports the
 * plan just bought (or the list runs out; the next return to the app reads `/me` again).
 */
class PlanViewModel(
    private val billing: CompanyBilling,
    private val companyId: String,
    plan: FieldLogPlan,
    lapsed: Boolean,
    /** Re-reads `/me`; returns the company's plan now, or null when the read failed. */
    private val refresh: suspend () -> FieldLogPlan?,
    private val backoffMillis: List<Long> = PLAN_REFRESH_BACKOFF,
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
            result.getOrNull()?.let { awaitPlan(it) }
        }
    }

    /** Polls `/me` until it reports [kind] in force, so the paywall gives way without a restart. */
    private suspend fun awaitPlan(kind: FieldLogPlanKind) {
        for (wait in backoffMillis) {
            delay(wait)
            val plan = runCatching { refresh() }.getOrNull() ?: continue
            onPlan(plan, lapsed = _state.value.lapsed && !plan.isActive)
            if (plan.kind == kind) return
        }
    }

    companion object {
        /** First read straight away, then about a minute and a half of backing off for RevenueCat's webhook. */
        val PLAN_REFRESH_BACKOFF = listOf(0L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L)
    }
}
