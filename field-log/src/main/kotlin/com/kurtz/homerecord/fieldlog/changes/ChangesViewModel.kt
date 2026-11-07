package com.kurtz.homerecord.fieldlog.changes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.changeorder.JobChangeOrders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import com.kurtz.homerecord.core.data.Reloadable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

class ChangesDeps(
    val jobId: String,
    val orders: JobChangeOrders,
    /** RoleGates.leads(jobId): Owner, Office and the job's Lead write change orders (AC-F3). */
    val canManage: Flow<Boolean> = flowOf(false),
)

/** A job's Changes tab: its change orders, newest first, with status and total (AC-F37). */
class ChangesViewModel(d: ChangesDeps) : ViewModel() {
    /** A failed read shows the error state with Retry instead of ending the ViewModel (AC-U4). */
    private val load = Reloadable("Change orders")

    val state: StateFlow<ChangesUiState> = load.wrap(ChangesUiState(loading = false, loadFailed = true)) {
        combine(d.orders.observeForJob(d.jobId), d.canManage) { orders, manage -> ChangesUiState.of(orders, manage) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChangesUiState())

    fun retry() = load.retry()
}
