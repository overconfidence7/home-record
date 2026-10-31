package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.blueprint.LayerStore
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import com.kurtz.homerecord.housemodel.ui.HouseRoute

/** What the House tab needs from the app: the shared house model and where to go for the editor and floors. */
class JobHouseDeps(
    val homes: HomeRepository,
    val layers: LayerStore,
    val provisioner: JobHomeProvisioner,
    val onEditPlan: (homeId: String, title: String, floorId: String?) -> Unit,
    val onOpenRoom: (roomId: String) -> Unit,
    val onEditShape: (homeId: String, title: String, floorId: String?, PlanRoom) -> Unit,
    val onOpenFloors: (homeId: String, title: String) -> Unit,
)

@Composable
fun JobRoute(companyId: String, jobId: String, db: HomeRecordDatabase, house: JobHouseDeps, onEdit: () -> Unit, onBack: () -> Unit, onCapture: (() -> Unit)? = null, onOpenPhoto: ((String) -> Unit)? = null, onOpenFile: ((String) -> Unit)? = null, files: com.kurtz.homerecord.fieldlog.files.FilesDeps? = null, downloads: com.kurtz.homerecord.core.media.MediaDownloads? = null, logs: com.kurtz.homerecord.core.log.DailyLogRepository? = null, onEditWeather: (java.time.LocalDate) -> Unit = {}, gates: kotlinx.coroutines.flow.Flow<com.kurtz.homerecord.core.company.RoleGates>, initialTab: JobTab = JobTab.OVERVIEW, initialLogDay: java.time.LocalDate? = null) {
    val vm = viewModel<JobOverviewViewModel>(
        key = jobId,
        factory = viewModelFactory {
            initializer {
                JobOverviewViewModel(
                    companyId, jobId, db.jobDao(), db.clientDao(), db.jobCrewDao(), db.memberDao(),
                    db.assignmentDao(), db.taskDao(), db.punchDao(), db.photoDao(), gates, house.provisioner::ensure,
                )
            }
        },
    )
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    val state by vm.state.collectAsState()
    JobScreen(
        state, tab, { tab = it },
        { event ->
            when (event) {
                is JobOverviewEvent.OpenTab -> tab = event.tab
                JobOverviewEvent.OpenEdit -> if (state.canEdit) onEdit()
            }
        },
        onBack,
        photosContent = {
            com.kurtz.homerecord.fieldlog.photos.PhotosRoute(
                if (downloads == null) com.kurtz.homerecord.fieldlog.photos.PhotosDeps(jobId, db.photoDao(), db.jobDao(), db.roomDao())
                else com.kurtz.homerecord.fieldlog.photos.PhotosDeps(
                    jobId, db.photoDao(), db.jobDao(), db.roomDao(), missing = downloads::missing, download = downloads::photo,
                ),
                onCapture = { onCapture?.invoke() },
                onOpen = { onOpenPhoto?.invoke(it) },
            )
        },
        logContent = {
            if (logs != null) com.kurtz.homerecord.fieldlog.log.LogRoute(com.kurtz.homerecord.fieldlog.log.LogDeps(jobId, logs, db.photoDao(), gates, initialDay = initialLogDay), onEditWeather = onEditWeather)
        },
        filesContent = {
            if (files != null) com.kurtz.homerecord.fieldlog.files.FilesRoute(files, onOpenFile = { onOpenFile?.invoke(it) })
        },
        houseContent = {
            val homeId = state.homeId
            if (homeId == null) {
                JobHouseEmpty(onStart = vm::startHouse, modifier = Modifier.fillMaxSize())
            } else {
                HouseRoute(
                    repo = house.homes,
                    layerStore = house.layers,
                    homeId = homeId,
                    homeName = state.name,
                    summary = state.address.orEmpty(),
                    onOpenRoom = { house.onOpenRoom(it.id) },
                    onEditPlan = { house.onEditPlan(homeId, state.name, it) },
                    onEditShape = { floorId, room -> house.onEditShape(homeId, state.name, floorId, room) },
                    onOpenFloors = { house.onOpenFloors(homeId, state.name) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    )
}
