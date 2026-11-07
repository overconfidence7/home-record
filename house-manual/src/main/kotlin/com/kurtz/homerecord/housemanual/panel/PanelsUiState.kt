package com.kurtz.homerecord.housemanual.panel

import androidx.navigation3.runtime.NavKey
import com.kurtz.homerecord.housemanual.PanelKey
import com.kurtz.homerecord.housemanual.PanelsKey
import com.kurtz.homerecord.housemodel.items.ItemRow
import com.kurtz.homerecord.housemodel.panel.isPanelSubtype

/** More → Breaker panels: the home's Breaker panel and Sub-panel items, oldest first (the main panel is usually first). */
/** [viewOnly]: a viewer on a shared home, no Add panel (AC-H16). */
/** [loadFailed]: the panels could not be read; the screen shows what failed and Retry (AC-U4). */
data class PanelsUiState(
    val loading: Boolean = true,
    val panels: List<ItemRow> = emptyList(),
    val viewOnly: Boolean = false,
    val loadFailed: Boolean = false,
)

sealed interface PanelsEvent {
    data object Back : PanelsEvent
    data class Open(val itemId: String) : PanelsEvent
    /** Empty state: add a Breaker panel item. */
    data object Add : PanelsEvent
    data object Retry : PanelsEvent
}

/** Where More → Breaker panels goes: straight to the panel when the home has exactly one, else the list (AC-C40). */
object PanelsEntry {
    fun panels(rows: List<ItemRow>): List<ItemRow> =
        rows.filter { isPanelSubtype(it.subtypeId) }.sortedWith(compareBy<ItemRow> { it.createdAt }.thenBy { it.name })

    fun destination(rows: List<ItemRow>): NavKey = panels(rows).singleOrNull()?.let { PanelKey(it.id) } ?: PanelsKey
}
