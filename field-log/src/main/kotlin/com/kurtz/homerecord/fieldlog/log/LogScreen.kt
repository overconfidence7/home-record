package com.kurtz.homerecord.fieldlog.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kurtz.homerecord.core.log.WeatherStatus
import com.kurtz.homerecord.core.model.DelayReason
import com.kurtz.homerecord.core.model.LogLineKind
import com.kurtz.homerecord.core.model.LogWeather
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.SectionHeader
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.fieldlog.clock.WeekSheetBuilder
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The Log tab of FL-Job (SPEC FL-Log): a day bar, then the day's sections in the order a client reads them. Every
 * section has its own edit control for whoever runs the job; Crew read it (AC-F3). Typed sections open a sheet with one
 * field, a crew line a one-field dialog, a delay a two-field sheet (ui-standards rule 15).
 */
@Composable
fun LogScreen(state: LogUiState, onEvent: (LogEvent) -> Unit, modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault()) {
    Column(modifier.fillMaxSize().testTag("fl_log")) {
        DayBar(state, onEvent)
        HorizontalDivider()
        when {
            state.loading -> LoadingState(Modifier.fillMaxSize())
            state.failed -> ErrorState(stringResource(R.string.log_failed), onRetry = { onEvent(LogEvent.Retry) }, Modifier.fillMaxSize())
            !state.exists -> EmptyState(
                Icons.AutoMirrored.Rounded.EventNote,
                headline = stringResource(R.string.log_empty_title),
                body = stringResource(R.string.log_empty_body),
                actionLabel = null,
                onAction = {},
                modifier = Modifier.fillMaxSize().testTag("fl_log_empty"),
            )
            else -> Sections(state, onEvent, zone)
        }
    }
    LogEditorHost(state, onEvent)
}

@Composable
private fun DayBar(state: LogUiState, onEvent: (LogEvent) -> Unit) {
    val label = when (state.day) {
        state.today -> stringResource(R.string.log_today, state.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)))
        else -> state.day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onEvent(LogEvent.PreviousDay) }, modifier = Modifier.testTag("fl_log_prev")) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = stringResource(R.string.log_previous_day))
        }
        TextButton(
            onClick = { onEvent(LogEvent.OpenDayPicker) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("fl_log_day"),
        ) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = null, Modifier.size(20.dp))
            Text(label, Modifier.padding(start = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = { onEvent(LogEvent.NextDay) }, enabled = state.canGoForward, modifier = Modifier.testTag("fl_log_next")) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.log_next_day))
        }
    }
}

