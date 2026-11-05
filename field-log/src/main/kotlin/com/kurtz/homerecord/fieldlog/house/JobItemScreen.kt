package com.kurtz.homerecord.fieldlog.house

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PinDrop
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kurtz.homerecord.core.warranty.WarrantyTerms
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SectionHeader
import com.kurtz.homerecord.coreui.components.WarrantyStatusLine
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.housemodel.items.ItemDetail
import com.kurtz.homerecord.housemodel.items.PseudoRooms
import com.kurtz.homerecord.housemodel.items.WarrantyLookupRepository
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * FL-Item (AC-F50): what was installed on the job, where it is, and its warranty. Owner, Office and Lead edit, look up
 * the warranty and delete; Crew and Sub read it. Rows follow rule 9 (section headers, full-width rows).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobItemScreen(state: JobItemUiState, onEvent: (JobItemEvent) -> Unit, modifier: Modifier = Modifier) {
    val detail = state.detail
    ScreenScaffold(
        title = when {
            detail != null -> detail.item.name
            state.loading -> ""
            else -> stringResource(R.string.jobitem_missing_title)
        },
        subtitle = detail?.let { listOf(it.subtypeLabel, it.roomName).filter(String::isNotBlank).joinToString(", ") },
        onBack = { onEvent(JobItemEvent.Back) },
        actions = {
            if (detail != null && state.canEdit) {
                IconButton(onClick = { onEvent(JobItemEvent.Edit) }, modifier = Modifier.testTag("fl_jobitem_edit")) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.jobitem_edit))
                }
                IconButton(onClick = { onEvent(JobItemEvent.AskDelete) }, modifier = Modifier.testTag("fl_jobitem_delete")) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.jobitem_delete))
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            detail == null -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                headline = stringResource(R.string.jobitem_missing_title),
                body = stringResource(R.string.jobitem_missing_body),
                actionLabel = stringResource(R.string.jobitem_back),
                onAction = { onEvent(JobItemEvent.Back) },
                modifier = Modifier.padding(padding),
            )
            else -> Column(modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp)) {
                detail.photos.firstOrNull()?.let { photo ->
                    AsyncImage(
                        model = File(photo.path), contentDescription = stringResource(R.string.jobitem_photo, detail.item.name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)).testTag("fl_jobitem_photo"),
                    )
                }
                WarrantyStatusLine(detail.status, Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("fl_jobitem_status"))
                DetailsSection(detail)
                WhereSection(detail, state.canEdit, onEvent)
                WarrantySection(detail, state, onEvent)
                detail.item.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Section(stringResource(R.string.jobitem_notes)) { Inset { Text(notes, style = MaterialTheme.typography.bodyLarge) } }
                }
            }
        }
    }
    state.review?.let { terms ->
        ModalBottomSheet(modifier = Modifier.windowTestTags(), onDismissRequest = { onEvent(JobItemEvent.DiscardTerms) }) {
            JobItemReviewContent(terms, onEvent)
        }
    }
    if (state.confirmDelete && detail != null) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(JobItemEvent.CancelDelete) },
            title = { Text(stringResource(R.string.jobitem_delete_title, detail.item.name)) },
            text = { Text(stringResource(R.string.jobitem_delete_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(JobItemEvent.ConfirmDelete) }, modifier = Modifier.testTag("fl_jobitem_delete_confirm")) {
                    Text(stringResource(R.string.jobitem_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(JobItemEvent.CancelDelete) }, modifier = Modifier.testTag("fl_jobitem_delete_cancel")) {
                    Text(stringResource(R.string.jobitem_cancel))
                }
            },
        )
    }
}

@Composable
private fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title)
        content()
    }
}

