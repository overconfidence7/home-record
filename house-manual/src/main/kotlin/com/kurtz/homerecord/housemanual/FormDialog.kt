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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kurtz.homerecord.coreui.components.DiscardChangesDialog
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.window.DialogProperties

/**
 * A create/edit form with more than two fields: full screen with Close and Save in the top app bar (ui-standards rule 15).
 * When [dirty], Close and system back ask before discarding (rule 3); Save is enabled only while [saveEnabled].
 */
@Composable
fun FormScreenDialog(
    title: String,
    saveTag: String,
    dirty: Boolean,
    saveEnabled: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
    content: @Composable () -> Unit,
) {
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val requestClose = { if (dirty) confirmDiscard = true else onClose() }
    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        DialogSystemBarIcons()
        Box(Modifier.fillMaxSize().windowTestTags()) { FormScreenScaffold(title, saveTag, saveEnabled, requestClose, onSave, content) }
        if (confirmDiscard) DiscardChangesDialog(onDiscard = { confirmDiscard = false; onClose() }, onKeepEditing = { confirmDiscard = false })
    }
}

/** The dialog draws under the status bar, so its icons follow the form's surface, not the platform default (rule 34). */
@Composable
private fun DialogSystemBarIcons() {
    val view = LocalView.current
    val light = MaterialTheme.colorScheme.surface.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

/** The form's window content; previews and the QA catalog draw it without the dialog window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreenScaffold(
    title: String,
    saveTag: String,
    saveEnabled: Boolean,
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
                    TextButton(onClick = onSave, enabled = saveEnabled, modifier = Modifier.heightIn(min = 48.dp).testTag(saveTag)) {
                        Text(stringResource(R.string.form_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).imePadding()) { content() }
    }
}
