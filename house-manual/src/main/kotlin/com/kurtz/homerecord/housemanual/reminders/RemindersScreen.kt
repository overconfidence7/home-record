package com.kurtz.homerecord.housemanual.reminders

import com.kurtz.homerecord.coreui.components.windowTestTags
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.DateField
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SectionHeader
import com.kurtz.homerecord.housemanual.FormScreenDialog
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.reminders.ClimateZone
import com.kurtz.homerecord.housemodel.reminders.ReminderBucket
import com.kurtz.homerecord.housemodel.reminders.ReminderDraft
import com.kurtz.homerecord.housemodel.reminders.ReminderRow
import com.kurtz.homerecord.housemodel.reminders.SnoozeLength
import com.kurtz.homerecord.housemodel.reminders.labelRes
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** HM-Reminders tab (AC-H12): what is due, what comes next, what was just done; the zone chips seed seasonal tasks. */
@Composable
fun RemindersScreen(state: RemindersUiState, onEvent: (RemindersEvent) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            if (!state.loading && !state.viewOnly) {
                ExtendedFloatingActionButton(
                    onClick = { onEvent(RemindersEvent.OpenForm) },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.reminders_add)) },
                    modifier = Modifier.testTag("hm_reminders_fab"),
                )
            }
        },
    ) { padding ->
        if (state.loading) {
            LoadingState(Modifier.padding(padding))
        } else {
            LazyColumn(
                Modifier.padding(padding).fillMaxWidth().testTag("hm_reminders_list"),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                // Picking a zone seeds the home's seasonal tasks, so a viewer does not get the chips.
                if (!state.viewOnly) item(key = "zone", contentType = "zone") { ZoneChips(state.zone, onEvent) }
                if (state.rows.isEmpty()) {
                    item(key = "empty", contentType = "empty") { EmptyLine(state.zone != null, state.viewOnly) }
                }
                section(R.string.reminders_due, state.due, state, onEvent)
                section(R.string.reminders_upcoming, state.upcoming, state, onEvent)
                section(R.string.reminders_done, state.done, state, onEvent)
            }
        }
    }
    state.form?.let { form ->
        FormScreenDialog(
            title = stringResource(R.string.reminders_form_title),
            saveTag = "hm_reminders_save",
            dirty = state.formDirty,
            saveEnabled = true,
            onClose = { onEvent(RemindersEvent.CloseForm) },
            onSave = { onEvent(RemindersEvent.SaveForm) },
        ) { ReminderFormContent(form, state.showFormErrors, onEvent) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoneChips(zone: ClimateZone?, onEvent: (RemindersEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.reminders_zone_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Text(
            stringResource(if (zone == null) R.string.reminders_zone_hint else R.string.reminders_zone_picked),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClimateZone.entries.forEach { z ->
                FilterChip(
                    selected = z == zone,
                    onClick = { onEvent(RemindersEvent.PickZone(z)) },
                    label = { Text(stringResource(z.labelRes())) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hm_reminders_zone_${z.id}"),
                )
            }
        }
    }
}

@Composable
private fun EmptyLine(zonePicked: Boolean, viewOnly: Boolean = false) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp).testTag("hm_reminders_empty"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.reminders_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(
                when {
                    viewOnly -> R.string.reminders_empty_body_view_only
                    zonePicked -> R.string.reminders_empty_body
                    else -> R.string.reminders_empty_body_zone
                },
            ),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.section(title: Int, rows: List<ReminderRow>, state: RemindersUiState, onEvent: (RemindersEvent) -> Unit) {
    if (rows.isEmpty()) return
    val bucket = rows.first().bucket.name.lowercase()
    item(key = "h:$bucket", contentType = "header") {
        SectionHeader(stringResource(R.string.reminders_section, stringResource(title), rows.size), Modifier.testTag("hm_reminders_section_$bucket"))
    }
    itemsIndexed(rows, key = { _, r -> r.id }, contentType = { _, _ -> "row" }) { i, r ->
        ReminderRowItem(
            r, "hm_reminders", "${bucket}_$i", state.today, state.snoozeMenuFor == r.id,
            onEvent = { e ->
                onEvent(
                    when (e) {
                        ReminderRowEvent.Done -> RemindersEvent.Done(r.id)
                        ReminderRowEvent.OpenSnooze -> RemindersEvent.OpenSnooze(r.id)
                        ReminderRowEvent.CloseSnooze -> RemindersEvent.CloseSnooze
                        is ReminderRowEvent.Snooze -> RemindersEvent.Snooze(r.id, e.length)
                        ReminderRowEvent.Open -> RemindersEvent.OpenItem(r.itemId.orEmpty())
                    },
                )
            },
            modifier = Modifier.animateItem(),
            actions = !state.viewOnly,
        )
    }
}

/** What a reminder row's controls ask for; the screen that draws the row maps them to its own events. */
sealed interface ReminderRowEvent {
    data object Done : ReminderRowEvent
    data object OpenSnooze : ReminderRowEvent
    data object CloseSnooze : ReminderRowEvent
    data class Snooze(val length: SnoozeLength) : ReminderRowEvent
    data object Open : ReminderRowEvent
}

/**
 * One reminder row with Done and Snooze (AC-C49), shared by HM-Reminders and the item page. Tags are
 * `<prefix>_row_<tag>`, `<prefix>_done_<tag>`, `<prefix>_snooze_<tag>`, `<prefix>_snooze_week|month`. [openable] false
 * drops the tap that opens the reminder's item (the item page is already there).
 */
@Composable
fun ReminderRowItem(
    row: ReminderRow, prefix: String, tag: String, today: LocalDate, snoozeOpen: Boolean, onEvent: (ReminderRowEvent) -> Unit,
    modifier: Modifier = Modifier, openable: Boolean = true, showItemName: Boolean = true,
    /** False for a viewer on a shared home: no Done or Snooze (AC-H16). */
    actions: Boolean = true,
) {
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    val due = row.snoozedUntil?.takeIf { it.isAfter(row.dueOn) } ?: row.dueOn
    val whenText = when (row.bucket) {
        ReminderBucket.DONE -> stringResource(R.string.reminders_done_on, (row.lastDoneOn ?: today).format(formatter))
        ReminderBucket.DUE -> if (due.isBefore(today)) stringResource(R.string.reminders_overdue_since, due.format(formatter))
            else stringResource(R.string.reminders_due_on, due.format(formatter))
        ReminderBucket.UPCOMING -> if (row.snoozedUntil != null && row.snoozedUntil == due) stringResource(R.string.reminders_snoozed_until, due.format(formatter))
            else stringResource(R.string.reminders_due_on, due.format(formatter))
    }
    val repeat = row.intervalMonths?.let { repeatLabel(it) }
    val supporting = listOfNotNull(row.itemName?.takeIf { showItemName }, listOfNotNull(whenText, repeat).joinToString(stringResource(R.string.list_separator))).joinToString("\n")
    val icon = when {
        row.bucket == ReminderBucket.DONE -> Icons.Rounded.TaskAlt
        row.bucket == ReminderBucket.DUE -> Icons.Rounded.NotificationsActive
        row.intervalMonths != null -> Icons.Rounded.EventRepeat
        else -> Icons.Rounded.Event
    }
    RecordListItem(
        headline = row.title,
        supporting = supporting,
        supportingMaxLines = 6,
        headlineMaxLines = 4,
        leading = {
            Icon(
                icon, contentDescription = null,
                tint = if (row.bucket == ReminderBucket.DUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
        trailing = if (row.bucket == ReminderBucket.DONE || !actions) null else {
            {
                Row {
                    IconButton(onClick = { onEvent(ReminderRowEvent.Done) }, modifier = Modifier.size(48.dp).testTag("${prefix}_done_$tag")) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = stringResource(R.string.reminders_mark_done, row.title))
                    }
                    Box {
                        IconButton(onClick = { onEvent(ReminderRowEvent.OpenSnooze) }, modifier = Modifier.size(48.dp).testTag("${prefix}_snooze_$tag")) {
                            Icon(Icons.Rounded.Snooze, contentDescription = stringResource(R.string.reminders_snooze_label, row.title))
                        }
                        DropdownMenu(modifier = Modifier.windowTestTags(), expanded = snoozeOpen, onDismissRequest = { onEvent(ReminderRowEvent.CloseSnooze) }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reminders_snooze_week)) },
                                onClick = { onEvent(ReminderRowEvent.Snooze(SnoozeLength.WEEK)) },
                                modifier = Modifier.testTag("${prefix}_snooze_week"),
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reminders_snooze_month)) },
                                onClick = { onEvent(ReminderRowEvent.Snooze(SnoozeLength.MONTH)) },
                                modifier = Modifier.testTag("${prefix}_snooze_month"),
                            )
                        }
                    }
                }
            }
        },
        onClick = row.itemId?.takeIf { openable }?.let { { onEvent(ReminderRowEvent.Open) } },
        onClickLabel = row.itemName?.takeIf { openable }?.let { stringResource(R.string.reminders_open_item, it) },
        modifier = modifier.testTag("${prefix}_row_$tag"),
    )
}

