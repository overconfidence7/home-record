package com.kurtz.homerecord.housemanual.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Description
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
    data object OpenContacts : MoreEvent
    data object OpenDocuments : MoreEvent
}

/** HM-More root: Pro, Contacts, Documents, then the account panel ([account], sign-in or the signed-in summary) filling the rest. */
@Composable
fun MoreScreen(onEvent: (MoreEvent) -> Unit, modifier: Modifier = Modifier, account: @Composable (Modifier) -> Unit) {
    Scaffold(modifier) { padding ->
        Column(Modifier.padding(padding)) {
            MoreRow(R.string.more_pro, R.string.more_pro_supporting, Icons.Outlined.WorkspacePremium, "hm_more_pro") { onEvent(MoreEvent.OpenPro) }
            MoreRow(R.string.more_contacts, R.string.more_contacts_supporting, Icons.Outlined.Contacts, "hm_more_contacts") { onEvent(MoreEvent.OpenContacts) }
            MoreRow(R.string.more_documents, R.string.more_documents_supporting, Icons.Outlined.Description, "hm_more_documents") { onEvent(MoreEvent.OpenDocuments) }
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
