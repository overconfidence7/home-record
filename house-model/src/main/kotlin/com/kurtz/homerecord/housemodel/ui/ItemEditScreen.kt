package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.coreui.components.MoneyField
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.components.windowTestTags
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import com.kurtz.homerecord.housemodel.items.SubtypeSearch
import com.kurtz.homerecord.housemodel.items.labelRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.kurtz.homerecord.core.model.PlateField
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import com.kurtz.homerecord.housemodel.items.DraftPhoto
import com.kurtz.homerecord.housemodel.items.PhotoKind
import java.io.File
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import com.kurtz.homerecord.core.model.ItemCategory
import com.kurtz.homerecord.coreui.components.DateField
import com.kurtz.homerecord.coreui.components.FormScaffold
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.components.OptionsSheet
import com.kurtz.homerecord.coreui.components.SheetOption
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.items.ItemField
import com.kurtz.homerecord.housemodel.items.PseudoRooms

/** HM-ItemEdit: category and subtype first, then room, details and photos (AC-C30). Data plate scan arrives in T022. */
@Composable
fun ItemEditScreen(state: ItemEditUiState, onEvent: (ItemEditEvent) -> Unit, onClose: () -> Unit) {
    if (state.loading) {
        FormScaffold(stringResource(R.string.item_edit_title_edit), dirty = false, saveEnabled = false, onClose = onClose, onSave = {}, placeholder = { LoadingState(it) }) {}
        return
    }
    if (state.missing) {
        ScreenScaffold(title = stringResource(R.string.item_edit_title_edit), onBack = onClose) { padding ->
            Text(stringResource(R.string.item_missing), Modifier.padding(padding).padding(16.dp))
        }
        return
    }
    val d = state.draft
    val errors = state.errors
    val subtype = d.subtypeId?.let(ItemCategory::subtype)
    val roomName = d.roomId?.let { id -> roomLabel(id, state) }
    FormScaffold(
        title = stringResource(if (state.isNew) R.string.item_edit_title_new else R.string.item_edit_title_edit),
        dirty = state.dirty,
        saveEnabled = !state.saving,
        onClose = onClose,
        onSave = { onEvent(ItemEditEvent.Save) },
    ) {
        PickerField(
            label = stringResource(R.string.item_type),
            value = subtype?.let { stringResource(R.string.item_type_value, stringResource(it.category.labelRes), it.label) }.orEmpty(),
            error = if (ItemField.SUBTYPE in errors) stringResource(R.string.item_type_error) else null,
            tag = "${LocalTagPrefix.current}_item_type",
            onClick = { onEvent(ItemEditEvent.OpenPicker(ItemPicker.SUBTYPE)) },
        )
        PickerField(
            label = stringResource(R.string.item_room),
            value = roomName.orEmpty(),
            error = if (ItemField.ROOM in errors) stringResource(R.string.item_room_error) else null,
            tag = "${LocalTagPrefix.current}_item_room",
            onClick = { onEvent(ItemEditEvent.OpenPicker(ItemPicker.ROOM)) },
        )
        LabeledField(
            stringResource(R.string.item_name), d.name, { onEvent(ItemEditEvent.Change(d.copy(name = it))) },
            Modifier.testTag("${LocalTagPrefix.current}_item_name"), required = true,
            error = if (ItemField.NAME in errors) stringResource(R.string.item_name_error) else null,
        )
        LabeledField(stringResource(R.string.item_make), d.make, { onEvent(ItemEditEvent.Change(d.copy(make = it))) }, Modifier.testTag("${LocalTagPrefix.current}_item_make"))
        LabeledField(stringResource(R.string.item_model), d.model, { onEvent(ItemEditEvent.Change(d.copy(model = it))) }, Modifier.testTag("${LocalTagPrefix.current}_item_model"))
        LabeledField(stringResource(R.string.item_serial), d.serial, { onEvent(ItemEditEvent.Change(d.copy(serial = it))) }, Modifier.testTag("${LocalTagPrefix.current}_item_serial"))
        DateField(
            stringResource(R.string.item_manufactured), d.manufacturedOn.ifBlank { null },
            { onEvent(ItemEditEvent.Change(d.copy(manufacturedOn = it.orEmpty()))) }, Modifier.testTag("${LocalTagPrefix.current}_item_manufactured"),
        )
        state.decodedRule?.let {
            Text(
                stringResource(R.string.item_manufactured_decoded, it), Modifier.testTag("${LocalTagPrefix.current}_item_manufactured_note"),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DateField(
            stringResource(R.string.item_purchased), d.purchasedOn.ifBlank { null },
            { onEvent(ItemEditEvent.Change(d.copy(purchasedOn = it.orEmpty()))) }, Modifier.testTag("${LocalTagPrefix.current}_item_purchased"),
        )
        DateField(
            stringResource(R.string.item_in_service), d.inServiceOn.ifBlank { null },
            { onEvent(ItemEditEvent.Change(d.copy(inServiceOn = it.orEmpty()))) }, Modifier.testTag("${LocalTagPrefix.current}_item_in_service"),
        )
        MoneyField(
            stringResource(R.string.item_price), d.price, { onEvent(ItemEditEvent.Change(d.copy(price = it))) },
            Modifier.testTag("${LocalTagPrefix.current}_item_price"),
            error = if (ItemField.PRICE in errors) stringResource(R.string.item_price_error) else null,
        )
        LabeledField(stringResource(R.string.item_vendor), d.vendor, { onEvent(ItemEditEvent.Change(d.copy(vendor = it))) }, Modifier.testTag("${LocalTagPrefix.current}_item_vendor"))
        LabeledField(
            stringResource(R.string.item_manual_url), d.manualUrl, { onEvent(ItemEditEvent.Change(d.copy(manualUrl = it))) },
            Modifier.testTag("${LocalTagPrefix.current}_item_manual_url"), keyboardType = KeyboardType.Uri,
        )
        LabeledField(
            stringResource(R.string.item_notes), d.notes, { onEvent(ItemEditEvent.Change(d.copy(notes = it))) },
            Modifier.testTag("${LocalTagPrefix.current}_item_notes"), singleLine = false,
        )
        if (state.scanning) {
            Text(stringResource(R.string.item_plate_reading), style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        PhotosSection(d.photos, onEvent)
    }
    state.plateScan?.let { scan ->
        PlateScanSheet(scan, onEvent)
    }
    state.photoSheetFor?.let { kind ->
        OptionsSheet(
            title = stringResource(if (kind == PhotoKind.DATA_PLATE) R.string.item_photo_data_plate else R.string.item_photo_item),
            options = listOf(
                SheetOption("take", stringResource(R.string.item_photo_take)),
                SheetOption("choose", stringResource(R.string.item_photo_choose)),
            ),
            onDismiss = { onEvent(ItemEditEvent.ClosePhotoSheet) },
            onPick = { onEvent(if (it.id == "take") ItemEditEvent.TakePhoto(kind) else ItemEditEvent.ChoosePhoto(kind)) },
        )
    }
    when (state.picker) {
        ItemPicker.SUBTYPE -> SubtypeSheet(d.subtypeId, { onEvent(ItemEditEvent.ClosePicker) }) { onEvent(ItemEditEvent.PickSubtype(it)) }
        ItemPicker.ROOM -> OptionsSheet(
            title = stringResource(R.string.item_room),
            options = roomOptions(state),
            onDismiss = { onEvent(ItemEditEvent.ClosePicker) },
            onPick = { onEvent(ItemEditEvent.PickRoom(it.id)) },
        )
        null -> Unit
    }
}

@Composable
private fun PhotosSection(photos: List<DraftPhoto>, onEvent: (ItemEditEvent) -> Unit) {
    Text(stringResource(R.string.item_photos), style = MaterialTheme.typography.titleSmall)
    if (photos.isNotEmpty()) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(photos, key = { it.path }) { photo ->
                val label = stringResource(if (photo.kind == PhotoKind.DATA_PLATE) R.string.item_photo_data_plate else R.string.item_photo_item)
                Box(Modifier.size(96.dp)) {
                    AsyncImage(
                        model = File(photo.path), contentDescription = label, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
                    )
                    IconButton(
                        onClick = { onEvent(ItemEditEvent.RemovePhoto(photo.path)) },
                        modifier = Modifier.align(Alignment.TopEnd).testTag("${LocalTagPrefix.current}_item_photo_remove"),
                    ) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.item_photo_remove, label))
                    }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { onEvent(ItemEditEvent.OpenPhotoSheet(PhotoKind.DATA_PLATE)) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("${LocalTagPrefix.current}_item_photo_data_plate"),
        ) { Text(stringResource(R.string.item_photo_data_plate), textAlign = TextAlign.Center) }
        OutlinedButton(
            onClick = { onEvent(ItemEditEvent.OpenPhotoSheet(PhotoKind.ITEM)) },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("${LocalTagPrefix.current}_item_photo_item"),
        ) { Text(stringResource(R.string.item_photo_item), textAlign = TextAlign.Center) }
    }
}

@Composable
private fun roomLabel(id: String, state: ItemEditUiState): String = when (id) {
    PseudoRooms.EXTERIOR -> stringResource(R.string.item_room_exterior)
    PseudoRooms.WHOLE_HOUSE -> stringResource(R.string.item_room_whole_house)
    else -> state.rooms.firstOrNull { it.id == id }?.let { stringResource(R.string.item_room_on_floor, it.name, it.floorName) }.orEmpty()
}

@Composable
private fun roomOptions(state: ItemEditUiState): List<SheetOption> =
    state.rooms.map { SheetOption(it.id, stringResource(R.string.item_room_on_floor, it.name, it.floorName)) } +
        SheetOption(PseudoRooms.EXTERIOR, stringResource(R.string.item_room_exterior)) +
        SheetOption(PseudoRooms.WHOLE_HOUSE, stringResource(R.string.item_room_whole_house))

/** Read-only field that opens a sheet; the whole field is the touch target. */
@Composable
private fun PickerField(label: String, value: String, error: String?, tag: String, onClick: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        enabled = false,
        modifier = Modifier.fillMaxWidth().testTag(tag).clickable(role = Role.Button, onClick = onClick),
        label = { Text(stringResource(com.kurtz.homerecord.coreui.R.string.required_label, label)) },
        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
        supportingText = error?.let { { Text(it) } },
        isError = error != null,
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            disabledLabelColor = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledSupportingTextColor = MaterialTheme.colorScheme.error,
        ),
    )
}

