package com.kurtz.homerecord.housemodel.blueprint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import com.kurtz.homerecord.core.units.LengthParser
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.plan.PlanRoom

/** Read-only blueprint of one floor, fitted to the space it gets. Tap opens a room, long press asks for its menu. */
@Composable
fun BlueprintCanvas(
    rooms: List<PlanRoom>,
    modifier: Modifier = Modifier,
    layers: LayerSet = LayerSet(),
    markers: List<BlueprintMarker> = emptyList(),
    system: UnitSystem = UnitSystem.IMPERIAL,
    onRoomClick: (PlanRoom) -> Unit = {},
    onRoomLongClick: (PlanRoom) -> Unit = {},
) {
    val density = LocalDensity.current.density
    val measurer = rememberTextMeasurer()
    val scheme = MaterialTheme.colorScheme
    val nameStyle = MaterialTheme.typography.labelLarge
    val dimStyle = MaterialTheme.typography.labelSmall
    val colors = BlueprintColors(
        fill = scheme.surfaceContainerHigh,
        wall = scheme.onSurface,
        text = scheme.onSurface,
        dimension = scheme.onSurfaceVariant,
        dark = isSystemInDarkTheme(),
    )
    val currentRooms by rememberUpdatedState(rooms)
    val click by rememberUpdatedState(onRoomClick)
    val longClick by rememberUpdatedState(onRoomLongClick)
    val description = pluralStringResource(R.plurals.blueprint_description, rooms.size, rooms.size)
    val marginPx = 40f * density
    BoxWithConstraints(modifier) {
    val widthPx = constraints.maxWidth.toFloat()
    val heightPx = constraints.maxHeight.toFloat()
    Canvas(
        Modifier
            .fillMaxSize()
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { at ->
                        val view = BlueprintView.fit(currentRooms, size.width.toFloat(), size.height.toFloat(), marginPx)
                        RoomHitTester.roomAt(currentRooms, view.planX(at.x), view.planY(at.y))?.let(click)
                    },
                    onLongPress = { at ->
                        val view = BlueprintView.fit(currentRooms, size.width.toFloat(), size.height.toFloat(), marginPx)
                        RoomHitTester.roomAt(currentRooms, view.planX(at.x), view.planY(at.y))?.let(longClick)
                    },
                )
            },
    ) {
        val view = BlueprintView.fit(rooms, size.width, size.height, marginPx)
        drawBlueprint(rooms, view, density, measurer, nameStyle, dimStyle, colors, system, layers, markers)
    }
    // Rooms are drawn, so TalkBack and UI tests get one pointer-transparent node per room laid over its bounds.
    if (rooms.isNotEmpty() && widthPx > 0f && heightPx > 0f) {
        val view = BlueprintView.fit(rooms, widthPx, heightPx, marginPx)
        val toDp = { px: Float -> (px / density).dp }
        rooms.forEach { room ->
            val size = stringResource(
                R.string.blueprint_room_size,
                LengthParser.format(room.maxX - room.minX, system),
                LengthParser.format(room.maxY - room.minY, system),
            )
            val left = view.x(room.minX)
            val top = view.y(room.minY)
            Box(
                Modifier
                    .offset(toDp(left), toDp(top))
                    .size(toDp(view.x(room.maxX) - left), toDp(view.y(room.maxY) - top))
                    .semantics {
                        role = Role.Button
                        contentDescription = room.name
                        stateDescription = size
                        onClick { click(room); true }
                        onLongClick { longClick(room); true }
                    },
            )
        }
    }
    }
}

@Composable
fun blueprintLayerLabel(layer: BlueprintLayer): String = stringResource(
    when (layer) {
        BlueprintLayer.ITEMS -> R.string.layer_items
        BlueprintLayer.ELECTRICAL -> R.string.layer_electrical
        BlueprintLayer.PLUMBING -> R.string.layer_plumbing
        BlueprintLayer.HVAC -> R.string.layer_hvac
        BlueprintLayer.LOW_VOLTAGE -> R.string.layer_low_voltage
        BlueprintLayer.GAS -> R.string.layer_gas
        BlueprintLayer.DRAIN_VENT -> R.string.layer_drain_vent
    },
)

/** One chip per layer, each tinted with its own color when on (AC-C15). */
@Composable
fun LayerToggleRow(layers: LayerSet, onToggle: (BlueprintLayer) -> Unit, modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    LazyRow(
        modifier.testTag("${LocalTagPrefix.current}_layers"),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(BlueprintLayer.entries, key = { it.wire }) { layer ->
            val tint = LayerPalette.color(layer, dark)
            FilterChip(
                selected = layer in layers,
                onClick = { onToggle(layer) },
                modifier = Modifier.testTag("${LocalTagPrefix.current}_layer_${layer.wire}"),
                label = { Text(blueprintLayerLabel(layer)) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = tint.copy(alpha = 0.22f),
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = layer in layers,
                    selectedBorderColor = tint,
                    selectedBorderWidth = 2.dp,
                ),
            )
        }
    }
}
