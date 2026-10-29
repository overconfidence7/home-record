package com.kurtz.homerecord.core.billing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Test stand-in whose Pro state can be flipped. */
class FakeEntitlements(pro: Boolean = false) : Entitlements {
    private val state = MutableStateFlow(pro)
    override val isPro: StateFlow<Boolean> = state
    fun setPro(value: Boolean) { state.value = value }
}