/** Subtypes grouped under their category, each category with its icon. Opens at full height so no row sits under the navigation bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtypeSheet(selectedId: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        modifier = Modifier.windowTestTags(),
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) { SubtypeList(selectedId, onPick, query) { query = it } }
}

/** The sheet's content, separate so previews can draw it (a modal sheet does not render in screenshot tests). */
@Composable
fun SubtypeList(selectedId: String?, onPick: (String) -> Unit, query: String = "", onQueryChange: (String) -> Unit = {}) {
    val resources = LocalResources.current
    val groups = remember(query, resources) { SubtypeSearch.matching(query) { resources.getString(it.labelRes) } }
    Column(Modifier.widthIn(max = 640.dp).navigationBarsPadding().imePadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp).testTag("${LocalTagPrefix.current}_item_type_search"),
            placeholder = { Text(stringResource(R.string.item_type_search)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = if (query.isEmpty()) null else {
                {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.testTag("${LocalTagPrefix.current}_item_type_search_clear")) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.item_type_search_clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        if (groups.isEmpty()) {
            Text(
                stringResource(R.string.item_type_search_none, query.trim()),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp).testTag("${LocalTagPrefix.current}_item_type_search_none"),
            )
        }
        LazyColumn(Modifier.testTag("${LocalTagPrefix.current}_item_type_sheet")) {
            groups.forEach { (category, subtypes) ->
                item(key = "h_${category.name}") {
                    ListItem(
                        headlineContent = { Text(stringResource(category.labelRes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) },
                        leadingContent = { Icon(itemIcon(category.icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    )
                }
                items(subtypes, key = { it.id }) { subtype ->
                    ListItem(
                        headlineContent = { Text(subtype.label) },
                        trailingContent = if (subtype.id == selectedId) { { Text(stringResource(R.string.item_type_selected)) } } else null,
                        modifier = Modifier.clickable { onPick(subtype.id) }.padding(start = 40.dp).testTag("${LocalTagPrefix.current}_item_type_${subtype.id}"),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlateScanSheet(scan: PlateScan, onEvent: (ItemEditEvent) -> Unit) {
    ModalBottomSheet(modifier = Modifier.windowTestTags(), onDismissRequest = { onEvent(ItemEditEvent.PlateDismiss) }) { PlateScanContent(scan, onEvent) }
}

/** Recognized lines with tap-to-assign (AC-C31); separate from the sheet so previews can draw it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlateScanContent(scan: PlateScan, onEvent: (ItemEditEvent) -> Unit) {
    LazyColumn(
        Modifier.widthIn(max = 640.dp).navigationBarsPadding().testTag("${LocalTagPrefix.current}_item_plate_sheet"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { Text(stringResource(R.string.item_plate_title), style = MaterialTheme.typography.titleMedium) }
        item { Text(stringResource(R.string.item_plate_hint), style = MaterialTheme.typography.bodyMedium) }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlateField.entries.forEach { field ->
                    val name = stringResource(plateFieldLabel(field))
                    val value = scan.valueFor(field)
                    FilterChip(
                        selected = scan.target == field,
                        onClick = { onEvent(ItemEditEvent.PlateTarget(field)) },
                        label = { Text(if (value.isBlank()) name else "$name: $value") },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("${LocalTagPrefix.current}_item_plate_${field.name.lowercase()}"),
                    )
                }
            }
        }
        itemsIndexed(scan.lines, key = { i, _ -> "line_$i" }) { _, line ->
            ListItem(
                headlineContent = { Text(line) },
                modifier = Modifier.clickable { onEvent(ItemEditEvent.PlateAssign(line)) }.heightIn(min = 48.dp).testTag("${LocalTagPrefix.current}_item_plate_line"),
            )
        }
        item {
            Button(
                onClick = { onEvent(ItemEditEvent.PlateApply) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("${LocalTagPrefix.current}_item_plate_apply"),
            ) { Text(stringResource(R.string.item_plate_apply)) }
        }
        item {
            OutlinedButton(
                onClick = { onEvent(ItemEditEvent.PlateDismiss) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("${LocalTagPrefix.current}_item_plate_skip"),
            ) { Text(stringResource(R.string.item_plate_skip)) }
        }
    }
}

private fun plateFieldLabel(field: PlateField): Int = when (field) {
    PlateField.MAKE -> R.string.item_make
    PlateField.MODEL -> R.string.item_model
    PlateField.SERIAL -> R.string.item_serial
}
