package com.kurtz.homerecord.fieldlog.files

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.kurtz.homerecord.coreui.components.RecordListItem
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.coreui.pages.PdfViewer
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * FL-File: PDF pages with a thumbnail strip and pinch zoom, or a photo, plus Share (AC-F19); Mark up for those who
 * manage files and Version history once a file has been marked up (AC-F21).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(state: FileViewerUiState, onEvent: (FileViewerEvent) -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    ScreenScaffold(
        title = state.name.ifEmpty { stringResource(R.string.file_viewer_title) },
        onBack = { onEvent(FileViewerEvent.Back) },
        actions = {
            if (state.canStartPinning) {
                IconButton(
                    onClick = { onEvent(if (state.pinMode) FileViewerEvent.CancelPinning else FileViewerEvent.StartPinning) },
                    modifier = Modifier.testTag("fl_file_pin"),
                ) {
                    Icon(
                        if (state.pinMode) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                        contentDescription = stringResource(R.string.file_viewer_pin_task),
                        tint = if (state.pinMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            }
            if (state.canMarkUp) {
                IconButton(onClick = { onEvent(FileViewerEvent.MarkUp) }, modifier = Modifier.testTag("fl_file_markup")) {
                    Icon(Icons.Rounded.Draw, contentDescription = stringResource(R.string.file_viewer_markup))
                }
            }
            if (state.versions.size > 1) {
                IconButton(onClick = { onEvent(FileViewerEvent.ShowHistory) }, modifier = Modifier.testTag("fl_file_history")) {
                    Icon(Icons.Rounded.History, contentDescription = stringResource(R.string.file_viewer_history))
                }
            }
            if (state.path.isNotEmpty() && state.problem != FileViewerProblem.NotOnPhone) {
                IconButton(onClick = { onEvent(FileViewerEvent.Share) }, modifier = Modifier.testTag("fl_file_share")) {
                    Icon(Icons.Rounded.Share, contentDescription = stringResource(R.string.file_viewer_share))
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val pages = state.pages
            when {
                state.loading -> LoadingState()
                pages != null -> Column(Modifier.fillMaxSize()) {
                    PdfViewer(
                        pages, tagPrefix = "fl_file", modifier = Modifier.weight(1f).fillMaxWidth(),
                        onPageTap = if (state.pinMode) { page, x, y -> onEvent(FileViewerEvent.TapPage(page, x, y)) } else null,
                        pageOverlay = { page -> PagePins(state.pins.filter { it.page == page }, onOpen = { onEvent(FileViewerEvent.OpenPin(it)) }) },
                    )
                    if (state.pinMode) PinHint(onCancel = { onEvent(FileViewerEvent.CancelPinning) })
                }
                else -> Problem(state.problem ?: FileViewerProblem.Missing, onEvent)
            }
        }
    }
    if (state.spot != null) {
        ModalBottomSheet(modifier = Modifier.windowTestTags(), onDismissRequest = { onEvent(FileViewerEvent.DismissChooser) }) {
            PinChooser(state.choices, onEvent)
        }
    }
    if (state.pinFailed) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(FileViewerEvent.DismissPinFailed) },
            text = { Text(stringResource(R.string.file_viewer_pin_failed)) },
            confirmButton = {
                TextButton(onClick = { onEvent(FileViewerEvent.DismissPinFailed) }, modifier = Modifier.testTag("fl_file_pin_failed_ok")) {
                    Text(stringResource(R.string.file_viewer_pin_failed_ok))
                }
            },
        )
    }
    if (state.showHistory) {
        ModalBottomSheet(modifier = Modifier.windowTestTags(), onDismissRequest = { onEvent(FileViewerEvent.HideHistory) }) {
            VersionHistory(state.versions, onEvent, zone)
        }
    }
}

/** The history sheet's list, newest first; previews and tests draw it without the sheet. */
@Composable
fun VersionHistory(versions: List<FileVersion>, onEvent: (FileViewerEvent) -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
    LazyColumn(Modifier.widthIn(max = 640.dp).navigationBarsPadding().testTag("fl_file_history_list")) {
        item(key = "title") {
            Text(
                stringResource(R.string.file_viewer_history), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(versions, key = { it.id }) { v ->
            val time = Instant.ofEpochMilli(v.createdAt).atZone(zone).format(fmt)
            ListItem(
                headlineContent = { Text(stringResource(R.string.file_viewer_version, v.version)) },
                supportingContent = { Text(if (v.author != null) stringResource(R.string.file_viewer_version_by, v.author, time) else time) },
                trailingContent = if (v.shown) {
                    { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.file_viewer_version_open)) }
                } else null,
                modifier = Modifier.clickable { onEvent(FileViewerEvent.OpenVersion(v.id)) }.testTag("fl_file_version_${v.version}"),
            )
        }
    }
}

/** The pin-mode bar under the pages: what to do next and Cancel (AC-F35). */
@Composable
private fun PinHint(onCancel: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().testTag("fl_file_pin_hint")) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.TouchApp, contentDescription = null)
            Text(
                stringResource(R.string.file_viewer_pin_hint), style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp).testTag("fl_file_pin_cancel")) {
                Text(stringResource(R.string.file_viewer_pin_cancel))
            }
        }
    }
}

