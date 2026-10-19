package com.kurtz.homerecord.fieldlog.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.company.TeamMember
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.MoneyField
import com.kurtz.homerecord.coreui.components.OptionsSheet
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SheetOption
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import java.util.Locale

/** FL-Team (Owner and Office only): invite code, members, role changes, removal and rates. */
@Composable
fun TeamScreen(
    state: TeamUiState,
    onEvent: (TeamEvent) -> Unit,
    onShareInvite: (code: String, link: String) -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(title = stringResource(R.string.team_title), subtitle = state.companyName.ifBlank { null }, onBack = onBack) { padding ->
        when (state.status) {
            TeamStatus.Loading -> LoadingState(Modifier.padding(padding))
            TeamStatus.Failed -> ErrorState(
                message = state.failure?.let { failureText(it) }.orEmpty(),
                onRetry = { onEvent(TeamEvent.Refresh) },
                modifier = Modifier.padding(padding),
            )
            TeamStatus.Ready -> TeamList(state, onEvent, onShareInvite, padding)
        }
    }
    TeamDialogs(state, onEvent)
}

@Composable
private fun TeamList(state: TeamUiState, onEvent: (TeamEvent) -> Unit, onShareInvite: (String, String) -> Unit, padding: PaddingValues) {
    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
        item("invite") { InviteCard(state, onEvent, onShareInvite) }
        state.actionError?.let { failure ->
            item("error") {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(failureText(failure), color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onEvent(TeamEvent.DismissError) }) { Text(stringResource(R.string.dismiss)) }
                }
            }
        }
        item("header") {
            Text(
                stringResource(R.string.team_members_header, state.members.size),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        items(state.members, key = { it.userId }) { member ->
            val editable = state.rolesFor(member).isNotEmpty() || state.canRemove(member) || state.canSetRate(member)
            RecordListItem(
                headline = member.displayName,
                supporting = memberSupporting(member, state.callerRole),
                leading = { Icon(Icons.Rounded.Person, contentDescription = null) },
                trailing = null,
                onClick = if (editable) ({ onEvent(TeamEvent.OpenActions(member)) }) else null,
            )
        }
    }
}

@Composable
private fun memberSupporting(member: TeamMember, caller: CompanyRole): String {
    val role = roleLabel(member.role)
    val rate = member.rateCents?.takeIf { caller == CompanyRole.Owner }
    return if (rate != null) stringResource(R.string.team_member_with_rate, role, formatRate(rate)) else role
}

private fun formatRate(cents: Long): String = String.format(Locale.US, "$%d.%02d", cents / 100, cents % 100)

@Composable
private fun InviteCard(state: TeamUiState, onEvent: (TeamEvent) -> Unit, onShareInvite: (String, String) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.team_invite_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.team_invite_body), style = MaterialTheme.typography.bodyMedium)
            val code = state.inviteCode
            if (code != null) {
                val spoken = code.toList().joinToString(" ")
                Text(
                    code.chunked(4).joinToString(" "),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = spoken },
                )
                Button(
                    onClick = { onShareInvite(code, state.inviteLink.orEmpty()) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null)
                    Text(stringResource(R.string.team_invite_share), modifier = Modifier.padding(start = 8.dp))
                }
            }
            OutlinedButton(
                onClick = { onEvent(TeamEvent.NewCode) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.team_invite_new)) }
        }
    }
}

@Composable
private fun TeamDialogs(state: TeamUiState, onEvent: (TeamEvent) -> Unit) {
    state.actionsFor?.let { member ->
        val options = buildList {
            state.rolesFor(member).forEach { add(SheetOption("role:${it.wire}", stringResource(R.string.team_make_role, roleLabel(it)), Icons.Rounded.Groups)) }
            if (state.canSetRate(member)) add(SheetOption("rate", stringResource(R.string.team_set_rate), Icons.Rounded.Payments))
            if (state.canRemove(member)) add(SheetOption("remove", stringResource(R.string.team_remove), Icons.Rounded.PersonRemove))
        }
        OptionsSheet(
            title = member.displayName,
            options = options,
            onDismiss = { onEvent(TeamEvent.DismissActions) },
            onPick = { option ->
                when {
                    option.id == "remove" -> onEvent(TeamEvent.AskRemove(member))
                    option.id == "rate" -> onEvent(TeamEvent.AskRate(member))
                    option.id.startsWith("role:") -> onEvent(TeamEvent.SetRole(member, CompanyRole.fromWire(option.id.removePrefix("role:"))))
                }
            },
        )
    }
    state.confirmRemove?.let { member ->
        AlertDialog(
            onDismissRequest = { onEvent(TeamEvent.CancelRemove) },
            title = { Text(stringResource(R.string.team_remove_title, member.displayName)) },
            text = { Text(stringResource(R.string.team_remove_body)) },
            confirmButton = { TextButton(onClick = { onEvent(TeamEvent.ConfirmRemove) }) { Text(stringResource(R.string.team_remove_confirm)) } },
            dismissButton = { TextButton(onClick = { onEvent(TeamEvent.CancelRemove) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    state.rateFor?.let { member ->
        var cents by rememberSaveable(member.userId) { mutableStateOf(member.rateCents) }
        AlertDialog(
            onDismissRequest = { onEvent(TeamEvent.DismissRate) },
            title = { Text(stringResource(R.string.team_rate_title, member.displayName)) },
            text = { MoneyField(label = stringResource(R.string.team_rate_label), cents = cents, onCentsChange = { cents = it }) },
            confirmButton = { TextButton(onClick = { onEvent(TeamEvent.SaveRate(cents)) }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { onEvent(TeamEvent.DismissRate) }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
