package com.kurtz.homerecord.coreui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.kurtz.homerecord.coreui.R

/** Quick add for one or two fields: sheet with the field focused and keyboard open; Done saves (ui-standards 14). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    title: String,
    fieldLabel: String,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val submit = { if (text.isNotBlank()) onAdd(text.trim()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.widthIn(max = 640.dp).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                label = { Text(fieldLabel) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Button(onClick = submit, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.add))
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

data class SheetOption(val id: String, val label: String, val icon: ImageVector? = null)

/** Bottom sheet list of choices or row actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsSheet(
    title: String?,
    options: List<SheetOption>,
    onDismiss: () -> Unit,
    onPick: (SheetOption) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.widthIn(max = 640.dp).navigationBarsPadding()) {
            if (title != null) {
                Text(
                    title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            options.forEach { option ->
                ListItem(
                    headlineContent = { Text(option.label) },
                    leadingContent = option.icon?.let { { Icon(it, contentDescription = null) } },
                    modifier = Modifier.clickable { onPick(option) },
                )
            }
        }
    }
}
