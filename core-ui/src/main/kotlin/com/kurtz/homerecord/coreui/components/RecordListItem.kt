package com.kurtz.homerecord.coreui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** The standard list row (AC-U3): leading icon or thumbnail, optional overline label, headline, supporting text, trailing meta or chevron. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordListItem(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    supportingMaxLines: Int = 2,
    overline: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    colors: ListItemColors = ListItemDefaults.colors(),
) {
    val click = if (onClick != null || onLongClick != null) {
        Modifier.combinedClickable(onClickLabel = onClickLabel, onClick = { onClick?.invoke() }, onLongClick = onLongClick)
    } else Modifier
    ListItem(
        headlineContent = { Text(headline, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = supporting?.let { { Text(it, maxLines = supportingMaxLines, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium) } },
        overlineContent = overline?.let { { Text(it, style = MaterialTheme.typography.labelMedium) } },
        leadingContent = leading,
        trailingContent = trailing,
        colors = colors,
        modifier = modifier.heightIn(min = 56.dp).then(click),
    )
}
