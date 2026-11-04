package com.kurtz.homerecord.fieldlog.clients

data class ClientsUiState(val loading: Boolean = true, val clients: List<ClientRow> = emptyList())

sealed interface ClientsEvent {
    data class Call(val phone: String) : ClientsEvent
    data class Email(val email: String) : ClientsEvent
    data class OpenJob(val jobId: String) : ClientsEvent
}
