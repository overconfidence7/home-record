package com.kurtz.homerecord.housemanual

import androidx.compose.foundation.layout.Box
import com.kurtz.homerecord.coreui.components.windowTestTags
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** A create/edit form with more than two fields: full screen with Close and Save in the top app bar (ui-standards rule 15). */
@Composable
fun FormScreenDialog(
    title: String,
    saveTag: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().windowTestTags()) { FormScreenScaffold(title, saveTag, onClose, onSave, content) }
    }
}

/** The form's window content; previews and the QA catalog draw it without the dialog window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreenScaffold(
    title: String,
    saveTag: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("hm_close")) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.form_close))
                    }
                },
                actions = {
                    TextButton(onClick = onSave, modifier = Modifier.heightIn(min = 48.dp).testTag(saveTag)) {
                        Text(stringResource(R.string.form_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).imePadding()) { content() }
    }
}
