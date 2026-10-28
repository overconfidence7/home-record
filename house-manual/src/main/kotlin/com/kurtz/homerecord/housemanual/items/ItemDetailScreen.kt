package com.kurtz.homerecord.housemanual.items

import com.kurtz.homerecord.coreui.components.windowTestTags
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import com.kurtz.homerecord.core.warranty.WarrantyTerms
import com.kurtz.homerecord.housemodel.items.WarrantyLookupRepository
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.RecordListItem
import com.kurtz.homerecord.coreui.components.SectionHeader
import com.kurtz.homerecord.coreui.components.WarrantyStatusLine
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemodel.items.ItemDetail
import com.kurtz.homerecord.housemodel.ui.itemIcon
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Item page (AC-H7): hero photo, status line, details, warranty, photos and notes with Edit and an overflow menu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(state: ItemDetailUiState, onEvent: (ItemDetailEvent) -> Unit, modifier: Modifier = Modifier) {
    val detail = state.detail
    ScreenScaffold(
        title = detail?.item?.name ?: stringResource(R.string.item_detail_missing_title),
        subtitle = detail?.let { listOf(it.subtypeLabel, it.roomName).filter(String::isNotBlank).joinToString(", ") },
        onBack = { onEvent(ItemDetailEvent.Back) },
        actions = {
            if (detail != null) {
                IconButton(onClick = { onEvent(ItemDetailEvent.Edit) }, modifier = Modifier.testTag("hm_item_detail_edit")) {
                    Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.item_detail_edit))
                }
                OverflowMenu(onEvent)
            }
        },
    ) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            detail == null -> EmptyState(
                icon = Icons.Rounded.SearchOff,
                headline = stringResource(R.string.item_detail_missing_title),
                body = stringResource(R.string.item_detail_missing_body),
                actionLabel = stringResource(R.string.item_detail_back_to_items),
                onAction = { onEvent(ItemDetailEvent.Back) },
                modifier = Modifier.padding(padding),
            )
            else -> Column(modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 16.dp, bottom = 24.dp)) {
                Hero(detail)
                WarrantyStatusLine(detail.status, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).testTag("hm_item_detail_status"))
                DetailsSection(detail, onEvent)
                WarrantySection(detail, state, onEvent)
                RecallsSection(state.recalls, onEvent)
                ConsumablesSection(state.consumables, onEvent)
                ServiceSection(state.service, state.contacts, onEvent)
                DocumentsSection(state, onEvent)
                if (detail.photos.isNotEmpty()) PhotosRow(detail)
                detail.item.notes?.let { notes ->
                    Section(stringResource(R.string.item_detail_notes)) { Inset { Text(notes, style = MaterialTheme.typography.bodyLarge) } }
                }
            }
        }
    }
    state.review?.let { terms ->
        ModalBottomSheet(modifier = Modifier.windowTestTags(), onDismissRequest = { onEvent(ItemDetailEvent.DiscardTerms) }) {
            WarrantyReviewContent(terms, onEvent)
        }
    }
    UpkeepFormSheet(state, onEvent)
    AttachDocumentSheet(state, onEvent)
    if (state.confirmDelete && detail != null) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(ItemDetailEvent.CancelDelete) },
            title = { Text(stringResource(R.string.item_detail_delete_title, detail.item.name)) },
            text = { Text(stringResource(R.string.item_detail_delete_body)) },
            confirmButton = {
                TextButton(onClick = { onEvent(ItemDetailEvent.ConfirmDelete) }, modifier = Modifier.testTag("hm_item_detail_delete_confirm")) {
                    Text(stringResource(R.string.item_detail_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(ItemDetailEvent.CancelDelete) }) { Text(stringResource(R.string.item_detail_cancel)) }
            },
        )
    }
}

@Composable
private fun OverflowMenu(onEvent: (ItemDetailEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = Modifier.testTag("hm_item_detail_more")) {
        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.item_detail_more))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.item_detail_duplicate)) },
            onClick = { open = false; onEvent(ItemDetailEvent.Duplicate) },
            modifier = Modifier.testTag("hm_item_detail_duplicate"),
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.item_detail_delete)) },
            onClick = { open = false; onEvent(ItemDetailEvent.AskDelete) },
            modifier = Modifier.testTag("hm_item_detail_delete"),
        )
    }
}

