package com.kurtz.homerecord.fieldlog.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.billing.FieldLogPlan
import com.kurtz.homerecord.core.billing.FieldLogPlanKind
import com.kurtz.homerecord.core.company.CompanyRole
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.coreui.account.DeleteAccountDialog
import com.kurtz.homerecord.coreui.account.DeleteAccountError
import com.kurtz.homerecord.coreui.account.DeleteAccountEvent
import com.kurtz.homerecord.coreui.account.DeleteAccountUiState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.fieldlog.company.roleLabel
import com.kurtz.homerecord.fieldlog.settings.unitsLabel

/**
 * [unsynced] is the count of changes not yet sent, which sign-out would delete (AC-F4). [userEmail] names the account
 * signed in on this phone under the company; [units] and [versionName] are the Settings and About rows' supporting lines.
 */
data class MoreUiState(
    val companyName: String,
    val role: CompanyRole,
    val unsynced: Int = 0,
    /** The company's plan; the Plan row (FL-Paywall, AC-F56) is the Owner's only. */
    val plan: FieldLogPlan? = null,
    val userEmail: String? = null,
    val units: UnitSystem = UnitSystem.IMPERIAL,
    val versionName: String? = null,
)

sealed interface MoreEvent {
    data object OpenTeam : MoreEvent
    data object OpenTimesheets : MoreEvent
    data object OpenReports : MoreEvent
    data object OpenCompany : MoreEvent
    data object OpenPlan : MoreEvent
    data object OpenClients : MoreEvent
    data object OpenMaterials : MoreEvent
    data object OpenSync : MoreEvent
    data object OpenSettings : MoreEvent
    data object OpenAbout : MoreEvent
    data object SignOut : MoreEvent
}

