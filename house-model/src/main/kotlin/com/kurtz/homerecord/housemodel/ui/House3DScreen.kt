package com.kurtz.homerecord.housemodel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.coreui.shell.SharedKeys
import com.kurtz.homerecord.coreui.shell.sharedScreenBounds
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.scene.FloorScene
import com.kurtz.homerecord.housemodel.blueprint.LayerToggleRow
import com.kurtz.homerecord.housemodel.overlays.nameRes
import com.kurtz.homerecord.housemodel.scene.FloorScenePicture
import com.kurtz.homerecord.housemodel.scene.SceneMarker
import com.kurtz.homerecord.housemodel.walls.WallStage

/** How the scene is to be drawn: the stage's photos, the Reset view count, the room the camera stands in, room taps. */
data class SceneCamera(
    val stage: WallStage,
    val resetCount: Int,
    val roomId: String? = null,
    val onRoomTap: (String) -> Unit = {},
    /** Overlays and items to draw on their walls and floors, already filtered by the layer toggles (AC-C23). */
    val markers: List<SceneMarker> = emptyList(),
    val onMarkerTap: (SceneMarker) -> Unit = {},
)

/** Draws the floor; the route passes the live SceneView, previews and tests the still picture. */
typealias SceneSlot = @Composable (scene: FloorScene, camera: SceneCamera, modifier: Modifier) -> Unit

/** The still picture; standing in a room it shows that room on its own, cut away from the rest of the floor. */
val StillScene: SceneSlot = { scene, camera, modifier ->
    val markers = camera.roomId?.let { id -> camera.markers.filter { it.roomId == id } } ?: camera.markers
    FloorScenePicture(scene.only(camera.roomId), camera.stage, modifier, markers)
}

private fun FloorScene.only(roomId: String?): FloorScene {
    if (roomId == null || roomId !in rooms) return this
    val parts = mesh.parts.filter { it.key.roomId == roomId }
    return copy(mesh = mesh.copy(parts = parts), ghostRooms = emptySet())
}

/**
 * HM-3D (AC-C20, AC-C21): the chosen floor's rooms as walls over the faded floors below it, wall photos at the chosen
 * stage painted on, Reset view over the scene. Floor chips and All floors when the home has more than one floor; the
 * stage picker matches the room page's. Tapping a room stands the camera inside it (AC-C22): the title names the room,
 * Back to floor (and the system Back, unless the screen opened on the room) returns to the whole floor. The blueprint's
 * layer chips show or hide overlays and item markers drawn on their walls and floors; tapping one opens it, and TalkBack
 * offers each shown marker as an "Open …" action on the scene (AC-C23).
 */
