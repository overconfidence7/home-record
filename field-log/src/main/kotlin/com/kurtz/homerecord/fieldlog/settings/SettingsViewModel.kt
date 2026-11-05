package com.kurtz.homerecord.fieldlog.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.photo.FileStorage
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.core.units.UnitsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What FL-Settings reads and changes: the units choice, the notification switch in system settings, job file storage. */
class SettingsDeps(
    val units: UnitsStore,
    val notificationsEnabled: () -> Boolean,
    val storage: suspend () -> FileStorage,
    val freeUp: suspend () -> Unit,
    val io: CoroutineDispatcher = Dispatchers.IO,
)

class SettingsViewModel(private val deps: SettingsDeps) : ViewModel() {
    private val storage = MutableStateFlow<FileStorage?>(null)
    private val notifications = MutableStateFlow(deps.notificationsEnabled())
    private var freeing = false

    val state: StateFlow<SettingsUiState> = combine(deps.units.units, notifications, storage) { units, on, s ->
        SettingsUiState(units, on, s)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState(deps.units.units.value, notifications.value))

    fun setUnits(system: UnitSystem) = deps.units.set(system)

    /** Reads the notification switch and counts storage again; the route calls it each time Settings comes back on screen. */
    fun refresh() {
        notifications.value = deps.notificationsEnabled()
        viewModelScope.launch { storage.value = withContext(deps.io) { deps.storage() } }
    }

    fun freeUp() {
        if (freeing) return
        freeing = true
        viewModelScope.launch {
            try {
                withContext(deps.io) { deps.freeUp() }
                storage.value = withContext(deps.io) { deps.storage() }
            } finally {
                freeing = false
            }
        }
    }
}
