package com.kurtz.homerecord.fieldlog.billing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.billing.CompanyBilling
import com.kurtz.homerecord.core.billing.FieldLogPlan

@Composable
fun PlanRoute(
    billing: () -> CompanyBilling,
    companyId: String,
    plan: FieldLogPlan,
    lapsed: Boolean,
    refresh: suspend () -> FieldLogPlan?,
    onBack: () -> Unit,
    onSignOut: () -> Unit,
) {
    val vm = viewModel<PlanViewModel>(
        key = "plan-$companyId",
        factory = viewModelFactory { initializer { PlanViewModel(billing(), companyId, plan, lapsed, refresh) } },
    )
    LaunchedEffect(plan, lapsed) { vm.onPlan(plan, lapsed) }
    val state = vm.state.collectAsState().value
    PlanScreen(state, onEvent = { event ->
        when (event) {
            PlanEvent.Back -> onBack()
            PlanEvent.SignOut -> onSignOut()
            else -> vm.onEvent(event)
        }
    })
}
