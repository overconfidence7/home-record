package com.kurtz.homerecord.housemodel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.theme.isDarkScheme
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.overlays.OverlayKind
import com.kurtz.homerecord.housemodel.overlays.color
import com.kurtz.homerecord.housemodel.overlays.icon
import com.kurtz.homerecord.housemodel.overlays.nameRes

/** The kinds drawn on a plan, once each, in the kind list's order (the legend's rows). */
fun legendKinds(overlays: List<Overlay>): List<OverlayKind> {
    val present = overlays.mapTo(HashSet()) { it.kind }
    return OverlayKind.entries.filter { it in present }
}

/**
 * Key for the overlays drawn on a plan (judge:hm-plan-overlays-no-legend, rule 26): each kind on the floor with its
 * icon in its layer colour and its name, so a green run reads as cold water before anyone taps it. Nothing when the
 * floor has no overlays. Read only; the overlays themselves pick.
 */
@Composable
fun OverlayLegend(overlays: List<Overlay>, modifier: Modifier = Modifier) {
    val kinds = legendKinds(overlays)
    if (kinds.isEmpty()) return
    val dark = isDarkScheme()
    val prefix = LocalTagPrefix.current
    val title = stringResource(R.string.plan_legend)
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f))
            .semantics { contentDescription = title }.testTag("${prefix}_plan_legend"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(kinds, key = { it.name }) { kind ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(min = 32.dp).padding(vertical = 4.dp).testTag("${prefix}_plan_legend_${kind.name.lowercase()}"),
            ) {
                Icon(kind.icon, contentDescription = null, tint = kind.color(dark), modifier = Modifier.size(18.dp))
                Text(stringResource(kind.nameRes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