@Composable
fun House3DScreen(
    state: House3DUiState,
    onEvent: (House3DEvent) -> Unit,
    onBack: () -> Unit,
    /** Empty floor's "Edit plan"; null for a viewer of a shared home (AC-H16), who gets no action. */
    onEditPlan: ((floorId: String?) -> Unit)?,
    onOpenMarker: (SceneMarker) -> Unit = {},
    sceneSlot: SceneSlot = StillScene,
) {
    val prefix = LocalTagPrefix.current
    val floorName = if (state.allFloors) stringResource(R.string.house3d_all_floors) else state.floor?.name
    val room = state.room
    BackHandler(enabled = room != null && !state.openedOnRoom) { onEvent(House3DEvent.BackToFloor) }
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    ScreenScaffold(
        title = (room?.name ?: floorName)?.let { stringResource(R.string.house3d_title, it) } ?: stringResource(R.string.house3d_title_plain),
        subtitle = state.homeName.ifBlank { null },
        onBack = onBack,
    ) { padding ->
        val scene = state.scene
        val markers = state.visibleMarkers.let { all -> if (room != null) all.filter { it.roomId == room.id } else all }
        val markerActions = markers.map { marker ->
            val name = marker.label.ifBlank { marker.overlayKind?.let { stringResource(it.nameRes) } ?: "" }
            CustomAccessibilityAction(stringResource(R.string.house3d_open_marker, name)) { onOpenMarker(marker); true }
        }
        val controls: @Composable () -> Unit = {
            if (state.floors.size > 1) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.floors.reversed(), key = { it.id }) { floor ->
                        FilterChip(
                            selected = floor.id == state.floorId && !state.allFloors,
                            onClick = { onEvent(House3DEvent.SelectFloor(floor.id)) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_3d_floor_${floor.id}"),
                            label = { Text(floor.name) },
                        )
                    }
                    item(key = "all") {
                        FilterChip(
                            selected = state.allFloors,
                            onClick = { onEvent(House3DEvent.ShowAllFloors) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_3d_floor_all"),
                            leadingIcon = { Icon(Icons.Rounded.Layers, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) },
                            label = { Text(stringResource(R.string.house3d_all_floors)) },
                        )
                    }
                }
            }
            WallStagePicker(state.stage, { onEvent(House3DEvent.SelectStage(it)) }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), screen = "3d")
            LayerToggleRow(state.layers, { onEvent(House3DEvent.ToggleLayer(it)) })
            if (scene != null && !scene.isEmpty) {
                Text(
                    stringResource(R.string.house3d_walls_photographed, state.photographedWalls, scene.wallCount, stringResource(state.stage.labelRes)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("${prefix}_3d_walls"),
                )
                if (scene.ghostRooms.isNotEmpty()) {
                    Text(
                        stringResource(R.string.house3d_ghost_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp).testTag("${prefix}_3d_ghost_hint"),
                    )
                }
                val hint = stringResource(if (room != null) R.string.house3d_room_hint else R.string.house3d_hint)
                Text(
                    if (markers.isEmpty()) hint else hint + " " + stringResource(R.string.house3d_marker_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        // Opened from a room page's "View in 3D": the view grows out of that button and shrinks back into it on Back
        // (AC-C22). The key holds from the first frame (roomId comes from the nav key), through loading and the scene.
        val sharedRoomId = state.roomId?.takeIf { state.openedOnRoom }
        val body: @Composable (Modifier) -> Unit = { outer ->
            val m = if (sharedRoomId != null) outer.sharedScreenBounds(SharedKeys.room3D(sharedRoomId)) else outer
            when {
                state.loading -> LoadingState(m)
                state.failed -> ErrorState(stringResource(R.string.house3d_failed), { onEvent(House3DEvent.Retry) }, m.testTag("${prefix}_3d_error"))
                scene == null || scene.isEmpty -> EmptyState(
                    icon = Icons.Rounded.ViewInAr,
                    headline = stringResource(R.string.house3d_empty_title),
                    body = stringResource(if (onEditPlan != null) R.string.house3d_empty_body else R.string.house3d_empty_body_view_only),
                    actionLabel = if (onEditPlan != null) stringResource(R.string.house_edit_plan) else null,
                    onAction = { onEditPlan?.invoke(state.floorId) },
                    modifier = m,
                    actionModifier = Modifier.testTag("${prefix}_3d_edit_plan"),
                )
                else -> Box(m) {
                    val description = if (room != null) {
                        stringResource(R.string.house3d_room_description, room.name)
                    } else {
                        stringResource(R.string.house3d_scene_description, floorName.orEmpty(), scene.roomCount)
                    }
                    // The tag and description sit on a plain Compose box around the scene: SceneView is an AndroidView,
                    // whose own accessibility node replaces the semantics set on it, so Maestro and TalkBack would never
                    // see `<app>_3d_scene` or the description on the view itself.
                    Box(
                        Modifier.fillMaxSize()
                            .semantics {
                                contentDescription = description
                                if (markerActions.isNotEmpty()) customActions = markerActions
                            }
                            .testTag("${prefix}_3d_scene"),
                    ) {
                        sceneSlot(
                            scene,
                            SceneCamera(
                                state.stage, state.resetCount, room?.id,
                                onRoomTap = { onEvent(House3DEvent.ViewRoom(it)) },
                                markers = markers,
                                onMarkerTap = onOpenMarker,
                            ),
                            Modifier.fillMaxSize(),
                        )
                    }
                    FlowRow(
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (room != null) {
                            FilledTonalButton(
                                onClick = { onEvent(House3DEvent.BackToFloor) },
                                modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_3d_back_to_floor"),
                            ) {
                                Icon(Icons.Rounded.Layers, contentDescription = null)
                                Text(stringResource(R.string.house3d_back_to_floor), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        FilledTonalButton(
                            onClick = { onEvent(House3DEvent.ResetView) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_3d_reset"),
                        ) {
                            Icon(Icons.Rounded.CenterFocusWeak, contentDescription = null)
                            Text(stringResource(R.string.house3d_reset), modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
        if (landscape) {
            Row(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.width(320.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) { controls() }
                body(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { controls() }
                body(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}