/**
 * The task pins on one page, each a numbered map pin whose tip is the spot and whose 48 dp head opens the task
 * (rule 13); drawn like the pins on FL-House.
 */
@Composable
fun PagePins(pins: List<PagePin>, onOpen: (String) -> Unit) {
    if (pins.isEmpty()) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        pins.forEach { pin ->
            val label = stringResource(R.string.task_pin_label, pin.number, pin.title)
            Box(
                Modifier
                    .offset(x = w * pin.x - PIN_BOX / 2, y = h * pin.y - PIN_BOX)
                    .size(PIN_BOX)
                    .clickable(role = Role.Button, onClickLabel = label) { onOpen(pin.taskId) }
                    .semantics { contentDescription = label }
                    .testTag("fl_file_pin_${pin.taskId}"),
            ) {
                MapPin(pin.number, Modifier.matchParentSize())
            }
        }
    }
}

private val PIN_BOX = 48.dp

@Composable
private fun MapPin(number: Int, modifier: Modifier) {
    val fill = MaterialTheme.colorScheme.tertiary
    val onFill = MaterialTheme.colorScheme.onTertiary
    val outline = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelMedium
    Canvas(modifier) {
        val tip = Offset(size.width / 2, size.height)
        val head = Offset(tip.x, tip.y - 20.dp.toPx())
        val r = 12.dp.toPx()
        val body = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(head.x - r * 0.7f, head.y + r * 0.7f)
            lineTo(head.x + r * 0.7f, head.y + r * 0.7f)
            close()
        }
        drawPath(body, outline, style = Stroke(width = 4.dp.toPx()))
        drawCircle(outline, r + 2.dp.toPx(), head)
        drawPath(body, fill)
        drawCircle(fill, r, head)
        // The number keeps its size at any font scale so it stays inside the head; TalkBack reads the label.
        val text = measurer.measure(number.toString(), style.copy(color = onFill, fontSize = 12.sp), density = Density(density, 1f))
        drawText(text, topLeft = Offset(head.x - text.size.width / 2f, head.y - text.size.height / 2f))
    }
}

/** "Pin which task?": New task here, then the job's open tasks (AC-F35); previews draw it without the sheet. */
@Composable
fun PinChooser(choices: List<TaskChoice>, onEvent: (FileViewerEvent) -> Unit) {
    LazyColumn(Modifier.widthIn(max = 640.dp).navigationBarsPadding().testTag("fl_file_pin_choices")) {
        item(key = "title") {
            Text(
                stringResource(R.string.file_viewer_pin_choose), style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        item(key = "new") {
            RecordListItem(
                headline = stringResource(R.string.file_viewer_pin_new),
                leading = { Icon(Icons.Rounded.AddTask, contentDescription = null) },
                trailing = null,
                onClick = { onEvent(FileViewerEvent.NewTaskHere) },
                modifier = Modifier.testTag("fl_file_pin_new"),
            )
        }
        if (choices.isEmpty()) {
            item(key = "none") {
                Text(
                    stringResource(R.string.file_viewer_pin_none), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
        items(choices, key = { it.id }) { c ->
            RecordListItem(
                headline = c.title,
                headlineMaxLines = 4,
                supporting = if (c.pinnedHere) stringResource(R.string.file_viewer_pin_moves) else null,
                leading = { Icon(Icons.Rounded.TaskAlt, contentDescription = null) },
                trailing = null,
                onClick = { onEvent(FileViewerEvent.ChooseTask(c.id)) },
                modifier = Modifier.testTag("fl_file_pin_choice_${c.id}"),
            )
        }
    }
}

@Composable
private fun Problem(problem: FileViewerProblem, onEvent: (FileViewerEvent) -> Unit) {
    val (icon, headline, body) = when (problem) {
        FileViewerProblem.NotOnPhone -> Triple(Icons.Rounded.CloudOff, R.string.file_viewer_not_on_phone_title, R.string.file_viewer_not_on_phone_body)
        FileViewerProblem.NoPreview -> Triple(Icons.Rounded.ErrorOutline, R.string.file_viewer_no_preview_title, R.string.file_viewer_no_preview_body)
        FileViewerProblem.Unreadable -> Triple(Icons.Rounded.ErrorOutline, R.string.file_viewer_unreadable_title, R.string.file_viewer_unreadable_body)
        FileViewerProblem.Missing -> Triple(Icons.Rounded.ErrorOutline, R.string.file_viewer_missing_title, R.string.file_viewer_missing_body)
    }
    val shareable = problem == FileViewerProblem.NoPreview
    val retry = problem == FileViewerProblem.NotOnPhone
    EmptyState(
        icon = icon,
        headline = stringResource(headline),
        body = stringResource(body),
        actionLabel = stringResource(if (shareable) R.string.file_viewer_share else if (retry) R.string.file_viewer_try_again else R.string.file_viewer_back),
        onAction = { onEvent(if (shareable) FileViewerEvent.Share else if (retry) FileViewerEvent.Retry else FileViewerEvent.Back) },
        actionModifier = Modifier.testTag(if (shareable) "fl_file_share_action" else if (retry) "fl_file_retry" else "fl_file_back"),
    )
}