/** FL-More. Team and Company are shown only to Owner and Office; Crew never sees them (AC-F3). A Lead gets Materials. */
@Composable
fun MoreScreen(
    state: MoreUiState,
    onEvent: (MoreEvent) -> Unit,
    modifier: Modifier = Modifier,
    deleteAccount: DeleteAccountUiState = DeleteAccountUiState(),
    onDeleteAccount: (DeleteAccountEvent) -> Unit = {},
) {
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Text(state.companyName, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp))
        Text(
            roleLabel(state.role),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = if (state.userEmail == null) 12.dp else 0.dp),
        )
        state.userEmail?.let { email ->
            Text(
                stringResource(R.string.more_signed_in_as, email),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).testTag("fl_more_account"),
            )
        }
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
                headline = stringResource(R.string.more_reports),
                leading = { Icon(Icons.Rounded.BarChart, contentDescription = null) },
                onClick = { onEvent(MoreEvent.OpenReports) },
                modifier = Modifier.testTag("fl_more_reports"),
            )
            RecordListItem(
                headline = stringResource(R.string.more_clients),
                leading = { Icon(Icons.Rounded.Handshake, contentDescription = null) },
                onClick = { onEvent(MoreEvent.OpenClients) },
                modifier = Modifier.testTag("fl_more_clients"),
            )
            RecordListItem(
                headline = stringResource(R.string.more_materials),
                leading = { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null) },
                onClick = { onEvent(MoreEvent.OpenMaterials) },
                modifier = Modifier.testTag("fl_more_materials"),
            )
            RecordListItem(
                headline = stringResource(R.string.more_company),
                leading = { Icon(Icons.Rounded.Business, contentDescription = null) },
                onClick = { onEvent(MoreEvent.OpenCompany) },
                modifier = Modifier.testTag("fl_more_company"),
            )
        }
        if (state.role == CompanyRole.Owner && state.plan != null) {
            RecordListItem(
                headline = stringResource(R.string.more_plan),
                supporting = planLine(state.plan),
                leading = { Icon(Icons.Rounded.Payments, contentDescription = null) },
                onClick = { onEvent(MoreEvent.OpenPlan) },
                modifier = Modifier.testTag("fl_more_plan"),
            )
        }
        if (state.role == CompanyRole.Lead) {
            // A Lead logs receipts on the jobs they run (T060c2, AC-F44).
            RecordListItem(
                headline = stringResource(R.string.more_materials),
                leading = { Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null) },
                onClick = { onEvent(MoreEvent.OpenMaterials) },
                modifier = Modifier.testTag("fl_more_materials"),
            )
        }
        RecordListItem(
            headline = stringResource(R.string.more_sync),
            supporting = if (state.unsynced > 0) pluralStringResource(R.plurals.sync_log_waiting, state.unsynced, state.unsynced)
            else stringResource(R.string.sync_log_up_to_date),
            leading = { Icon(Icons.Rounded.CloudSync, contentDescription = null) },
            onClick = { onEvent(MoreEvent.OpenSync) },
            modifier = Modifier.testTag("fl_more_sync"),
        )
        RecordListItem(
            headline = stringResource(R.string.more_settings),
            supporting = unitsLabel(state.units),
            leading = { Icon(Icons.Rounded.Settings, contentDescription = null) },
            onClick = { onEvent(MoreEvent.OpenSettings) },
            modifier = Modifier.testTag("fl_more_settings"),
        )
        RecordListItem(
            headline = stringResource(R.string.more_about),
            supporting = state.versionName?.let { stringResource(R.string.about_version_short, it) },
            leading = { Icon(Icons.Rounded.Info, contentDescription = null) },
            onClick = { onEvent(MoreEvent.OpenAbout) },
            modifier = Modifier.testTag("fl_more_about"),
        )
        RecordListItem(
            headline = stringResource(R.string.more_sign_out),
            leading = { Icon(Icons.Rounded.Logout, contentDescription = null) },
            trailing = null,
            onClick = { confirmSignOut = true },
            modifier = Modifier.testTag("fl_more_sign_out"),
        )
        // T081d: Play requires an in-app delete for apps with sign-in; below Sign out, in the error colour, behind a confirm.
        RecordListItem(
            headline = stringResource(if (deleteAccount.deleting) com.kurtz.homerecord.coreui.R.string.account_deleting else com.kurtz.homerecord.coreui.R.string.account_delete),
            supporting = stringResource(if (state.role == CompanyRole.Owner) R.string.more_delete_account_owner else R.string.more_delete_account_member),
            leading = { Icon(Icons.Rounded.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            trailing = null,
            onClick = { if (!deleteAccount.deleting) onDeleteAccount(DeleteAccountEvent.Delete) },
            modifier = Modifier.testTag("fl_more_delete_account"),
        )
        DeleteAccountError(deleteAccount, "fl_more", Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
    DeleteAccountDialog(deleteAccount, onDeleteAccount, stringResource(R.string.more_delete_account_confirm_body), "fl_more")
    if (confirmSignOut) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.sign_out_title)) },
            text = {
                Text(
                    if (state.unsynced > 0) pluralStringResource(R.plurals.sign_out_body_unsynced, state.unsynced, state.unsynced)
                    else stringResource(R.string.sign_out_body),
                    modifier = Modifier.testTag("fl_more_sign_out_body"),
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmSignOut = false; onEvent(MoreEvent.SignOut) }, modifier = Modifier.testTag("fl_more_sign_out_confirm")) { Text(stringResource(R.string.more_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }, modifier = Modifier.testTag("fl_more_sign_out_cancel")) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun planLine(plan: FieldLogPlan): String = when (plan.kind) {
    FieldLogPlanKind.Trial -> pluralStringResource(R.plurals.more_plan_trial, plan.trialDaysLeft ?: 0, plan.trialDaysLeft ?: 0)
    FieldLogPlanKind.Solo -> stringResource(R.string.plan_solo)
    FieldLogPlanKind.Crew -> stringResource(R.string.plan_crew)
    FieldLogPlanKind.Expired -> stringResource(R.string.more_plan_ended)
}
