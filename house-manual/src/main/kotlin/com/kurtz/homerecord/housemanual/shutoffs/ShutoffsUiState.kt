package com.kurtz.homerecord.housemanual.shutoffs

import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.ShutoffKind

data class ShutoffsUiState(
    val loaded: Boolean = false,
    val shutoffs: List<Shutoff> = emptyList(),
    val roomNames: Map<String, String> = emptyMap(),
) {
    /** Grouped by kind in enum order, so the three mains come first. */
    val groups: List<Pair<ShutoffKind, List<Shutoff>>>
        get() = shutoffs.groupBy { it.kind }.toList().sortedBy { it.first.ordinal }
}

sealed interface ShutoffsEvent {
    data object Add : ShutoffsEvent
    data class Open(val shutoffId: String) : ShutoffsEvent
}
