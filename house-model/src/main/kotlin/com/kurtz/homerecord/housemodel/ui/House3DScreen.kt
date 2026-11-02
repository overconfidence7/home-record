package com.kurtz.homerecord.housemodel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.ErrorState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.LocalTagPrefix
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.scene.FloorScene
import com.kurtz.homerecord.housemodel.scene.FloorScenePicture
import com.kurtz.homerecord.housemodel.walls.WallStage

/** Draws the floor; the route passes the live SceneView, previews and tests the still picture. */
typealias SceneSlot = @Composable (scene: FloorScene, stage: WallStage, resetCount: Int, modifier: Modifier) -> Unit

val StillScene: SceneSlot = { scene, stage, _, modifier -> FloorScenePicture(scene, stage, modifier) }

/**
 * HM-3D (AC-C20): the chosen floor's rooms as walls, wall photos at the chosen stage painted on, Reset view over the
 * scene. Floor chips when the home has more than one floor; the stage picker matches the room page's.
 */
@Composable
fun House3DScreen(
    state: House3DUiState,
    onEvent: (House3DEvent) -> Unit,
    onBack: () -> Unit,
    onEditPlan: (floorId: String?) -> Unit,
    sceneSlot: SceneSlot = StillScene,
) {
    val prefix = LocalTagPrefix.current
    val floorName = state.floor?.name
    val landscape = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    ScreenScaffold(
        title = floorName?.let { stringResource(R.string.house3d_title, it) } ?: stringResource(R.string.house3d_title_plain),
        subtitle = state.homeName.ifBlank { null },
        onBack = onBack,
    ) { padding ->
        val scene = state.scene
        val controls: @Composable () -> Unit = {
            if (state.floors.size > 1) {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.floors.reversed(), key = { it.id }) { floor ->
                        FilterChip(
                            selected = floor.id == state.floorId,
                            onClick = { onEvent(House3DEvent.SelectFloor(floor.id)) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("${prefix}_3d_floor_${floor.id}"),
                            label = { Text(floor.name) },
                        )
                    }
                }
            }
            WallStagePicker(state.stage, { onEvent(House3DEvent.SelectStage(it)) }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp), screen = "3d")
            if (scene != null && !scene.isEmpty) {
                Text(
                    stringResource(R.string.house3d_walls_photographed, state.photographedWalls, scene.wallCount, stringResource(state.stage.labelRes)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp).testTag("${prefix}_3d_walls"),
                )
                Text(
                    stringResource(R.string.house3d_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        val body: @Composable (Modifier) -> Unit = { m ->
            when {
                state.loading -> LoadingState(m)
                state.failed -> ErrorState(stringResource(R.string.house3d_failed), { onEvent(House3DEvent.Retry) }, m.testTag("${prefix}_3d_error"))
                scene == null || scene.isEmpty -> EmptyState(
                    icon = Icons.Rounded.ViewInAr,
                    headline = stringResource(R.string.house3d_empty_title),
                    body = stringResource(R.string.house3d_empty_body),
                    actionLabel = stringResource(R.string.house_edit_plan),
                    onAction = { onEditPlan(state.floorId) },
                    modifier = m,
                    actionModifier = Modifier.testTag("${prefix}_3d_edit_plan"),
                )
                else -> Box(m) {
                    val description = stringResource(R.string.house3d_scene_description, floorName.orEmpty(), scene.roomCount)
                    sceneSlot(
                        scene,
                        state.stage,
                        state.resetCount,
                        Modifier.fillMaxSize().semantics { contentDescription = description }.testTag("${prefix}_3d_scene"),
                    )
                    FilledTonalButton(
                        onClick = { onEvent(House3DEvent.ResetView) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).heightIn(min = 48.dp).testTag("${prefix}_3d_reset"),
                    ) {
                        Icon(Icons.Rounded.CenterFocusWeak, contentDescription = null)
                        Text(stringResource(R.string.house3d_reset), modifier = Modifier.padding(start = 8.dp))
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
