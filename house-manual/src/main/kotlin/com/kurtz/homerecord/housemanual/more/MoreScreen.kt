package com.kurtz.homerecord.housemanual.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PowerOff
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.reminders.ClimateZone
import com.kurtz.homerecord.housemodel.reminders.labelRes

sealed interface MoreEvent {
    data object OpenPro : MoreEvent
    /** HM-Emergency (AC-C44). */
    data object OpenEmergency : MoreEvent
    data object OpenContacts : MoreEvent
    /** Breaker panels (AC-C40). */
    data object OpenPanels : MoreEvent
    data object OpenDocuments : MoreEvent
    /** Pick a `.homerecord` handoff from Files (AC-H17). */
    data object OpenImport : MoreEvent
    /** HM-Report (AC-H13). */
    data object OpenReport : MoreEvent
    /** HM-Backup (AC-C48). */
    data object OpenBackup : MoreEvent
    /** HM-Account: sign in, sync, sign out (AC-H15). */
    data object OpenAccount : MoreEvent
    /** HM-Share (AC-H16). */
    data object OpenShare : MoreEvent
    /** HM-About: version and what House Manual is. */
    data object OpenAbout : MoreEvent
    /** Units picker (AC-C4). */
    data object OpenUnits : MoreEvent
    data class SetUnits(val system: UnitSystem) : MoreEvent
    /** Climate zone picker (AC-H12); owner and family only. */
    data object OpenZone : MoreEvent
    data class PickZone(val zone: ClimateZone) : MoreEvent
    data object DismissDialog : MoreEvent
}

/**
 * HM-More root (SPEC HM-More, T077): Emergency, Breaker panels, Documents, Contacts, Home report, Import, Backup and
 * restore, Account and sync, Share this house, Units, Climate zone, Pro and About as `ListItem` rows with icons (AC-U3),
 * one scrolling list so every row is reachable at 200% text. Units and Climate zone open a picker over the list.
 */
@Composable
fun MoreScreen(state: MoreUiState, onEvent: (MoreEvent) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            MoreRow(R.string.more_emergency, stringResource(R.string.more_emergency_supporting), Icons.Outlined.PowerOff, "hm_more_emergency") { onEvent(MoreEvent.OpenEmergency) }
            MoreRow(R.string.more_panels, stringResource(R.string.more_panels_supporting), Icons.Outlined.ElectricalServices, "hm_more_panels") { onEvent(MoreEvent.OpenPanels) }
            MoreRow(R.string.more_documents, stringResource(R.string.more_documents_supporting), Icons.Outlined.Description, "hm_more_documents") { onEvent(MoreEvent.OpenDocuments) }
            MoreRow(R.string.more_contacts, stringResource(R.string.more_contacts_supporting), Icons.Outlined.Contacts, "hm_more_contacts") { onEvent(MoreEvent.OpenContacts) }
            MoreRow(R.string.more_report, stringResource(R.string.more_report_supporting), Icons.Outlined.PictureAsPdf, "hm_more_report") { onEvent(MoreEvent.OpenReport) }
            MoreRow(R.string.more_import, stringResource(R.string.more_import_supporting), Icons.Outlined.FileOpen, "hm_more_import") { onEvent(MoreEvent.OpenImport) }
            MoreRow(R.string.more_backup, stringResource(R.string.more_backup_supporting), Icons.Outlined.Backup, "hm_more_backup") { onEvent(MoreEvent.OpenBackup) }
            MoreRow(R.string.more_account, stringResource(R.string.more_account_supporting), Icons.Outlined.CloudSync, "hm_more_account") { onEvent(MoreEvent.OpenAccount) }
            MoreRow(R.string.more_share, stringResource(R.string.more_share_supporting), Icons.Outlined.GroupAdd, "hm_more_share") { onEvent(MoreEvent.OpenShare) }
            MoreRow(R.string.more_units, unitsLabel(state.units), Icons.Outlined.Straighten, "hm_more_units") { onEvent(MoreEvent.OpenUnits) }
            MoreRow(
                R.string.more_zone,
                when {
                    state.zone != null && state.canEdit -> stringResource(state.zone.labelRes())
                    state.zone != null -> stringResource(R.string.more_zone_view_only, stringResource(state.zone.labelRes()))
                    state.canEdit -> stringResource(R.string.more_zone_none)
                    else -> stringResource(R.string.more_zone_none_view_only)
                },
                Icons.Outlined.Thermostat, "hm_more_zone",
                onClick = if (state.canEdit) ({ onEvent(MoreEvent.OpenZone) }) else null,
            )
            MoreRow(R.string.more_pro, stringResource(R.string.more_pro_supporting), Icons.Outlined.WorkspacePremium, "hm_more_pro") { onEvent(MoreEvent.OpenPro) }
            MoreRow(R.string.more_about, stringResource(R.string.more_about_supporting), Icons.Outlined.Info, "hm_more_about") { onEvent(MoreEvent.OpenAbout) }
        }
    }
    when (state.dialog) {
        MoreDialog.UNITS -> PickerDialog(
            title = R.string.more_units,
            body = R.string.more_units_help,
            options = UnitSystem.entries.map { system ->
                Option(unitsLabel(system), system == state.units, if (system == UnitSystem.METRIC) "hm_more_units_metric" else "hm_more_units_imperial") {
                    onEvent(MoreEvent.SetUnits(system))
                }
            },
            onDismiss = { onEvent(MoreEvent.DismissDialog) },
        )
        MoreDialog.ZONE -> PickerDialog(
            title = R.string.more_zone,
            body = if (state.zone == null) R.string.reminders_zone_hint else R.string.reminders_zone_picked,
            options = ClimateZone.entries.map { zone ->
                Option(stringResource(zone.labelRes()), zone == state.zone, "hm_more_zone_${zone.id}") { onEvent(MoreEvent.PickZone(zone)) }
            },
            onDismiss = { onEvent(MoreEvent.DismissDialog) },
        )
        null -> Unit
    }
}

@Composable
fun unitsLabel(system: UnitSystem): String =
    stringResource(if (system == UnitSystem.METRIC) R.string.more_units_metric else R.string.more_units_imperial)

private class Option(val label: String, val selected: Boolean, val tag: String, val onPick: () -> Unit)

/** Radio rows rather than a segmented row: "Feet and inches" and zone names wrap at 200% text (rule 32). */
@Composable
private fun PickerDialog(title: Int, body: Int, options: List<Option>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.padding(top = 8.dp).selectableGroup()) {
                    options.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = option.selected, role = Role.RadioButton, onClick = option.onPick)
                                .testTag(option.tag),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option.selected, onClick = null)
                            Text(option.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_more_dialog_close")) {
                Text(stringResource(R.string.more_dialog_close))
            }
        },
    )
}

@Composable
private fun MoreRow(headline: Int, supporting: String, icon: ImageVector, tag: String, onClick: (() -> Unit)?) {
    ListItem(
        headlineContent = { Text(stringResource(headline)) },
        supportingContent = { Text(supporting) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.heightIn(min = 56.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).testTag(tag),
    )
}
