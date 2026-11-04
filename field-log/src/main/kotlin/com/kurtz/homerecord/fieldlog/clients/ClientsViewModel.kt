package com.kurtz.homerecord.fieldlog.clients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kurtz.homerecord.core.data.ClientEntity
import com.kurtz.homerecord.core.data.JobEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What FL-Clients reads: the company's clients and jobs, live from Room. */
class ClientsDeps(val clients: Flow<List<ClientEntity>>, val jobs: Flow<List<JobEntity>>)

/** FL-Clients (AC-F45): clients from jobs, kept current as jobs and clients change. */
class ClientsViewModel(deps: ClientsDeps) : ViewModel() {
    val state: StateFlow<ClientsUiState> = combine(deps.clients, deps.jobs) { clients, jobs ->
        ClientsUiState(loading = false, clients = ClientListBuilder.build(clients, jobs))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClientsUiState())
}
