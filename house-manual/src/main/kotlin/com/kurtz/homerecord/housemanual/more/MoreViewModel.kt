package com.kurtz.homerecord.housemanual.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.units.UnitsStore
import com.kurtz.homerecord.housemodel.reminders.ClimateZone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * HM-More's Units and Climate zone rows (T077). Navigation events go straight to the route; this keeps the open picker,
 * writes the units choice to [units] and the zone through [setZone] (which seeds the zone's seasonal tasks, AC-H12).
 */
class MoreViewModel(
    private val units: UnitsStore,
    zone: Flow<ClimateZone?>,
    canEdit: Flow<Boolean>,
    private val setZone: suspend (ClimateZone) -> Unit,
) : ViewModel() {
    private val dialog = MutableStateFlow<MoreDialog?>(null)

    val state: StateFlow<MoreUiState> = combine(units.units, zone, canEdit, dialog) { u, z, edit, d ->
        MoreUiState(units = u, zone = z, canEdit = edit, dialog = d.takeUnless { it == MoreDialog.ZONE && !edit })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MoreUiState(units = units.units.value))

    fun onEvent(e: MoreEvent) {
        when (e) {
            MoreEvent.OpenUnits -> dialog.value = MoreDialog.UNITS
            MoreEvent.OpenZone -> if (state.value.canEdit) dialog.value = MoreDialog.ZONE
            MoreEvent.DismissDialog -> dialog.value = null
            is MoreEvent.SetUnits -> {
                units.set(e.system)
                dialog.value = null
            }
            is MoreEvent.PickZone -> {
                dialog.value = null
                if (state.value.canEdit) viewModelScope.launch { setZone(e.zone) }
            }
            else -> Unit
        }
    }
}
