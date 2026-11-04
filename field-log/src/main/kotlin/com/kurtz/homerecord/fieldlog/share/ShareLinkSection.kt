package com.kurtz.homerecord.fieldlog.share

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.windowTestTags
import com.kurtz.homerecord.fieldlog.R

/**
 * Share link with, while a link is on, a line saying who can open it and Turn off link (AC-F28 gallery link, AC-B35).
 * [tag] is the screen's test-tag prefix (`fl_photos`, `fl_log`); [linkOn] says what the page shows.
 */
@Composable
fun ShareLinkSection(ui: ShareLinkUi, @StringRes linkOn: Int, tag: String, onEvent: (ShareLinkEvent) -> Unit, modifier: Modifier = Modifier) {
    if (!ui.allowed) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(
            onClick = { onEvent(ShareLinkEvent.Share) },
            enabled = ui.canShare,
            modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_share_link"),
        ) {
            if (ui.working) {
                CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.Link, contentDescription = null, Modifier.padding(end = 8.dp))
            }
            Text(stringResource(R.string.share_link))
        }
        if (ui.failed) {
            Text(
                stringResource(R.string.share_link_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("${tag}_link_failed"),
            )
        }
        if (ui.url != null) {
            Text(
                stringResource(linkOn),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("${tag}_link_on"),
            )
            TextButton(
                onClick = { onEvent(ShareLinkEvent.AskOff) },
                enabled = !ui.working,
                modifier = Modifier.heightIn(min = 48.dp).testTag("${tag}_link_off"),
            ) {
                Icon(Icons.Rounded.LinkOff, contentDescription = null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.share_link_off))
            }
        }
    }
    if (ui.confirmOff) {
        AlertDialog(
            modifier = Modifier.windowTestTags(),
            onDismissRequest = { onEvent(ShareLinkEvent.DismissOff) },
            title = { Text(stringResource(R.string.share_link_off_title)) },
            text = { Text(stringResource(R.string.share_link_off_body)) },
            confirmButton = {
                TextButton({ onEvent(ShareLinkEvent.TurnOff) }, Modifier.heightIn(min = 48.dp).testTag("${tag}_link_off_confirm")) {
                    Text(stringResource(R.string.share_link_off_confirm))
                }
            },
            dismissButton = {
                TextButton({ onEvent(ShareLinkEvent.DismissOff) }, Modifier.heightIn(min = 48.dp).testTag("${tag}_link_off_cancel")) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
