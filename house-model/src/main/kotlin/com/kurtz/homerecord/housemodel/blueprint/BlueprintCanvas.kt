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
import androidx.compose.ui.geometry.Offset
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
import com.kurtz.homerecord.housemodel.overlays.nameRes

/**
 * Read-only blueprint of one floor, fitted to the space it gets. Tap opens a room, long press asks for its menu.
 * Task [pins] (AC-F35) are drawn on top and a tap on one calls [onPinClick]. With [onPlanTap] set, a tap anywhere on
 * the plan reports its plan position in inches instead (placing a pin), and rooms and pins do not open.
 */
@Composable
fun BlueprintCanvas(
    rooms: List<PlanRoom>,
    modifier: Modifier = Modifier,
    layers: LayerSet = LayerSet(),
    /** The floor's plan overlays (AC-C25), each drawn when its layer is on. */
    overlays: List<com.kurtz.homerecord.housemodel.overlays.Overlay> = emptyList(),
    /** The floor's wall overlays along their walls and its placed items (AC-C15), each drawn when its layer is on. */
    marks: List<PlanMark> = emptyList(),
    system: UnitSystem = UnitSystem.IMPERIAL,
    pins: List<BlueprintPin> = emptyList(),
    onPinClick: (String) -> Unit = {},
    onPlanTap: ((xIn: Int, yIn: Int) -> Unit)? = null,
    onRoomClick: (PlanRoom) -> Unit = {},
    onRoomLongClick: (PlanRoom) -> Unit = {},
    /**
     * A tap on a shown wall overlay, item or plan overlay (T055a); a plan overlay comes as a mark with no inward side.
     * Null keeps marks out of the way, taps landing on the room beneath.
     */
    onMarkClick: ((PlanMark) -> Unit)? = null,
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
    val currentPins by rememberUpdatedState(pins)
    val pinClick by rememberUpdatedState(onPinClick)
    val planTap by rememberUpdatedState(onPlanTap)
    val markClick by rememberUpdatedState(onMarkClick)
    val overlayIcons = rememberOverlayIcons()
    val itemIcons = rememberItemIcons()
    val shownOverlays = overlays.filter { layers.shows(it) }
    val shownMarks = PlanMarks.visible(marks, layers)
    // Everything a tap can open, in the order it is drawn: wall overlays, plan overlays, then items on top.
    val tappable = shownMarks.filter { !it.isItem } +
        shownOverlays.map { PlanMark(com.kurtz.homerecord.housemodel.scene.MarkerTarget.OverlayMark(it.id), it.roomId.orEmpty(), it.kind.layer, it.kind, it.points, label = it.label) } +
        shownMarks.filter { it.isItem }
    val currentTappable by rememberUpdatedState(tappable)
    val insetPx = PLAN_WALL_INSET_DP * density
    val pinColors = PinColors(scheme.tertiary, scheme.onTertiary, scheme.surface)
    // The number sits inside a fixed-size head, so it does not grow with the font scale; TalkBack reads the task title.
    val fontScale = LocalDensity.current.fontScale
    val pinStyle = MaterialTheme.typography.labelMedium.copy(fontSize = androidx.compose.ui.unit.TextUnit(12f / fontScale, androidx.compose.ui.unit.TextUnitType.Sp))
    val liftPx = PIN_LIFT_DP * density
    val hitPx = 24f * density
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
                        val place = planTap
                        if (place != null) {
                            place(Math.round(view.planX(at.x)), Math.round(view.planY(at.y)))
                            return@detectTapGestures
                        }
                        val pin = PinHitTester.pinAt(currentPins, view, at.x, at.y, liftPx, hitPx)
                        val openMark = markClick
                        val mark = if (pin == null && openMark != null) PlanMarkHitTester.markAt(currentTappable, view, at.x, at.y, insetPx, hitPx) else null
                        if (pin != null) pinClick(pin.id)
                        else if (mark != null && openMark != null) openMark(mark)
                        else RoomHitTester.roomAt(currentRooms, view.planX(at.x), view.planY(at.y))?.let(click)
                    },
                    onLongPress = { at ->
                        if (planTap != null) return@detectTapGestures
                        val view = BlueprintView.fit(currentRooms, size.width.toFloat(), size.height.toFloat(), marginPx)
                        RoomHitTester.roomAt(currentRooms, view.planX(at.x), view.planY(at.y))?.let(longClick)
                    },
                )
            },
    ) {
        val view = BlueprintView.fit(rooms, size.width, size.height, marginPx)
        val obstacles = tappable.flatMap { m ->
            val half = (if (m.isItem) PLAN_ITEM_RADIUS_DP + 2f else if (m.isRun) 4f else PLAN_SPOT_RADIUS_DP + 2f) * density
            LabelPlacer.alongRun(PlanMarkHitTester.placed(m, view, insetPx), half)
        }
        drawBlueprint(rooms, view, density, measurer, nameStyle, dimStyle, colors, system, obstacles)
        val toScreen = { p: com.kurtz.homerecord.housemodel.overlays.OverlayPoint -> Offset(p.x * view.scale + view.originX, p.y * view.scale + view.originY) }
        drawPlanMarks(shownMarks, toScreen, density, colors.dark, scheme.surface, overlayIcons, itemIcons, items = false)
        drawPlanOverlays(shownOverlays, toScreen, density, colors.dark, scheme.surface, overlayIcons)
        drawPlanMarks(shownMarks, toScreen, density, colors.dark, scheme.surface, overlayIcons, itemIcons, items = true)
        pins.forEach { drawPin(it, view, density, measurer, pinStyle, pinColors) }
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
        val prefix = LocalTagPrefix.current
        // One 48 dp node per wall overlay and item shown, so TalkBack names what each layer draws and opens it (rule 13).
        shownMarks.forEach { mark ->
            val (px, py) = PlanMarkHitTester.placed(mark, view, insetPx).let { it[it.size / 2] }
            val kindName = mark.overlayKind?.let { stringResource(it.nameRes) } ?: stringResource(R.string.layer_items)
            val open = onMarkClick
            Box(
                Modifier
                    .offset(toDp(px - hitPx), toDp(py - hitPx))
                    .size(toDp(2 * hitPx))
                    .testTag("${prefix}_plan_mark")
                    .semantics {
                        contentDescription = mark.label.ifBlank { kindName }
                        if (open != null && planTap == null) {
                            role = Role.Button
                            onClick { open(mark); true }
                        }
                    },
            )
        }
        // One 48 dp node per pin over its head, after the rooms so TalkBack reads them last (rule 13).
        pins.forEach { pin ->
            val (hx, hy) = PinHitTester.head(pin, view, liftPx)
            Box(
                Modifier
                    .offset(toDp(hx - hitPx), toDp(hy - hitPx))
                    .size(toDp(2 * hitPx))
                    .testTag("${prefix}_plan_pin_${pin.id}")
                    .semantics {
                        role = Role.Button
                        contentDescription = pin.label
                        if (planTap == null) onClick { pinClick(pin.id); true }
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