@Composable
private fun repeatLabel(months: Int): String = when (months) {
    12 -> stringResource(R.string.reminders_repeat_year)
    1 -> stringResource(R.string.reminders_repeat_month)
    else -> stringResource(R.string.reminders_repeat_months, months)
}

/** Repeat choices on the Add reminder form; null is "Once". */
private val repeatChoices: List<Int?> = listOf(null, 1, 3, 6, 12)

/** The Add reminder form as dialog content; previewed directly because dialogs don't render in screenshot tests. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReminderFormContent(draft: ReminderDraft, showErrors: Boolean, onEvent: (RemindersEvent) -> Unit, modifier: Modifier = Modifier) {
    fun change(d: ReminderDraft) = onEvent(RemindersEvent.ChangeForm(d))
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LabeledField(
            stringResource(R.string.reminders_form_what), draft.title, { change(draft.copy(title = it)) },
            Modifier.fillMaxWidth().testTag("hm_reminders_form_title"), required = true,
            helper = stringResource(R.string.reminders_form_what_helper),
            error = if (showErrors && draft.title.isBlank()) stringResource(R.string.field_required) else null,
        )
        DateField(
            stringResource(R.string.reminders_form_due), draft.dueOn?.toString(),
            { iso -> change(draft.copy(dueOn = iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() })) },
            Modifier.testTag("hm_reminders_form_due"), required = true,
        )
        if (showErrors && draft.dueOn == null) {
            Text(stringResource(R.string.reminders_form_due_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Text(stringResource(R.string.reminders_form_repeat), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeatChoices.forEach { m ->
                FilterChip(
                    selected = draft.intervalMonths == m,
                    onClick = { change(draft.copy(intervalMonths = m)) },
                    label = { Text(if (m == null) stringResource(R.string.reminders_repeat_once) else repeatLabel(m)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hm_reminders_form_repeat_${m ?: 0}"),
                )
            }
        }
    }
}
