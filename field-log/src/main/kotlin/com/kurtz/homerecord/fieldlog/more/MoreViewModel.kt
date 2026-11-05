package com.kurtz.homerecord.fieldlog.more

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.billing.FieldLogPlan
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.units.UnitSystem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What FL-More needs from the app: who is signed in, the unsent change count, the units choice and the version (AC-F4). */
class MoreDeps(
    val companyName: String,
    val role: CompanyRole,
    val unsynced: Flow<Int>,
    val units: StateFlow<UnitSystem>,
    val userEmail: String? = null,
    val versionName: String? = null,
    val plan: FieldLogPlan? = null,
)

/** FL-More: the rows and their supporting lines; navigation stays in the route. Storage moved to Settings (T065a). */
class MoreViewModel(private val deps: MoreDeps) : ViewModel() {
    private fun state(unsynced: Int, units: UnitSystem) =
        MoreUiState(deps.companyName, deps.role, unsynced, deps.plan, deps.userEmail, units, deps.versionName)

    val state: StateFlow<MoreUiState> = combine(deps.unsynced, deps.units) { unsynced, units -> state(unsynced, units) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, state(0, deps.units.value))
}
