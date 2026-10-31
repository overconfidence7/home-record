package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.data.ClientDao
import com.kurtz.homerecord.core.data.FloorDao
import com.kurtz.homerecord.core.data.HomeDao
import com.kurtz.homerecord.core.data.JobCrewDao
import com.kurtz.homerecord.core.data.JobDao
import com.kurtz.homerecord.core.data.MemberDao
import com.kurtz.homerecord.core.data.RoomDao
import com.kurtz.homerecord.housemodel.R as HouseModelR

@Composable
fun JobEditRoute(
    companyId: String,
    jobId: String?,
    jobs: JobDao,
    clients: ClientDao,
    homes: HomeDao,
    floors: FloorDao,
    rooms: RoomDao,
    crew: JobCrewDao,
    members: MemberDao,
    refreshMembers: suspend () -> Unit,
    recordChange: suspend (String, String) -> Unit,
    onClose: () -> Unit,
    onSaved: () -> Unit = {},
) {
    val mainFloor = stringResource(HouseModelR.string.floor_default_main)
    val vm = viewModel<JobEditViewModel>(
        factory = viewModelFactory { initializer { JobEditViewModel(companyId, jobId, jobs, clients, homes, floors, rooms, crew, members, refreshMembers, recordChange, mainFloor, createSavedStateHandle()) } },
    )
    val state = vm.state.collectAsState().value
    LaunchedEffect(state.saved) { if (state.saved) { onSaved(); onClose() } }
    JobEditScreen(state, vm::onEvent, onClose)
}
