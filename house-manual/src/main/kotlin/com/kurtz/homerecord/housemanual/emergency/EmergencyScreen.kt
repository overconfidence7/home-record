package com.kurtz.homerecord.housemanual.emergency

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PowerOff
import androidx.compose.material.icons.rounded.Water
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import coil3.compose.AsyncImage
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.ShutoffGroup
import com.kurtz.homerecord.core.model.ShutoffGroups
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R
import java.io.File

/**
 * HM-Emergency (AC-C44): every shutoff in large type, grouped Water / Gas / Electrical / HVAC / Other with mains first,
 * 24 sp instructions and full-width photos that pinch-zoom; "Manage shutoffs" at the bottom. Reads only local data.
 * [previewPhoto] stands in for each shutoff photo in previews and screenshot tests, which cannot load files.
 */
@Composable
fun EmergencyScreen(state: EmergencyUiState, onEvent: (EmergencyEvent) -> Unit, previewPhoto: Painter? = null) {
    ScreenScaffold(title = stringResource(R.string.emergency_title), onBack = { onEvent(EmergencyEvent.Back) }) { padding ->
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            state.sections.isEmpty() -> EmptyState(
                icon = Icons.Rounded.PowerOff,
                headline = stringResource(R.string.emergency_empty_title),
                body = stringResource(R.string.emergency_empty_body),
                actionLabel = stringResource(R.string.home_emergency_add),
                onAction = { onEvent(EmergencyEvent.Add) },
                modifier = Modifier.padding(padding),
                actionModifier = Modifier.testTag("hm_emergency_add"),
            )
            else -> LazyColumn(
                Modifier.padding(padding).fillMaxWidth().testTag("hm_emergency_list"),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                state.sections.forEach { section ->
                    item(key = "h-" + section.group.name) { GroupHeader(section.group) }
                    itemsIndexed(section.shutoffs, key = { _, s -> s.itemId }) { i, s ->
                        ShutoffBlock(s, "${section.group.name.lowercase()}_${i + 1}", previewPhoto, onEvent)
                    }
                }
                item(key = "manage") { Manage(onEvent) }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: ShutoffGroup) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { heading() }.testTag("hm_emergency_group_" + group.name.lowercase()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(groupIcon(group), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(stringResource(groupLabel(group)), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ShutoffBlock(s: Shutoff, tag: String, previewPhoto: Painter?, onEvent: (EmergencyEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        // The name opens the item to change instructions or photo; the whole row is the 48 dp target.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onEvent(EmergencyEvent.Open(s.itemId)) }
                .padding(horizontal = 16.dp, vertical = 8.dp).testTag("hm_emergency_shutoff_$tag"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.headlineSmall)
                val where = listOfNotNull(
                    if (ShutoffGroups.isMain(s.subtypeId)) stringResource(R.string.emergency_main) else null,
                    s.roomName.ifBlank { null },
                ).joinToString(stringResource(R.string.list_separator))
                if (where.isNotEmpty()) Text(where, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = stringResource(R.string.emergency_open, s.name))
        }
        Text(
            s.instructions.ifBlank { stringResource(R.string.emergency_no_instructions) },
            fontSize = 24.sp, lineHeight = 32.sp,
            color = if (s.instructions.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp).testTag("hm_emergency_instructions"),
        )
        if (s.photoPath != null) {
            ZoomablePhoto(s, previewPhoto, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
        }
        HorizontalDivider(Modifier.padding(top = 16.dp))
    }
}

/** Full-width photo; two fingers zoom up to 5× and pan while zoomed, one finger still scrolls the page. */
@Composable
private fun ZoomablePhoto(s: Shutoff, previewPhoto: Painter?, modifier: Modifier = Modifier) {
    var scale by remember(s.itemId) { mutableFloatStateOf(1f) }
    var offset by remember(s.itemId) { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    val description = stringResource(R.string.emergency_photo, s.name)
    Surface(
        modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)).testTag("hm_emergency_photo"),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(
            Modifier.transformable(transform, canPan = { scale > 1f })
                .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            contentAlignment = Alignment.Center,
        ) {
            if (previewPhoto != null) {
                Image(previewPhoto, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
            } else {
                AsyncImage(File(s.photoPath.orEmpty()), contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f))
            }
        }
    }
}

@Composable
private fun Manage(onEvent: (EmergencyEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("hm_emergency_manage"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.emergency_manage), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.emergency_manage_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { onEvent(EmergencyEvent.Add) }, modifier = Modifier.heightIn(min = 48.dp).testTag("hm_emergency_manage_add")) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(stringResource(R.string.home_emergency_add), modifier = Modifier.padding(start = 8.dp))
        }
    }
}

private fun groupLabel(g: ShutoffGroup): Int = when (g) {
    ShutoffGroup.WATER -> R.string.emergency_group_water
    ShutoffGroup.GAS -> R.string.emergency_group_gas
    ShutoffGroup.ELECTRICAL -> R.string.emergency_group_electrical
    ShutoffGroup.HVAC -> R.string.emergency_group_hvac
    ShutoffGroup.OTHER -> R.string.emergency_group_other
}

private fun groupIcon(g: ShutoffGroup): ImageVector = when (g) {
    ShutoffGroup.WATER -> Icons.Rounded.WaterDrop
    ShutoffGroup.GAS -> Icons.Rounded.LocalFireDepartment
    ShutoffGroup.ELECTRICAL -> Icons.Rounded.Bolt
    ShutoffGroup.HVAC -> Icons.Rounded.Air
    ShutoffGroup.OTHER -> Icons.Rounded.Water
}
