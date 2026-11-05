package com.kurtz.homerecord.fieldlog.clients

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.fieldlog.jobs.statusLabel

/** FL-Clients (AC-F45): each client from the jobs, with Call and Email, and their jobs under them. */
@Composable
fun ClientsScreen(state: ClientsUiState, onEvent: (ClientsEvent) -> Unit, onBack: () -> Unit) {
    ScreenScaffold(title = stringResource(R.string.clients_title), onBack = onBack) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.clients.isEmpty() -> EmptyState(
                icon = Icons.Rounded.Handshake,
                headline = stringResource(R.string.clients_empty_title),
                body = stringResource(R.string.clients_empty_body),
                actionLabel = null,
                onAction = {},
                modifier = Modifier.padding(padding).testTag("fl_clients_empty"),
            )
            else -> LazyColumn(Modifier.padding(padding).testTag("fl_clients_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
                state.clients.forEachIndexed { index, client ->
                    if (index > 0) item("divider_${client.clientId}") { HorizontalDivider(Modifier.padding(vertical = 4.dp)) }
                    item("client_${client.clientId}") { ClientHeader(client, onEvent) }
                    items(client.jobs, key = { "job_${client.clientId}_${it.jobId}" }) { job ->
                        RecordListItem(
                            headline = job.name,
                            supporting = statusLabel(job.status),
                            leading = { Icon(Icons.Rounded.Work, contentDescription = null, Modifier.padding(start = 16.dp)) },
                            onClick = { onEvent(ClientsEvent.OpenJob(job.jobId)) },
                            modifier = Modifier.testTag("fl_clients_job_${job.jobId}"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientHeader(client: ClientRow, onEvent: (ClientsEvent) -> Unit) {
    val contact = listOfNotNull(client.phone?.let(ClientListBuilder::displayPhone), client.email).joinToString("\n")
    val jobs = pluralStringResource(R.plurals.clients_job_count, client.jobs.size, client.jobs.size)
    RecordListItem(
        headline = client.name,
        supporting = if (contact.isEmpty()) jobs else "$contact\n$jobs",
        supportingMaxLines = Int.MAX_VALUE,
        leading = { Icon(Icons.Rounded.Person, contentDescription = null) },
        trailing = {
            Row {
                client.phone?.let { phone ->
                    IconButton({ onEvent(ClientsEvent.Call(phone)) }, Modifier.size(48.dp).testTag("fl_clients_call_${client.clientId}")) {
                        Icon(Icons.Rounded.Call, contentDescription = stringResource(R.string.clients_call, client.name))
                    }
                }
                client.email?.let { email ->
                    IconButton({ onEvent(ClientsEvent.Email(email)) }, Modifier.size(48.dp).testTag("fl_clients_email_${client.clientId}")) {
                        Icon(Icons.Rounded.Email, contentDescription = stringResource(R.string.clients_email, client.name))
                    }
                }
            }
        },
        modifier = Modifier.testTag("fl_clients_row_${client.clientId}"),
    )
}