@Composable
private fun Sections(state: LogUiState, onEvent: (LogEvent) -> Unit, zone: ZoneId) {
    LazyColumn(Modifier.fillMaxSize().testTag("fl_log_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
        state.sentAt?.let { sent ->
            item(key = "sent") {
                val time = Instant.ofEpochMilli(sent).atZone(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MarkEmailRead, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.log_sent_at, time), Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        crew(state, onEvent)
        textList(
            "work", R.string.log_section_work, state.workDone, Icons.AutoMirrored.Rounded.Notes, R.string.log_work_none,
            edit = if (state.canEdit) ({ onEvent(LogEvent.EditText(LogTextSection.WORK_DONE)) }) else null,
        )
        photos(state, onEvent)
        lines("tasks", R.string.log_section_tasks, state.tasksDone, Icons.Rounded.CheckCircle, R.string.log_tasks_none, LogLineKind.TASK, state, onEvent)
        if (state.changeOrders.isNotEmpty() || (state.dropped[LogLineKind.CHANGE_ORDER] ?: 0) > 0) {
            lines("changes", R.string.log_section_changes, state.changeOrders, Icons.Rounded.ReceiptLong, R.string.log_changes_none, LogLineKind.CHANGE_ORDER, state, onEvent)
        }
        lines("materials", R.string.log_section_materials, state.materials, Icons.Rounded.LocalShipping, R.string.log_materials_none, LogLineKind.MATERIAL, state, onEvent)
        delays(state, onEvent)
        weather(state, onEvent)
        typed("visitors", R.string.log_section_visitors, state.visitors, Icons.Rounded.Groups, LogTextSection.VISITORS, state.canEdit, onEvent)
        typed("safety", R.string.log_section_safety, state.safety, Icons.Rounded.HealthAndSafety, LogTextSection.SAFETY, state.canEdit, onEvent)
        typed("client", R.string.log_section_client, state.clientNotes, Icons.AutoMirrored.Rounded.EventNote, LogTextSection.CLIENT_NOTES, state.canEdit, onEvent)
    }
}

/** A section header with its edit control on the right when this person may change it. */
@Composable
private fun Header(title: String, key: String, editLabel: String? = null, onEdit: (() -> Unit)? = null, supporting: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            // the pencil drops by the header's top padding so it lines up with the title, not above it
            SectionHeader(title, Modifier.weight(1f))
            if (onEdit != null && editLabel != null) {
                IconButton(onClick = onEdit, modifier = Modifier.padding(top = 12.dp).testTag("fl_log_${key}_edit")) {
                    Icon(Icons.Rounded.Edit, contentDescription = editLabel)
                }
            }
        }
        if (supporting != null) {
            Text(
                supporting, Modifier.padding(horizontal = 16.dp).testTag("fl_log_${key}_summary"),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoneLine(text: Int) {
    Text(
        stringResource(text), Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Line(icon: ImageVector, headline: String, modifier: Modifier = Modifier, supporting: String? = null, trailing: (@Composable () -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(headline, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = supporting?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = trailing,
        modifier = modifier.heightIn(min = 56.dp),
    )
}

private fun LazyListScope.crew(state: LogUiState, onEvent: (LogEvent) -> Unit) {
    item(key = "crew-h") {
        Header(
            stringResource(R.string.log_section_crew), "crew",
            supporting = if (state.crew.isEmpty()) null else pluralStringResource(
                R.plurals.log_crew_summary, state.crew.size, state.crew.size, WeekSheetBuilder.hm(state.totalMinutes * 60_000L),
            ),
        )
    }
    if (state.crew.isEmpty()) item(key = "crew-none") { NoneLine(R.string.log_crew_none) }
    items(state.crew, key = { "crew-${it.memberId}" }) { row ->
        val hours = WeekSheetBuilder.hm(row.minutes * 60_000L)
        val editLabel = stringResource(R.string.log_crew_edit, row.name)
        Line(
            Icons.Rounded.Person, row.name,
            supporting = if (row.onSiteNow) stringResource(R.string.log_crew_on_site, hours) else hours,
            modifier = if (state.canEdit) Modifier.clickable(onClickLabel = editLabel) { onEvent(LogEvent.EditHours(row.memberId)) }
                .testTag("fl_log_crew_${row.memberId}") else Modifier.testTag("fl_log_crew_${row.memberId}"),
            trailing = if (state.canEdit) ({ Icon(Icons.Rounded.Edit, contentDescription = null) }) else null,
        )
    }
}

private fun LazyListScope.textList(key: String, title: Int, entries: List<String>, icon: ImageVector, none: Int, edit: (() -> Unit)?) {
    item(key = "$key-h") {
        val t = stringResource(title)
        Header(t, key, stringResource(R.string.log_edit_section, t), edit)
    }
    if (entries.isEmpty()) item(key = "$key-none") { NoneLine(none) }
    itemsIndexed(entries, key = { i, _ -> "$key-$i" }) { _, text -> Line(icon, text) }
}

/** Record lines a lead can leave out of the log one by one (AC-F30); the records themselves stay on the job. */
private fun LazyListScope.lines(
    key: String, title: Int, lines: List<LogLine>, icon: ImageVector, none: Int, kind: LogLineKind, state: LogUiState, onEvent: (LogEvent) -> Unit,
) {
    item(key = "$key-h") { Header(stringResource(title), key) }
    if (lines.isEmpty()) item(key = "$key-none") { NoneLine(none) }
    items(lines, key = { "$key-${it.id}" }) { line ->
        Line(
            icon, line.headline, Modifier.testTag("fl_log_${key}_${line.id}"), supporting = line.supporting,
            trailing = if (state.canEdit) ({
                IconButton(onClick = { onEvent(LogEvent.AskDrop(kind, line.id)) }, modifier = Modifier.testTag("fl_log_${key}_drop_${line.id}")) {
                    Icon(Icons.Rounded.RemoveCircleOutline, contentDescription = stringResource(R.string.log_drop_line, line.headline))
                }
            }) else null,
        )
    }
    putBack(key, kind, state, onEvent)
}

/** "Put back 2 left out" under a section once something was dropped from it. */
private fun LazyListScope.putBack(key: String, kind: LogLineKind, state: LogUiState, onEvent: (LogEvent) -> Unit) {
    val count = state.dropped[kind] ?: 0
    if (!state.canEdit || count == 0) return
    item(key = "$key-put-back") {
        TextButton(
            onClick = { onEvent(LogEvent.PutBack(kind)) },
            modifier = Modifier.padding(horizontal = 8.dp).heightIn(min = 48.dp).testTag("fl_log_${key}_put_back"),
        ) {
            Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null, Modifier.size(18.dp))
            Text(pluralStringResource(R.plurals.log_put_back, count, count), Modifier.padding(start = 8.dp))
        }
    }
}

private fun LazyListScope.photos(state: LogUiState, onEvent: (LogEvent) -> Unit) {
    item(key = "photos-h") {
        Header(
            stringResource(R.string.log_section_photos), "photos",
            supporting = if (state.photos.isEmpty()) null else pluralStringResource(R.plurals.log_photo_count, state.photos.size, state.photos.size),
        )
    }
    if (state.photos.isEmpty()) {
        item(key = "photos-none") { NoneLine(R.string.log_photos_none) }
    } else {
        item(key = "photos-row") {
            LazyRow(
                Modifier.fillMaxWidth().testTag("fl_log_photos"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.photos, key = { _, p -> p.id }) { index, photo ->
                    val shape = RoundedCornerShape(8.dp)
                    Box(Modifier.size(72.dp)) {
                    if (photo.path.isEmpty()) {
                        Icon(
                            Icons.Rounded.PhotoLibrary, contentDescription = stringResource(R.string.photos_not_here),
                            Modifier.size(72.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        AsyncImage(
                            model = File(photo.path), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(72.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        )
                    }
                    if (state.canEdit) {
                        // a small badge on a 48 dp target in the corner, readable over any photo
                        IconButton(
                            onClick = { onEvent(LogEvent.AskDrop(LogLineKind.PHOTO, photo.id)) },
                            modifier = Modifier.align(Alignment.TopEnd).testTag("fl_log_photos_drop_${photo.id}"),
                        ) {
                            Icon(
                                Icons.Rounded.Close, contentDescription = stringResource(R.string.log_drop_photo, index + 1),
                                Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(3.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                    }
                }
            }
        }
    }
    putBack("photos", LogLineKind.PHOTO, state, onEvent)
}

private fun DelayReason.icon(): ImageVector = when (this) {
    DelayReason.WEATHER -> Icons.Rounded.Umbrella
    DelayReason.MATERIALS -> Icons.Rounded.LocalShipping
    DelayReason.INSPECTION -> Icons.Rounded.FactCheck
    DelayReason.CLIENT -> Icons.Rounded.Person
    DelayReason.OTHER -> Icons.Rounded.MoreHoriz
}

fun DelayReason.label(): Int = when (this) {
    DelayReason.WEATHER -> R.string.log_delay_weather
    DelayReason.MATERIALS -> R.string.log_delay_materials
    DelayReason.INSPECTION -> R.string.log_delay_inspection
    DelayReason.CLIENT -> R.string.log_delay_client
    DelayReason.OTHER -> R.string.log_delay_other
}

private fun LazyListScope.delays(state: LogUiState, onEvent: (LogEvent) -> Unit) {
    item(key = "delays-h") { Header(stringResource(R.string.log_section_delays), "delays") }
    if (state.delays.isEmpty()) item(key = "delays-none") { NoneLine(R.string.log_delays_none) }
    itemsIndexed(state.delays, key = { i, _ -> "delay-$i" }) { i, delay ->
        val reason = stringResource(delay.reason.label())
        Line(
            delay.reason.icon(), reason, Modifier.testTag("fl_log_delay_$i"), delay.note.ifBlank { null },
            trailing = if (state.canEdit) ({
                IconButton(onClick = { onEvent(LogEvent.AskRemoveDelay(i)) }, modifier = Modifier.testTag("fl_log_delay_remove_$i")) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.log_delay_remove, reason))
                }
            }) else null,
        )
    }
    if (state.canEdit) {
        item(key = "delays-add") {
            OutlinedButton(
                onClick = { onEvent(LogEvent.AddDelay) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 48.dp).testTag("fl_log_delay_add"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                Text(stringResource(R.string.log_delay_add), Modifier.padding(start = 8.dp))
            }
        }
    }
}

internal fun conditionsIcon(key: String): ImageVector = when (key) {
    "clear" -> Icons.Rounded.WbSunny
    "partly_cloudy" -> Icons.Rounded.WbCloudy
    "drizzle" -> Icons.Rounded.Grain
    "rain", "showers" -> Icons.Rounded.Umbrella
    "freezing_rain", "snow" -> Icons.Rounded.AcUnit
    "thunderstorm" -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

internal fun conditionsLabel(key: String): Int = when (key) {
    "clear" -> R.string.weather_clear
    "partly_cloudy" -> R.string.weather_partly_cloudy
    "cloudy" -> R.string.weather_cloudy
    "fog" -> R.string.weather_fog
    "drizzle" -> R.string.weather_drizzle
    "rain" -> R.string.weather_rain
    "freezing_rain" -> R.string.weather_freezing_rain
    "snow" -> R.string.weather_snow
    "showers" -> R.string.weather_showers
    "thunderstorm" -> R.string.weather_thunderstorm
    else -> R.string.weather_unknown
}

private fun LazyListScope.weather(state: LogUiState, onEvent: (LogEvent) -> Unit) {
    item(key = "weather-h") {
        val t = stringResource(R.string.log_section_weather)
        Header(t, "weather", stringResource(R.string.log_edit_section, t), if (state.canEdit) ({ onEvent(LogEvent.EditWeather) }) else null)
    }
    item(key = "weather") {
        val w: LogWeather? = state.weather
        if (w != null) {
            val headline = listOfNotNull(
                stringResource(conditionsLabel(w.conditions)),
                if (w.highF != null && w.lowF != null) stringResource(R.string.weather_high_low, w.highF ?: 0, w.lowF ?: 0) else null,
            ).joinToString(", ")
            val precip = w.precipIn?.takeIf { it > 0 }?.let { stringResource(R.string.weather_precip, "%.2f".format(java.util.Locale.US, it)) }
            val supporting = listOfNotNull(precip, if (state.weatherEdited) stringResource(R.string.log_weather_typed) else null).joinToString(". ").ifEmpty { null }
            Line(conditionsIcon(w.conditions), headline, Modifier.testTag("fl_log_weather"), supporting)
        } else {
            val text = when (state.weatherStatus) {
                WeatherStatus.NoLocation -> R.string.log_weather_no_location
                WeatherStatus.NotYet -> R.string.log_weather_not_yet
                WeatherStatus.Offline -> R.string.log_weather_offline
                else -> R.string.log_weather_none
            }
            Line(Icons.Rounded.CloudOff, stringResource(text), modifier = Modifier.testTag("fl_log_weather_none"))
        }
    }
}

private fun LazyListScope.typed(key: String, title: Int, text: String, icon: ImageVector, section: LogTextSection, canEdit: Boolean, onEvent: (LogEvent) -> Unit) {
    item(key = "$key-h") {
        val t = stringResource(title)
        Header(t, key, stringResource(R.string.log_edit_section, t), if (canEdit) ({ onEvent(LogEvent.EditText(section)) }) else null)
    }
    item(key = key) {
        if (text.isBlank()) NoneLine(R.string.log_typed_none) else Line(icon, text, modifier = Modifier.testTag("fl_log_${key}_text"))
    }
}

/** The open sheet or dialog, if any. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogEditorHost(state: LogUiState, onEvent: (LogEvent) -> Unit) {
    val dismiss = { onEvent(LogEvent.DismissEditor) }
    when (val e = state.editor) {
        null -> Unit
        is LogEditor.Text, is LogEditor.Delay -> ModalBottomSheet(
            onDismissRequest = dismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            modifier = Modifier.windowTestTags(),
        ) {
            if (e is LogEditor.Text) LogTextSheetContent(e, onEvent) else LogDelaySheetContent(e as LogEditor.Delay, onEvent)
        }
        is LogEditor.Hours -> AlertDialog(
            onDismissRequest = dismiss,
            modifier = Modifier.windowTestTags(),
            title = { Text(stringResource(R.string.log_hours_title, e.name)) },
            text = {
                val focus = remember { FocusRequester() }
                OutlinedTextField(
                    value = e.text,
                    onValueChange = { onEvent(LogEvent.EditorText(it)) },
                    label = { Text(stringResource(R.string.log_hours_label)) },
                    singleLine = true,
                    isError = state.hoursInvalid,
                    supportingText = { Text(stringResource(if (state.hoursInvalid) R.string.log_hours_error else R.string.log_hours_help)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("fl_log_hours_field"),
                )
                LaunchedEffect(Unit) { focus.requestFocus() }
            },
            confirmButton = {
                TextButton(onClick = { onEvent(LogEvent.SaveEditor) }, modifier = Modifier.testTag("fl_log_hours_save")) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
        )
        is LogEditor.RemoveDelay -> {
            val delay = state.delays.getOrNull(e.index)
            AlertDialog(
                onDismissRequest = dismiss,
                modifier = Modifier.windowTestTags(),
                title = { Text(stringResource(R.string.log_delay_remove_title)) },
                text = {
                    Text(
                        if (delay == null) "" else listOf(stringResource(delay.reason.label()), delay.note).filter { it.isNotBlank() }.joinToString(": "),
                    )
                },
                confirmButton = {
                    TextButton(onClick = { onEvent(LogEvent.SaveEditor) }, modifier = Modifier.testTag("fl_log_delay_remove_confirm")) {
                        Text(stringResource(R.string.log_delay_remove_confirm))
                    }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
            )
        }
        is LogEditor.Drop -> AlertDialog(
            onDismissRequest = dismiss,
            modifier = Modifier.windowTestTags(),
            title = { Text(stringResource(R.string.log_drop_title)) },
            text = {
                Text(
                    if (e.label == null) stringResource(R.string.log_drop_photo_body)
                    else stringResource(R.string.log_drop_body, e.label),
                )
            },
            confirmButton = {
                TextButton(onClick = { onEvent(LogEvent.SaveEditor) }, modifier = Modifier.testTag("fl_log_drop_confirm")) {
                    Text(stringResource(R.string.log_drop_confirm))
                }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
        )
        LogEditor.PickDay -> {
            val todayMillis = state.today.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val picker = rememberDatePickerState(
                initialSelectedDateMillis = state.day.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                    override fun isSelectableYear(year: Int) = year <= state.today.year
                },
            )
            DatePickerDialog(
                onDismissRequest = dismiss,
                modifier = Modifier.windowTestTags(),
                confirmButton = {
                    TextButton(
                        onClick = {
                            val picked = picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                            if (picked != null) onEvent(LogEvent.PickDay(picked)) else dismiss()
                        },
                        modifier = Modifier.testTag("fl_log_day_ok"),
                    ) { Text(stringResource(com.kurtz.homerecord.coreui.R.string.ok)) }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
            ) { DatePicker(picker) }
        }
    }
}

private fun LogTextSection.title(): Int = when (this) {
    LogTextSection.WORK_DONE -> R.string.log_section_work
    LogTextSection.VISITORS -> R.string.log_section_visitors
    LogTextSection.SAFETY -> R.string.log_section_safety
    LogTextSection.CLIENT_NOTES -> R.string.log_section_client
}

private fun LogTextSection.help(): Int = when (this) {
    LogTextSection.WORK_DONE -> R.string.log_work_help
    LogTextSection.VISITORS -> R.string.log_visitors_help
    LogTextSection.SAFETY -> R.string.log_safety_help
    LogTextSection.CLIENT_NOTES -> R.string.log_client_help
}

/** One text field for a typed section; previews draw it without the sheet. */
@Composable
fun LogTextSheetContent(editor: LogEditor.Text, onEvent: (LogEvent) -> Unit) {
    val focus = remember { FocusRequester() }
    val title = stringResource(editor.section.title())
    SheetColumn {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        OutlinedTextField(
            value = editor.text,
            onValueChange = { onEvent(LogEvent.EditorText(it)) },
            label = { Text(title) },
            supportingText = { Text(stringResource(editor.section.help())) },
            minLines = 3,
            maxLines = 8,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("fl_log_text_field"),
        )
        Button(onClick = { onEvent(LogEvent.SaveEditor) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("fl_log_text_save")) {
            Text(stringResource(R.string.save))
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Reason (five, so a list rather than segments, rule 22) and an optional note. */
@Composable
fun LogDelaySheetContent(editor: LogEditor.Delay, onEvent: (LogEvent) -> Unit) {
    SheetColumn {
        Text(stringResource(R.string.log_delay_add), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Column(Modifier.selectableGroup()) {
            DelayReason.entries.forEach { reason ->
                val selected = editor.reason == reason
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .selectable(selected = selected, onClick = { onEvent(LogEvent.EditorReason(reason)) }, role = Role.RadioButton)
                        .testTag("fl_log_delay_reason_${reason.name.lowercase()}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Icon(reason.icon(), contentDescription = null, Modifier.padding(start = 16.dp))
                    Text(stringResource(reason.label()), Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        OutlinedTextField(
            value = editor.note,
            onValueChange = { onEvent(LogEvent.EditorText(it)) },
            label = { Text(stringResource(R.string.log_delay_note)) },
            supportingText = { Text(stringResource(R.string.log_delay_note_help)) },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth().testTag("fl_log_delay_note"),
        )
        Button(
            onClick = { onEvent(LogEvent.SaveEditor) }, enabled = editor.reason != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("fl_log_delay_save"),
        ) { Text(stringResource(R.string.log_delay_save)) }
    }
}

@Composable
private fun SheetColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}
