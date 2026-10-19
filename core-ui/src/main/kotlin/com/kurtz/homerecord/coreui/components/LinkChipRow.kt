package com.kurtz.homerecord.coreui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.R

data class LinkChip(val id: String, val label: String, val icon: ImageVector? = null)

/** Links between records as chips (AC-U7). Pass [onRemove] to show a remove X on each chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LinkChipRow(
    links: List<LinkChip>,
    onOpen: (LinkChip) -> Unit,
    modifier: Modifier = Modifier,
    onRemove: ((LinkChip) -> Unit)? = null,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        links.forEach { link ->
            AssistChip(
                onClick = { onOpen(link) },
                label = { Text(link.label) },
                leadingIcon = link.icon?.let { { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) } },
                trailingIcon = onRemove?.let {
                    {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.remove_link, link.label),
                            modifier = Modifier.size(18.dp).clickable(role = Role.Button) { it(link) },
                        )
                    }
                },
            )
        }
    }
}
