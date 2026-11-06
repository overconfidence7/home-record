package com.kurtz.homerecord.housemodel.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.kurtz.homerecord.housemodel.overlays.OverlayItemChoice
import java.io.File
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.FormScaffold
import com.kurtz.homerecord.coreui.components.LabeledField
import com.kurtz.homerecord.coreui.components.OptionsSheet
import com.kurtz.homerecord.coreui.components.SheetOption
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.overlays.OverlayDepth
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.color
import com.kurtz.homerecord.housemodel.overlays.icon
import com.kurtz.homerecord.housemodel.overlays.nameRes

/**
 * The overlay form (AC-C26), full screen with Close and Save (rule 15): kind (required, a sheet of every kind with its icon
 * and color), label, depth, the stage it was recorded at, the item it serves (a searchable sheet of the house's items,
 * [items] in the picker's order, [linked] the chosen one), a photo from the camera or gallery when [canAddPhoto], and a note.
 * [photo] stands in for the photo file in previews.
 */
@Composable
fun OverlayFormScreen(
    form: OverlayForm,
    onEvent: (WallOverlaysEvent) -> Unit,
    items: List<OverlayItemChoice> = emptyList(),
    linked: OverlayItemChoice? = null,
    canAddPhoto: Boolean = false,
    photo: Painter? = null,
    houseHasItems: Boolean = items.isNotEmpty(),
) {
    val prefix = LocalTagPrefix.current
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    FormScaffold(
        title = stringResource(if (form.editingId == null) R.string.overlay_form_new_title else R.string.overlay_form_edit_title),
        dirty = form.dirty,
        // Save stays on so a press without a kind points at the field (rule 21).
        saveEnabled = !form.saving,
        onClose = { onEvent(WallOverlaysEvent.CloseForm) },
        onSave = { onEvent(WallOverlaysEvent.Save) },
    ) {
        Text(
            if (form.points.size >= 2) pluralStringResource(R.plurals.overlay_form_run, form.points.size, form.points.size)
            else stringResource(R.string.overlay_form_spot),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val kind = form.kind
        PickerField(
            label = stringResource(com.kurtz.homerecord.coreui.R.string.required_label, stringResource(R.string.overlay_form_kind)),
            value = kind?.let { stringResource(it.nameRes) }.orEmpty(),
            onOpen = { onEvent(WallOverlaysEvent.OpenSheet(OverlayFormSheet.KIND)) },
            error = if (form.triedSave && kind == null) stringResource(R.string.overlay_form_kind_missing) else null,
            leading = kind?.let { { Icon(it.icon, contentDescription = null, tint = it.color(dark)) } },
            modifier = Modifier.testTag("${prefix}_overlay_form_kind"),
        )
        LabeledField(
            label = stringResource(R.string.overlay_form_label),
            value = form.label,
            onValueChange = { onEvent(WallOverlaysEvent.SetLabel(it)) },
            helper = stringResource(R.string.overlay_form_label_helper),
            modifier = Modifier.testTag("${prefix}_overlay_form_label"),
        )
        PickerField(
            label = stringResource(R.string.overlay_form_depth),
            value = stringResource(form.depth.nameRes),
            onOpen = { onEvent(WallOverlaysEvent.OpenSheet(OverlayFormSheet.DEPTH)) },
            modifier = Modifier.testTag("${prefix}_overlay_form_depth"),
        )
        Column {
            Text(
                stringResource(R.string.overlay_form_stage),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
            )
            WallStagePicker(form.stage, { onEvent(WallOverlaysEvent.SetFormStage(it)) }, screen = "overlay_form")
        }
        PickerField(
            label = stringResource(R.string.overlay_form_item),
            value = linked?.name ?: stringResource(R.string.overlay_form_item_none),
            onOpen = { onEvent(WallOverlaysEvent.OpenSheet(OverlayFormSheet.ITEM)) },
            helper = stringResource(R.string.overlay_form_item_helper),
            modifier = Modifier.testTag("${prefix}_overlay_form_item"),
        )
        if (canAddPhoto || form.photoPath != null) PhotoField(form.photoPath, photo, canAddPhoto, onEvent)
        LabeledField(
            label = stringResource(R.string.overlay_form_note),
            value = form.note,
            onValueChange = { onEvent(WallOverlaysEvent.SetNote(it)) },
            singleLine = false,
            imeAction = ImeAction.Default,
            modifier = Modifier.testTag("${prefix}_overlay_form_note"),
        )
    }
    when (form.sheet) {
        OverlayFormSheet.KIND -> KindSheet(form.kind, dark, onDismiss = { onEvent(WallOverlaysEvent.OpenSheet(null)) }, onPick = { onEvent(WallOverlaysEvent.SetKind(it)) })
        OverlayFormSheet.DEPTH -> OptionsSheet(
            title = stringResource(R.string.overlay_form_depth),
            options = OverlayDepth.entries.map { SheetOption("depth_${it.name.lowercase()}", stringResource(it.nameRes)) },
            onDismiss = { onEvent(WallOverlaysEvent.OpenSheet(null)) },
            onPick = { picked -> OverlayDepth.entries.firstOrNull { "depth_${it.name.lowercase()}" == picked.id }?.let { onEvent(WallOverlaysEvent.SetDepth(it)) } },
        )
        OverlayFormSheet.PHOTO -> OptionsSheet(
            title = stringResource(R.string.overlay_form_photo),
            options = listOf(
                SheetOption("overlay_photo_take", stringResource(R.string.item_photo_take)),
                SheetOption("overlay_photo_choose", stringResource(R.string.item_photo_choose)),
            ),
            onDismiss = { onEvent(WallOverlaysEvent.OpenSheet(null)) },
            onPick = { onEvent(if (it.id == "overlay_photo_take") WallOverlaysEvent.TakePhoto else WallOverlaysEvent.ChoosePhoto) },
        )
        OverlayFormSheet.ITEM -> ItemSheet(form, items, houseHasItems, onEvent)
        null -> Unit
    }
}

/** The overlay's photo with Replace and Remove, or Add photo (AC-C26); a plain tile shows while it loads (rule 27). */
@Composable
private fun PhotoField(path: String?, preview: Painter?, canAddPhoto: Boolean, onEvent: (WallOverlaysEvent) -> Unit) {
    val prefix = LocalTagPrefix.current
    Column {
        Text(
            stringResource(R.string.overlay_form_photo),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp).semantics { heading() },
        )
        Text(
            stringResource(R.string.overlay_form_photo_helper),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (path == null) {
            OutlinedButton(
                onClick = { onEvent(WallOverlaysEvent.OpenSheet(OverlayFormSheet.PHOTO)) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_overlay_form_photo_add"),
            ) {
                Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                Text(stringResource(R.string.overlay_form_photo_add), modifier = Modifier.padding(start = 8.dp))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                OverlayPhoto(path, preview, stringResource(R.string.overlay_form_photo_description), Modifier.size(96.dp).testTag("${prefix}_overlay_form_photo"))
                Column {
                    if (canAddPhoto) {
                        TextButton(
                            onClick = { onEvent(WallOverlaysEvent.OpenSheet(OverlayFormSheet.PHOTO)) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_overlay_form_photo_replace"),
                        ) {
                            Icon(Icons.Rounded.AddAPhoto, contentDescription = null)
                            Text(stringResource(R.string.overlay_form_photo_replace), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    TextButton(
                        onClick = { onEvent(WallOverlaysEvent.RemovePhoto) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_overlay_form_photo_remove"),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Text(stringResource(R.string.overlay_form_photo_remove), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

/** An overlay photo cropped to [modifier]'s size over a plain tile with a picture icon, shown while loading or when the file is gone. */
@Composable
internal fun OverlayPhoto(path: String, preview: Painter?, description: String, modifier: Modifier) {
    Box(modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (preview != null) {
            Image(preview, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            AsyncImage(model = File(path), contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(form: OverlayForm, items: List<OverlayItemChoice>, houseHasItems: Boolean, onEvent: (WallOverlaysEvent) -> Unit) {
    ModalBottomSheet(modifier = Modifier.windowTestTags(), onDismissRequest = { onEvent(WallOverlaysEvent.OpenSheet(null)) }) {
        ItemPickList(form.itemQuery, form.itemId, items, houseHasItems, onEvent)
    }
}

/**
 * The linked-item sheet's content (AC-C26): a search field, "Nothing linked", then [items] (the wall's room first) with
 * their rooms, the chosen one marked. No items in the house says so; a query that matches nothing offers Clear search
 * (rule 11). Previews draw it.
 */
@Composable
fun ItemPickList(query: String, selectedId: String?, items: List<OverlayItemChoice>, houseHasItems: Boolean, onEvent: (WallOverlaysEvent) -> Unit) {
    val prefix = LocalTagPrefix.current
    val chosen: @Composable () -> Unit = {
        Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.overlay_form_kind_chosen), tint = MaterialTheme.colorScheme.primary)
    }
    LazyColumn(Modifier.widthIn(max = 640.dp).navigationBarsPadding().testTag("${prefix}_overlay_item_list")) {
        item(key = "title") {
            Text(
                stringResource(R.string.overlay_item_sheet_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { heading() },
            )
        }
        if (houseHasItems) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { onEvent(WallOverlaysEvent.SetItemQuery(it)) },
                    label = { Text(stringResource(R.string.overlay_item_search)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("${prefix}_overlay_item_search"),
                )
            }
        }
        item(key = "none") {
            ListItem(
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                headlineContent = { Text(stringResource(R.string.overlay_item_none)) },
                supportingContent = { Text(stringResource(R.string.overlay_item_none_supporting)) },
                leadingContent = { Icon(Icons.Rounded.LinkOff, contentDescription = null) },
                trailingContent = if (selectedId == null) chosen else null,
                modifier = Modifier.fillMaxWidth().clickable { onEvent(WallOverlaysEvent.SetItem(null)) }.testTag("${prefix}_overlay_item_none"),
            )
        }
        when {
            !houseHasItems -> item(key = "empty") {
                Text(
                    stringResource(R.string.overlay_item_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).testTag("${prefix}_overlay_item_empty"),
                )
            }
            items.isEmpty() -> item(key = "no-match") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        stringResource(R.string.overlay_item_no_match, query.trim()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { onEvent(WallOverlaysEvent.SetItemQuery("")) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_overlay_item_clear"),
                    ) { Text(stringResource(R.string.overlay_item_clear_search)) }
                }
            }
        }
        items(items, key = { it.id }) { item ->
            ListItem(
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                headlineContent = { Text(item.name) },
                supportingContent = item.roomName?.let { { Text(it) } },
                leadingContent = {
                    val icon = item.subtype?.let { com.kurtz.homerecord.core.model.ItemCategory.subtype(it)?.iconName }.orEmpty()
                    Icon(itemIcon(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingContent = if (item.id == selectedId) chosen else null,
                modifier = Modifier.fillMaxWidth().clickable { onEvent(WallOverlaysEvent.SetItem(item.id)) }.testTag("${prefix}_overlay_item_${item.id}"),
            )
        }
    }
}

/** A read-only field that opens its picker from a tap anywhere on it; the cover also gives TalkBack one click action. */
@Composable
private fun PickerField(
    label: String,
    value: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    leading: (@Composable () -> Unit)? = null,
    helper: String? = null,
) {
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            leadingIcon = leading,
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
            isError = error != null,
            supportingText = (error ?: helper)?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClickLabel = label, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(label, value.ifEmpty { null }, error ?: helper).joinToString(", ") },
        )
    }
}

/** Every kind with its icon in its layer color (AC-C25, rule 26), the chosen one marked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindSheet(selected: OverlayKind?, dark: Boolean, onDismiss: () -> Unit, onPick: (OverlayKind) -> Unit) {
    ModalBottomSheet(modifier = Modifier.windowTestTags(), onDismissRequest = onDismiss) {
        KindList(selected, dark, onPick)
    }
}

/** The kind sheet's list, scrolling so all 22 kinds stay reachable at 200% text; previews draw it. */
@Composable
fun KindList(selected: OverlayKind?, dark: Boolean, onPick: (OverlayKind) -> Unit) {
    val prefix = LocalTagPrefix.current
    LazyColumn(Modifier.widthIn(max = 640.dp).navigationBarsPadding().testTag("${prefix}_overlay_kind_list")) {
        item(key = "title") {
            Text(
                stringResource(R.string.overlay_form_kind),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        items(OverlayKind.entries, key = { it.wire }) { kind ->
            ListItem(
                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                headlineContent = { Text(stringResource(kind.nameRes)) },
                leadingContent = { Icon(kind.icon, contentDescription = null, tint = kind.color(dark)) },
                trailingContent = if (kind == selected) {
                    { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.overlay_form_kind_chosen), tint = MaterialTheme.colorScheme.primary) }
                } else null,
                modifier = Modifier.fillMaxWidth().clickable { onPick(kind) }.testTag("${prefix}_overlay_kind_${kind.wire}"),
            )
        }
    }
}
