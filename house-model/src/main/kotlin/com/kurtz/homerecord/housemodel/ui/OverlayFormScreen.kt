package com.kurtz.homerecord.housemodel.ui

import androidx.compose.foundation.clickable
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
 * and color), label, depth, the stage it was recorded at, and a note. Photo and linked item arrive with T052b2.
 */
@Composable
fun OverlayFormScreen(form: OverlayForm, onEvent: (WallOverlaysEvent) -> Unit) {
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
        null -> Unit
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
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
        )
        Box(
            Modifier
                .matchParentSize()
                .clickable(role = Role.Button, onClickLabel = label, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(label, value.ifEmpty { null }, error).joinToString(", ") },
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
