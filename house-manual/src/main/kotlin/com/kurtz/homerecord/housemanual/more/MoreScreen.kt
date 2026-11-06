package com.kurtz.homerecord.housemanual.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ElectricalServices
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PowerOff
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.housemanual.R

sealed interface MoreEvent {
    data object OpenPro : MoreEvent
    /** HM-Emergency (AC-C44). */
    data object OpenEmergency : MoreEvent
    data object OpenContacts : MoreEvent
    /** Breaker panels (AC-C40). */
    data object OpenPanels : MoreEvent
    data object OpenDocuments : MoreEvent
    /** Pick a `.homerecord` handoff from Files (AC-H17). */
    data object OpenImport : MoreEvent
    /** HM-Report (AC-H13). */
    data object OpenReport : MoreEvent
    /** HM-Backup (AC-C48). */
    data object OpenBackup : MoreEvent
}

/** HM-More root: Pro, Emergency, Breaker panels, Contacts, Documents, Home report, Backup and restore, Import handoff, then the account panel ([account], sign-in or the signed-in summary) filling the rest. */
@Composable
fun MoreScreen(onEvent: (MoreEvent) -> Unit, modifier: Modifier = Modifier, account: @Composable (Modifier) -> Unit) {
    Scaffold(modifier) { padding ->
        Column(Modifier.padding(padding)) {
            MoreRow(R.string.more_pro, R.string.more_pro_supporting, Icons.Outlined.WorkspacePremium, "hm_more_pro") { onEvent(MoreEvent.OpenPro) }
            MoreRow(R.string.more_emergency, R.string.more_emergency_supporting, Icons.Outlined.PowerOff, "hm_more_emergency") { onEvent(MoreEvent.OpenEmergency) }
            MoreRow(R.string.more_panels, R.string.more_panels_supporting, Icons.Outlined.ElectricalServices, "hm_more_panels") { onEvent(MoreEvent.OpenPanels) }
            MoreRow(R.string.more_contacts, R.string.more_contacts_supporting, Icons.Outlined.Contacts, "hm_more_contacts") { onEvent(MoreEvent.OpenContacts) }
            MoreRow(R.string.more_documents, R.string.more_documents_supporting, Icons.Outlined.Description, "hm_more_documents") { onEvent(MoreEvent.OpenDocuments) }
            MoreRow(R.string.more_report, R.string.more_report_supporting, Icons.Outlined.PictureAsPdf, "hm_more_report") { onEvent(MoreEvent.OpenReport) }
            MoreRow(R.string.more_backup, R.string.more_backup_supporting, Icons.Outlined.Backup, "hm_more_backup") { onEvent(MoreEvent.OpenBackup) }
            MoreRow(R.string.more_import, R.string.more_import_supporting, Icons.Outlined.FileOpen, "hm_more_import") { onEvent(MoreEvent.OpenImport) }
            account(Modifier.weight(1f))
        }
    }
}

@Composable
private fun MoreRow(headline: Int, supporting: Int, icon: ImageVector, tag: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(headline)) },
        supportingContent = { Text(stringResource(supporting)) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.heightIn(min = 56.dp).clickable(onClick = onClick).testTag(tag),
    )
}