@Composable
private fun Hero(detail: ItemDetail) {
    val shape = RoundedCornerShape(16.dp)
    val hero = detail.photos.firstOrNull()
    Box(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(180.dp).clip(shape).testTag("hm_item_detail_hero"),
        contentAlignment = Alignment.Center,
    ) {
        if (hero != null) {
            AsyncImage(
                model = File(hero.path), contentDescription = stringResource(R.string.item_detail_photo, detail.item.name),
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val icon = ItemCategory.subtype(detail.item.subtype)?.iconName.orEmpty()
                Icon(itemIcon(icon), contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** A titled group on the item page (rule 9): a section header, then full-width list rows; non-row content goes in [Inset]. */
@Composable
internal fun Section(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        SectionHeader(title)
        content()
    }
}

/** Text, notes and buttons inside a [Section], indented to line up with the list rows' content. */
@Composable
internal fun Inset(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/**
 * One detail as a compact label and value line under the Details header, read by TalkBack as one node.
 * From 150% text the label sits above the value so neither breaks mid-word.
 */
@Composable
private fun Field(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    val modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 16.dp, vertical = 6.dp).semantics(mergeDescendants = true) {}
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
private fun DetailsSection(detail: ItemDetail, onEvent: (ItemDetailEvent) -> Unit) {
    val i = detail.item
    Section(stringResource(R.string.item_detail_details)) {
        Field(stringResource(R.string.item_detail_make), i.make)
        Field(stringResource(R.string.item_detail_model), i.model)
        Field(stringResource(R.string.item_detail_serial), i.serial)
        Field(stringResource(R.string.item_detail_manufactured), i.manufacturedOn?.let(::displayDate))
        Field(stringResource(R.string.item_detail_purchased), i.purchasedOn?.let(::displayDate))
        Field(stringResource(R.string.item_detail_in_service), i.inServiceOn?.let(::displayDate))
        Field(stringResource(R.string.item_detail_price), i.priceCents?.let { NumberFormat.getCurrencyInstance(Locale.US).format(it / 100.0) })
        Field(stringResource(R.string.item_detail_vendor), i.vendor)
        i.manualUrl?.let { url ->
            RecordListItem(
                headline = stringResource(R.string.item_detail_open_manual),
                leading = { Icon(Icons.Rounded.Description, contentDescription = null) },
                trailing = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
                onClick = { onEvent(ItemDetailEvent.OpenLink(url)) },
                modifier = Modifier.testTag("hm_item_detail_manual"),
            )
        }
    }
}

@Composable
private fun WarrantySection(detail: ItemDetail, state: ItemDetailUiState, onEvent: (ItemDetailEvent) -> Unit) {
    val w = detail.warranty
    val awaiting = w?.confidence?.startsWith(WarrantyLookupRepository.REVIEW) == true
    val queued = w?.confidence == WarrantyLookupRepository.PENDING
    Section(stringResource(R.string.item_detail_warranty)) {
        Inset {
            if (awaiting) {
                Text(stringResource(R.string.item_detail_warranty_review_badge), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("hm_item_detail_review_badge"))
                Button(onClick = { onEvent(ItemDetailEvent.OpenReview) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_review")) {
                    Text(stringResource(R.string.item_detail_warranty_review))
                }
                return@Inset
            }
            if (w == null || queued) {
                Text(
                    stringResource(if (queued || state.lookupNote == LookupNote.Queued) R.string.item_detail_warranty_queued else R.string.item_detail_warranty_none),
                    style = MaterialTheme.typography.bodyLarge,
                )
                state.lookupNote?.takeIf { it != LookupNote.Queued }?.let {
                    Text(
                        stringResource(if (it == LookupNote.NeedsMakeModel) R.string.item_detail_warranty_needs_model else R.string.item_detail_warranty_failed),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("hm_item_detail_lookup_note"),
                    )
                }
                if (!queued) LookupButton(state.lookupBusy, R.string.item_detail_warranty_lookup, "hm_item_detail_lookup") { onEvent(ItemDetailEvent.LookUp(again = false)) }
                return@Inset
            }
            w.partsMonths?.let { Text(stringResource(R.string.item_detail_warranty_parts, months(it)), style = MaterialTheme.typography.bodyLarge) }
            w.laborMonths?.let { Text(stringResource(R.string.item_detail_warranty_labor, months(it)), style = MaterialTheme.typography.bodyLarge) }
            w.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            w.lookedUpAt?.let {
                val date = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                Text(
                    stringResource(R.string.item_detail_warranty_looked_up, displayDate(date)),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            w.sourceUrl?.let {
                AssistChip(
                    onClick = { onEvent(ItemDetailEvent.OpenLink(it)) },
                    label = { Text(stringResource(R.string.item_detail_warranty_source)) },
                    modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_warranty_source"),
                )
            }
            state.lookupNote?.takeIf { it != LookupNote.Queued }?.let {
                Text(
                    stringResource(if (it == LookupNote.NeedsMakeModel) R.string.item_detail_warranty_needs_model else R.string.item_detail_warranty_failed),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("hm_item_detail_lookup_note"),
                )
            }
            LookupButton(state.lookupBusy, R.string.item_detail_warranty_lookup_again, "hm_item_detail_lookup_again") { onEvent(ItemDetailEvent.LookUp(again = true)) }
            val registeredLabel = stringResource(R.string.item_detail_warranty_registered)
            // The whole row toggles, so the target is the full-width 56 dp row rather than the 32 dp switch.
            Row(
                Modifier.fillMaxWidth().height(56.dp)
                    .toggleable(value = w.registered, role = Role.Switch, onValueChange = { onEvent(ItemDetailEvent.SetRegistered(it)) })
                    .testTag("hm_item_detail_registered"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(registeredLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = w.registered, onCheckedChange = null)
            }
        }
    }
}

@Composable
private fun LookupButton(busy: Boolean, label: Int, tag: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp).testTag(tag)) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.item_detail_warranty_looking))
        } else {
            Text(stringResource(label))
        }
    }
}

/** Terms from the lookup with the source link: the user edits the months, confirms or discards (AC-C34). */
@Composable
fun WarrantyReviewContent(terms: WarrantyTerms, onEvent: (ItemDetailEvent) -> Unit, modifier: Modifier = Modifier) {
    var parts by remember(terms) { mutableStateOf(terms.partsMonths?.toString().orEmpty()) }
    var labor by remember(terms) { mutableStateOf(terms.laborMonths?.toString().orEmpty()) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.item_detail_warranty_review_title), style = MaterialTheme.typography.titleLarge)
        if (terms.summary.isNotBlank()) Text(terms.summary, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(
            parts, { parts = it.filter(Char::isDigit).take(3) }, singleLine = true,
            label = { Text(stringResource(R.string.item_detail_warranty_parts_months)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("hm_item_detail_review_parts"),
        )
        OutlinedTextField(
            labor, { labor = it.filter(Char::isDigit).take(3) }, singleLine = true,
            label = { Text(stringResource(R.string.item_detail_warranty_labor_months)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().testTag("hm_item_detail_review_labor"),
        )
        terms.specialTerms.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        Text(
            stringResource(R.string.item_detail_warranty_confidence, terms.confidence.lowercase()),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        terms.sourceUrl?.let {
            AssistChip(
                onClick = { onEvent(ItemDetailEvent.OpenLink(it)) },
                label = { Text(stringResource(R.string.item_detail_warranty_source)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_review_source"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onEvent(ItemDetailEvent.ConfirmTerms(parts.toIntOrNull(), labor.toIntOrNull())) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_review_save"),
            ) { Text(stringResource(R.string.item_detail_warranty_save)) }
            TextButton(onClick = { onEvent(ItemDetailEvent.DiscardTerms) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_review_discard")) {
                Text(stringResource(R.string.item_detail_warranty_discard))
            }
        }
    }
}

@Composable
private fun PhotosRow(detail: ItemDetail) {
    Section(stringResource(R.string.item_detail_photos)) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(detail.photos, key = { it.id }) { photo ->
                AsyncImage(
                    model = File(photo.path), contentDescription = stringResource(R.string.item_detail_photo, detail.item.name),
                    contentScale = ContentScale.Crop, modifier = Modifier.size(96.dp).clip(MaterialTheme.shapes.medium),
                )
            }
        }
    }
}

@Composable
private fun months(n: Int) = stringResource(R.string.item_detail_warranty_months, n)

private fun displayDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) }.getOrDefault(iso)

/** "Check recalls" against the CPSC feed (AC-B12). A recall that doesn't name the model is shown lower and never read as "safe". */
@Composable
private fun RecallsSection(recalls: RecallUi, onEvent: (ItemDetailEvent) -> Unit) {
    Section(stringResource(R.string.item_detail_recalls)) {
        Inset {
            val busy = recalls == RecallUi.Busy
            OutlinedButton(
                onClick = { onEvent(ItemDetailEvent.CheckRecalls) }, enabled = !busy,
                modifier = Modifier.heightIn(min = 48.dp).testTag("hm_item_detail_recalls"),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.item_detail_recalls_checking))
                } else {
                    Text(stringResource(R.string.item_detail_recalls_check))
                }
            }
            val note = when (recalls) {
                RecallUi.NeedsMake -> R.string.item_detail_recalls_needs_make
                RecallUi.Offline -> R.string.item_detail_recalls_offline
                RecallUi.Failed -> R.string.item_detail_recalls_failed
                is RecallUi.Done -> if (recalls.recalls.isEmpty()) R.string.item_detail_recalls_none else null
                else -> null
            }
            note?.let {
                val isError = recalls != RecallUi.Done(emptyList())
                Text(
                    stringResource(it), style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("hm_item_detail_recalls_note"),
                )
            }
        }
        if (recalls is RecallUi.Done) recalls.recalls.forEach { r ->
            RecordListItem(
                headline = r.title,
                overline = stringResource(if (r.modelMatch) R.string.item_detail_recall_matches else R.string.item_detail_recall_same_maker),
                supporting = listOfNotNull(r.hazard, r.remedy).joinToString("\n").ifEmpty { null },
                supportingMaxLines = 6,
                leading = { Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                trailing = r.url?.let { { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) } },
                onClick = r.url?.let { url -> { onEvent(ItemDetailEvent.OpenLink(url)) } },
                onClickLabel = r.url?.let { stringResource(R.string.item_detail_recall_open) },
                modifier = Modifier.testTag("hm_item_detail_recall_row"),
            )
        }
    }
}
