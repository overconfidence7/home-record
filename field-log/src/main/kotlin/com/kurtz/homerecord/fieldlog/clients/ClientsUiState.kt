package com.kurtz.homerecord.fieldlog.clients

/** [loadFailed]: the clients could not be read; the screen shows what failed and Retry (AC-U4). */
data class ClientsUiState(val loading: Boolean = true, val clients: List<ClientRow> = emptyList(), val loadFailed: Boolean = false)

sealed interface ClientsEvent {
    data class Call(val phone: String) : ClientsEvent
    data class Email(val email: String) : ClientsEvent
    data class OpenJob(val jobId: String) : ClientsEvent
    data object Retry : ClientsEvent
}
