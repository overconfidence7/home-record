package com.kurtz.homerecord.housemanual.items

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
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
            else -> Column(
                modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Hero(detail)
                WarrantyStatusLine(detail.status, Modifier.testTag("hm_item_detail_status"))
                DetailsCard(detail, onEvent)
                WarrantyCard(detail, onEvent)
                if (detail.photos.isNotEmpty()) PhotosRow(detail)
                detail.item.notes?.let { notes ->
                    Section(stringResource(R.string.item_detail_notes)) { Text(notes, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }
    }
    if (state.confirmDelete && detail != null) {
        AlertDialog(
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
        Modifier.fillMaxWidth().height(180.dp).clip(shape).testTag("hm_item_detail_hero"),
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

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DetailsCard(detail: ItemDetail, onEvent: (ItemDetailEvent) -> Unit) {
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
            AssistChip(
                onClick = { onEvent(ItemDetailEvent.OpenLink(url)) },
                label = { Text(stringResource(R.string.item_detail_open_manual)) },
                modifier = Modifier.testTag("hm_item_detail_manual"),
            )
        }
    }
}

@Composable
private fun WarrantyCard(detail: ItemDetail, onEvent: (ItemDetailEvent) -> Unit) {
    val w = detail.warranty
    Section(stringResource(R.string.item_detail_warranty)) {
        if (w == null) {
            Text(stringResource(R.string.item_detail_warranty_none), style = MaterialTheme.typography.bodyLarge)
            return@Section
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
                modifier = Modifier.testTag("hm_item_detail_warranty_source"),
            )
        }
        val registeredLabel = stringResource(R.string.item_detail_warranty_registered)
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(registeredLabel, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = w.registered, onCheckedChange = { onEvent(ItemDetailEvent.SetRegistered(it)) },
                modifier = Modifier.semantics { role = Role.Switch }.testTag("hm_item_detail_registered"),
            )
        }
    }
}

@Composable
private fun PhotosRow(detail: ItemDetail) {
    Section(stringResource(R.string.item_detail_photos)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
