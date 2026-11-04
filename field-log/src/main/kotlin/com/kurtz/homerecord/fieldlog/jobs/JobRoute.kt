package com.kurtz.homerecord.fieldlog.jobs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.data.HomeRecordDatabase
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.core.model.TaskPins
import com.kurtz.homerecord.fieldlog.R
import com.kurtz.homerecord.housemodel.blueprint.BlueprintPin
import com.kurtz.homerecord.housemodel.blueprint.LayerStore
import kotlinx.coroutines.flow.flowOf
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
    /** Opens the job's house in 3D on the floor in view (AC-C20). */
    val on3D: (homeId: String, title: String, floorId: String?) -> Unit,
    /** The job's plan overlays on the blueprint (AC-C25). */
    val overlays: com.kurtz.homerecord.housemodel.overlays.OverlayRepository? = null,
    /** Opens "Behind the wall" for the job's house: room, wall, camera (AC-F49). */
    val onBehindWall: ((homeId: String, title: String) -> Unit)? = null,
    /** A tapped overlay on the blueprint: its wall or the job plan with it picked (T055a). */
    val onOpenOverlay: ((homeId: String, title: String, com.kurtz.homerecord.housemodel.overlays.Overlay) -> Unit)? = null,
    /** A tapped item on the blueprint; Field Log opens the room it stands in until the item page (T062). */
    val onOpenItemRoom: (roomId: String) -> Unit = onOpenRoom,
)

@Composable
fun JobRoute(companyId: String, jobId: String, db: HomeRecordDatabase, house: JobHouseDeps, onEdit: () -> Unit, onBack: () -> Unit, onCapture: (() -> Unit)? = null, onOpenPhoto: ((String) -> Unit)? = null, onOpenFile: ((String) -> Unit)? = null, files: com.kurtz.homerecord.fieldlog.files.FilesDeps? = null, downloads: com.kurtz.homerecord.core.media.MediaDownloads? = null, logs: com.kurtz.homerecord.core.log.DailyLogRepository? = null, onEditWeather: (java.time.LocalDate) -> Unit = {}, logSender: com.kurtz.homerecord.fieldlog.log.LogSender? = null, onPreviewLog: (java.time.LocalDate) -> Unit = {}, gates: kotlinx.coroutines.flow.Flow<com.kurtz.homerecord.core.company.RoleGates>, initialTab: JobTab = JobTab.OVERVIEW, initialLogDay: java.time.LocalDate? = null, tasks: com.kurtz.homerecord.fieldlog.tasks.TasksDeps? = null, onOpenTask: (String?) -> Unit = {}, changes: com.kurtz.homerecord.fieldlog.changes.ChangesDeps? = null, onOpenChange: (String?) -> Unit = {}) {
    val vm = viewModel<JobOverviewViewModel>(
        key = jobId,
        factory = viewModelFactory {
            initializer {
                JobOverviewViewModel(
                    companyId, jobId, db.jobDao(), db.clientDao(), db.jobCrewDao(), db.memberDao(),
                    db.assignmentDao(), db.taskDao(), db.punchDao(), db.photoDao(), gates, house.provisioner::ensure,
                    me = tasks?.me,
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
            if (logs != null) com.kurtz.homerecord.fieldlog.log.LogRoute(com.kurtz.homerecord.fieldlog.log.LogDeps(jobId, logs, db.photoDao(), gates, dayTurns = com.kurtz.homerecord.core.log.Midnight.ticks(), initialDay = initialLogDay, sender = logSender), onEditWeather = onEditWeather, onPreview = onPreviewLog)
        },
        tasksContent = {
            if (tasks != null) com.kurtz.homerecord.fieldlog.tasks.TasksRoute(tasks, onOpenTask = { onOpenTask(it) }, onAddTask = { onOpenTask(null) })
        },
        changesContent = {
            if (changes != null) com.kurtz.homerecord.fieldlog.changes.ChangesRoute(changes, onOpen = onOpenChange)
        },
        filesContent = {
            if (files != null) com.kurtz.homerecord.fieldlog.files.FilesRoute(files, onOpenFile = { onOpenFile?.invoke(it) })
        },
        houseContent = {
            val homeId = state.homeId
            // Task pins (AC-F35): managers see every open pin and open the task form; Crew see their own and land on Tasks.
            val jobTasks by remember(tasks) { tasks?.tasks?.observeForJob(jobId) ?: flowOf(emptyList()) }.collectAsState(emptyList())
            val canManageTasks by remember(tasks) { tasks?.canManage ?: flowOf(false) }.collectAsState(false)
            val pinLabel = stringResource(R.string.task_pin_label)
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
                    on3D = { floorId -> house.on3D(homeId, state.name, floorId) },
                    overlays = house.overlays,
                    onBehindWall = house.onBehindWall?.let { open -> { open(homeId, state.name) } },
                    onOpenOverlay = house.onOpenOverlay?.let { open -> { o -> open(homeId, state.name, o) } },
                    onOpenItem = { _, roomId -> house.onOpenItemRoom(roomId) },
                    pins = { floorId ->
                        TaskPins.onFloor(jobTasks, floorId, canManageTasks, tasks?.me).mapIndexed { i, t ->
                            val pin = t.planPin ?: return@mapIndexed null
                            BlueprintPin(t.id, String.format(pinLabel, i + 1, t.title), pin.xIn, pin.yIn, i + 1)
                        }.filterNotNull()
                    },
                    onOpenPin = { id -> onOpenTask(id) },
                )
            }
        },
    )
}
