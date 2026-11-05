package com.kurtz.homerecord.fieldlog.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.media.ByteSize
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SectionHeader
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R

/** FL-Settings: Units, Notifications, Storage on this phone (SPEC FL-More, T065a). */
@Composable
fun SettingsScreen(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, modifier: Modifier = Modifier) {
    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = { onEvent(SettingsEvent.Back) }) { padding ->
        Column(modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).testTag("fl_settings_list")) {
            SectionHeader(stringResource(R.string.settings_units))
            // Radio rows rather than a segmented row: "Feet and inches" wraps at 200% text and a segment cannot (rule 32).
            Column(Modifier.selectableGroup()) {
                UnitSystem.entries.forEach { system ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .selectable(selected = state.units == system, role = Role.RadioButton) { onEvent(SettingsEvent.SetUnits(system)) }
                            .padding(horizontal = 16.dp)
                            .testTag(if (system == UnitSystem.METRIC) "fl_settings_units_metric" else "fl_settings_units_imperial"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.units == system, onClick = null)
                        Text(unitsLabel(system), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
            Text(
                stringResource(R.string.settings_units_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            )
            SectionHeader(stringResource(R.string.settings_notifications))
            RecordListItem(
                headline = stringResource(if (state.notificationsOn) R.string.settings_notifications_on else R.string.settings_notifications_off),
                supporting = stringResource(
                    if (state.notificationsOn) R.string.settings_notifications_on_detail else R.string.settings_notifications_off_detail,
                ),
                supportingMaxLines = Int.MAX_VALUE,
                leading = {
                    Icon(if (state.notificationsOn) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff, contentDescription = null)
                },
                onClick = { onEvent(SettingsEvent.OpenNotificationSettings) },
                onClickLabel = stringResource(R.string.settings_notifications_open),
                modifier = Modifier.testTag("fl_settings_notifications"),
            )
            SectionHeader(stringResource(R.string.settings_storage))
            val s = state.storage
            if (s == null) {
                // A small inline spinner: the row's own loading state inside the scrolling list.
                val loading = stringResource(R.string.settings_storage_counting)
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 72.dp).semantics { contentDescription = loading }.testTag("fl_settings_storage_loading"),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            } else {
                RecordListItem(
                    headline = stringResource(R.string.more_storage),
                    supporting = stringResource(R.string.more_storage_detail, ByteSize.label(s.totalBytes), ByteSize.label(s.offlineBytes)),
                    // The numbers are the point of the row; at 200% they wrap instead of ending in "…" (rule 32).
                    supportingMaxLines = Int.MAX_VALUE,
                    leading = { Icon(Icons.Rounded.Storage, contentDescription = null) },
                    trailing = {
                        TextButton(
                            { onEvent(SettingsEvent.FreeUp) },
                            Modifier.heightIn(min = 48.dp).testTag("fl_more_free_up"),
                            enabled = s.cachedBytes > 0,
                        ) { Text(stringResource(R.string.more_free_up)) }
                    },
                    modifier = Modifier.testTag("fl_more_storage"),
                )
                Text(
                    stringResource(R.string.settings_storage_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 24.dp),
                )
            }
        }
    }
}

@Composable
fun unitsLabel(system: UnitSystem): String =
    stringResource(if (system == UnitSystem.METRIC) R.string.settings_units_metric else R.string.settings_units_imperial)
