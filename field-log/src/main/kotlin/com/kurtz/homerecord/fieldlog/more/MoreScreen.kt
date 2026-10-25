package com.kurtz.homerecord.fieldlog.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.fieldlog.company.roleLabel

data class MoreUiState(val companyName: String, val role: CompanyRole)

sealed interface MoreEvent {
    data object OpenTeam : MoreEvent
    data object OpenTimesheets : MoreEvent
    data object OpenCompany : MoreEvent
    data object SignOut : MoreEvent
}

/** FL-More. Team and Company are shown only to Owner and Office; Crew never sees them (AC-F3). */
@Composable
fun MoreScreen(state: MoreUiState, onEvent: (MoreEvent) -> Unit, modifier: Modifier = Modifier) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Column(modifier.statusBarsPadding().verticalScroll(rememberScrollState())) {
        Text(state.companyName, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp))
        Text(
            roleLabel(state.role),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        )
        if (state.role.isAdmin) {
            RecordListItem(
                headline = stringResource(R.string.more_team),
                leading = { Icon(Icons.Rounded.Groups, contentDescription = null) },
                onClick = { onEvent(MoreEvent.OpenTeam) },
                modifier = Modifier.testTag("fl_more_team"),
            )
            RecordListItem(
                headline = stringResource(R.string.more_timesheets),
                leading = { Icon(Icons.Rounded.Schedule, contentDescription = null) },
                onClick = { onEvent(MoreEvent.OpenTimesheets) },
                modifier = Modifier.testTag("fl_more_timesheets"),
            )
            RecordListItem(
                headline = stringResource(R.string.more_company),
                leading = { Icon(Icons.Rounded.Business, contentDescription = null) },
                onClick = { onEvent(MoreEvent.OpenCompany) },
                modifier = Modifier.testTag("fl_more_company"),
            )
        }
        RecordListItem(
            headline = stringResource(R.string.more_sign_out),
            leading = { Icon(Icons.Rounded.Logout, contentDescription = null) },
            trailing = null,
            onClick = { confirmSignOut = true },
            modifier = Modifier.testTag("fl_more_sign_out"),
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out_title)) },
            text = { Text(stringResource(R.string.sign_out_body)) },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; onEvent(MoreEvent.SignOut) }, modifier = Modifier.testTag("fl_more_sign_out_confirm")) { Text(stringResource(R.string.more_sign_out)) }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