@Composable
private fun Inset(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** A label and value read by TalkBack as one node; from 150% text the label sits above the value. */
@Composable
private fun Field(label: String, value: String?, tag: String) {
    if (value.isNullOrBlank()) return
    val modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 6.dp).semantics(mergeDescendants = true) {}.testTag(tag)
    val labelStyle = MaterialTheme.typography.bodyMedium
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(modifier) {
            Text(label, style = labelStyle, color = labelColor)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(0.4f), style = labelStyle, color = labelColor)
            Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DetailsSection(detail: ItemDetail) {
    val i = detail.item
    Section(stringResource(R.string.jobitem_details)) {
        if (listOf(i.make, i.model, i.serial, i.inServiceOn).all { it.isNullOrBlank() }) {
            Inset {
                Text(stringResource(R.string.jobitem_details_none), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Field(stringResource(R.string.jobitem_make), i.make, "fl_jobitem_make")
        Field(stringResource(R.string.jobitem_model), i.model, "fl_jobitem_model")
        Field(stringResource(R.string.jobitem_serial), i.serial, "fl_jobitem_serial")
        Field(stringResource(R.string.jobitem_installed), i.inServiceOn?.let(::displayDate), "fl_jobitem_installed")
    }
}

@Composable
private fun WhereSection(detail: ItemDetail, canEdit: Boolean, onEvent: (JobItemEvent) -> Unit) {
    val i = detail.item
    val pseudo = PseudoRooms.isPseudo(i.roomId) || i.roomId == null
    val placed = !pseudo && i.planX != null && i.planY != null
    val opens = canEdit && !pseudo
    Section(stringResource(R.string.jobitem_where)) {
        RecordListItem(
            headline = detail.roomName.ifBlank { stringResource(R.string.jobitem_where_no_room) },
            supporting = stringResource(
                when {
                    pseudo -> R.string.jobitem_where_off_plan
                    placed -> R.string.jobitem_where_placed
                    canEdit -> R.string.jobitem_where_not_placed
                    else -> R.string.jobitem_where_not_placed_read
                },
            ),
            leading = { Icon(if (placed) Icons.Rounded.PinDrop else Icons.Rounded.Place, contentDescription = null) },
            trailing = if (opens) ({ Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) }) else null,
            onClick = if (opens) ({ onEvent(JobItemEvent.Place) }) else null,
            onClickLabel = stringResource(if (placed) R.string.jobitem_where_move else R.string.jobitem_where_place),
            modifier = Modifier.testTag("fl_jobitem_place"),
        )
    }
}

@Composable
private fun WarrantySection(detail: ItemDetail, state: JobItemUiState, onEvent: (JobItemEvent) -> Unit) {
    val w = detail.warranty
    val awaiting = w?.confidence?.startsWith(WarrantyLookupRepository.REVIEW) == true
    val queued = w?.confidence == WarrantyLookupRepository.PENDING
    Section(stringResource(R.string.jobitem_warranty), Modifier.testTag("fl_jobitem_warranty")) {
        Inset {
            if (awaiting) {
                Text(stringResource(R.string.jobitem_warranty_review_badge), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("fl_jobitem_review_badge"))
                if (state.canEdit) {
                    Button(onClick = { onEvent(JobItemEvent.OpenReview) }, modifier = Modifier.heightIn(min = 56.dp).testTag("fl_jobitem_review")) {
                        Text(stringResource(R.string.jobitem_warranty_review))
                    }
                }
                return@Inset
            }
            if (w == null || queued) {
                Text(
                    stringResource(if (queued || state.lookupNote == JobItemLookupNote.Queued) R.string.jobitem_warranty_queued else R.string.jobitem_warranty_none),
                    style = MaterialTheme.typography.bodyLarge,
                )
                LookupNoteLine(state.lookupNote)
                if (!queued && state.canEdit) LookupButton(state.lookupBusy, R.string.jobitem_warranty_lookup, "fl_jobitem_lookup") { onEvent(JobItemEvent.LookUp(again = false)) }
                return@Inset
            }
            w.partsMonths?.let { Text(stringResource(R.string.jobitem_warranty_parts, months(it)), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("fl_jobitem_warranty_parts")) }
            w.laborMonths?.let { Text(stringResource(R.string.jobitem_warranty_labor, months(it)), style = MaterialTheme.typography.bodyLarge) }
            w.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            w.lookedUpAt?.let {
                val date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                Text(
                    stringResource(R.string.jobitem_warranty_looked_up, displayDate(date)),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            w.sourceUrl?.let {
                AssistChip(
                    onClick = { onEvent(JobItemEvent.OpenLink(it)) },
                    label = { Text(stringResource(R.string.jobitem_warranty_source)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("fl_jobitem_warranty_source"),
                )
            }
            LookupNoteLine(state.lookupNote)
            if (state.canEdit) {
                LookupButton(state.lookupBusy, R.string.jobitem_warranty_lookup_again, "fl_jobitem_lookup_again") { onEvent(JobItemEvent.LookUp(again = true)) }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(value = w.registered, role = Role.Switch, onValueChange = { onEvent(JobItemEvent.SetRegistered(it)) })
                        .testTag("fl_jobitem_registered"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.jobitem_warranty_registered), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = w.registered, onCheckedChange = null)
                }
            }
        }
    }
}

@Composable
private fun LookupNoteLine(note: JobItemLookupNote?) {
    val res = when (note) {
        JobItemLookupNote.NeedsMakeModel -> R.string.jobitem_warranty_needs_model
        JobItemLookupNote.Failed -> R.string.jobitem_warranty_failed
        JobItemLookupNote.Queued, null -> return
    }
    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("fl_jobitem_lookup_note"))
}

@Composable
private fun LookupButton(busy: Boolean, label: Int, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = !busy, modifier = Modifier.heightIn(min = 56.dp).testTag(tag)) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.jobitem_warranty_looking))
        } else {
            Text(stringResource(label))
        }
    }
}

