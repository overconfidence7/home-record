package com.kurtz.homerecord.housemanual.house

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.core.units.LengthParser
import com.kurtz.homerecord.core.units.UnitSystem
import com.kurtz.homerecord.coreui.components.EmptyState
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.coreui.shell.ScreenScaffold
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.plan.kindLabel
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.plan.RoomKind

data class RoomUiState(val loading: Boolean = true, val room: PlanRoom? = null, val system: UnitSystem = UnitSystem.IMPERIAL)

/** HM-Room, opened from the blueprint (AC-C14). The room's sections arrive with T017. */
@Composable
fun RoomScreen(state: RoomUiState, onBack: () -> Unit) {
    ScreenScaffold(title = state.room?.name?.ifBlank { null } ?: stringResource(R.string.plan_unnamed_room), onBack = onBack) { padding ->
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
            else -> Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                Text(
                    stringResource(
                        R.string.room_size_summary,
                        LengthParser.format(room.widthIn, state.system),
                        LengthParser.format(room.lengthIn, state.system),
                        kindLabel(RoomKind.parse(room.kind)),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
fun RoomRoute(repo: HomeRepository, roomId: String, onBack: () -> Unit) {
    var state by remember(roomId) { mutableStateOf(RoomUiState()) }
    LaunchedEffect(roomId) { state = RoomUiState(loading = false, room = repo.findRoom(roomId)) }
    RoomScreen(state, onBack)
}
