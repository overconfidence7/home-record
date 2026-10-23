package com.kurtz.homerecord.housemodel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.annotation.StringRes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.units.LengthParser
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.RoomKind

data class RoomUiState(
    val loading: Boolean = true,
    val room: PlanRoom? = null,
    val system: UnitSystem = UnitSystem.IMPERIAL,
    val floorName: String? = null,
    val homeName: String? = null,
)

/** HM-Room, opened from the blueprint (AC-C14). Each section says what it will hold until its feature lands (AC-U4). */
@Composable
fun RoomScreen(state: RoomUiState, onBack: () -> Unit) {
    val subtitle = listOfNotNull(state.floorName, state.homeName).joinToString(" · ").ifBlank { null }
    ScreenScaffold(
        title = if (state.loading || state.room != null) {
            state.room?.name?.ifBlank { null } ?: stringResource(R.string.plan_unnamed_room)
        } else {
            stringResource(R.string.room_screen_title)
        },
        subtitle = subtitle,
        onBack = onBack,
    ) { padding ->
        val room = state.room
        when {
            state.loading -> LoadingState(Modifier.padding(padding))
            room == null -> EmptyState(
                icon = Icons.Rounded.MeetingRoom,
                headline = stringResource(R.string.room_not_found_title),
                body = stringResource(R.string.room_not_found_body),
                actionLabel = stringResource(R.string.room_back),
                onAction = onBack,
                modifier = Modifier.padding(padding),
            )
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(
                        R.string.room_size_summary,
                        LengthParser.format(room.widthIn, state.system),
                        LengthParser.format(room.lengthIn, state.system),
                        kindLabel(RoomKind.parse(room.kind)),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
                RoomSections.forEach { section ->
                    RoomSectionCard(section)
                }
            }
        }
    }
}

@Composable
fun RoomRoute(repo: HomeRepository, roomId: String, onBack: () -> Unit) {
    var state by remember(roomId) { mutableStateOf(RoomUiState()) }
    LaunchedEffect(roomId) {
        val where = repo.roomLocation(roomId)
        state = RoomUiState(loading = false, room = repo.findRoom(roomId), floorName = where?.floorName, homeName = where?.homeName)
    }
    RoomScreen(state, onBack)
}

private data class RoomSection(val tag: String, val icon: ImageVector, @StringRes val title: Int, @StringRes val empty: Int)

private val RoomSections = listOf(
    RoomSection("items", Icons.Rounded.Inventory2, R.string.room_section_items, R.string.room_section_items_empty),
    RoomSection("walls", Icons.Rounded.Straighten, R.string.room_section_walls, R.string.room_section_walls_empty),
    RoomSection("photos", Icons.Rounded.PhotoLibrary, R.string.room_section_photos, R.string.room_section_photos_empty),
    RoomSection("overlays", Icons.Rounded.Layers, R.string.room_section_overlays, R.string.room_section_overlays_empty),
    RoomSection("breakers", Icons.Rounded.ElectricalServices, R.string.room_section_breakers, R.string.room_section_breakers_empty),
    RoomSection("shutoffs", Icons.Rounded.WaterDrop, R.string.room_section_shutoffs, R.string.room_section_shutoffs_empty),
    RoomSection("notes", Icons.Rounded.StickyNote2, R.string.room_section_notes, R.string.room_section_notes_empty),
)

@Composable
private fun RoomSectionCard(section: RoomSection) {
    Card(Modifier.fillMaxWidth().testTag("hm_room_${section.tag}")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(section.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(section.title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            Text(stringResource(section.empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