/** Terms from the lookup with the source link: the user edits the months, saves or discards them (AC-C34). */
@Composable
fun JobItemReviewContent(terms: WarrantyTerms, onEvent: (JobItemEvent) -> Unit, modifier: Modifier = Modifier) {
    var parts by remember(terms) { mutableStateOf(terms.partsMonths?.toString().orEmpty()) }
    var labor by remember(terms) { mutableStateOf(terms.laborMonths?.toString().orEmpty()) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.jobitem_warranty_review_title), style = MaterialTheme.typography.titleLarge)
        if (terms.summary.isNotBlank()) Text(terms.summary, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            parts, { parts = it.filter(Char::isDigit).take(3) }, singleLine = true,
            label = { Text(stringResource(R.string.jobitem_warranty_parts_months)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("fl_jobitem_review_parts"),
        )
        OutlinedTextField(
            labor, { labor = it.filter(Char::isDigit).take(3) }, singleLine = true,
            label = { Text(stringResource(R.string.jobitem_warranty_labor_months)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("fl_jobitem_review_labor"),
        )
        terms.specialTerms.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        terms.sourceUrl?.let {
            AssistChip(
                onClick = { onEvent(JobItemEvent.OpenLink(it)) },
                label = { Text(stringResource(R.string.jobitem_warranty_source)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("fl_jobitem_review_source"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onEvent(JobItemEvent.ConfirmTerms(parts.toIntOrNull(), labor.toIntOrNull())) },
                modifier = Modifier.heightIn(min = 56.dp).testTag("fl_jobitem_review_save"),
            ) { Text(stringResource(R.string.jobitem_warranty_save)) }
            TextButton(onClick = { onEvent(JobItemEvent.DiscardTerms) }, modifier = Modifier.heightIn(min = 56.dp).testTag("fl_jobitem_review_discard")) {
                Text(stringResource(R.string.jobitem_warranty_discard))
            }
        }
    }
}

@Composable
private fun months(n: Int) = stringResource(R.string.jobitem_warranty_months, n)

private fun displayDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(iso)
