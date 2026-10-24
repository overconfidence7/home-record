package com.kurtz.homerecord.housemanual.paywall

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.billing.LimitedFeature
import com.kurtz.homerecord.core.billing.ProBilling

/** Closes itself the moment Pro turns on, so the limit that opened it is already lifted (AC-H18). */
@Composable
fun PaywallRoute(billing: ProBilling, feature: LimitedFeature?, onClose: () -> Unit) {
    val vm = viewModel<PaywallViewModel>(
        factory = viewModelFactory { initializer { PaywallViewModel(billing, feature) } },
    )
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.isPro) { if (state.isPro) onClose() }
    PaywallScreen(state, onEvent = { if (it == PaywallEvent.Back) onClose() else vm.onEvent(it) })
}
