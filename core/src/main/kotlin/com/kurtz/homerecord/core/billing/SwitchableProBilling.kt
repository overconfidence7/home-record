package com.kurtz.homerecord.core.billing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Debug builds only: [base] with a QA switch on top, so the keyless QA builds can reach what Pro unlocks (sign in, sync)
 * after the debug seed's `pro` scenario turns [forcedPro] on. Plans, purchase and restore still go to [base]; release
 * builds use [base] directly (docs/decisions.md 2026-11-07).
 */
class SwitchableProBilling(
    private val base: ProBilling,
    forcedPro: StateFlow<Boolean>,
    scope: CoroutineScope,
) : ProBilling by base {
    override val isPro: StateFlow<Boolean> =
        combine(base.isPro, forcedPro) { bought, forced -> bought || forced }
            .stateIn(scope, SharingStarted.Eagerly, base.isPro.value || forcedPro.value)
}
