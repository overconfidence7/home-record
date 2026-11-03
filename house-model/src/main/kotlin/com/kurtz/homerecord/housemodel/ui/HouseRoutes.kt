package com.kurtz.homerecord.housemodel.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.coreui.platform.Intents
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.R
import com.kurtz.homerecord.housemodel.blueprint.BlueprintPin
import com.kurtz.homerecord.housemodel.blueprint.LayerStore
import com.kurtz.homerecord.housemodel.plan.PlanRoom

@Composable
fun FloorsRoute(repo: HomeRepository, homeId: String, homeName: String, onBack: () -> Unit) {
    val vm = viewModel<FloorsViewModel>(factory = viewModelFactory { initializer { FloorsViewModel(repo, homeId) } })
    FloorsScreen(vm.state.collectAsState().value, vm::onEvent, onBack, homeName)
}

@Composable
fun HouseRoute(
    repo: HomeRepository,
    layerStore: LayerStore,
    homeId: String,
    homeName: String,
    summary: String,
    onOpenRoom: (PlanRoom) -> Unit,
    onEditPlan: (floorId: String?) -> Unit,
    onEditShape: (floorId: String?, PlanRoom) -> Unit,
    onOpenFloors: () -> Unit,
    modifier: Modifier = Modifier,
    /** Task pins for a floor (FL-House, AC-F35). */
    pins: (floorId: String?) -> List<BlueprintPin> = { emptyList() },
    onOpenPin: (String) -> Unit = {},
    /** Opens HM-3D on the floor in view (AC-C20). */
    on3D: ((floorId: String?) -> Unit)? = null,
    /** Draws the floor's plan overlays on the blueprint (AC-C25). */
    overlays: com.kurtz.homerecord.housemodel.overlays.OverlayRepository? = null,
    /** Field Log's "Behind the wall" capture (AC-F49). */
    onBehindWall: (() -> Unit)? = null,
) {
    val context = LocalContext.current.applicationContext
    val vm = viewModel<HouseViewModel>(
        factory = viewModelFactory {
            initializer {
                HouseViewModel(
                    repo, homeId, layerStore, createSavedStateHandle(), homeName, summary, exporter = PdfFileExporter(context),
                    homeOverlays = overlays?.observeForHome(homeId) ?: kotlinx.coroutines.flow.flowOf(emptyList()),
                    wallKeys = { ids -> overlays?.observeWallKeys(ids) ?: kotlinx.coroutines.flow.flowOf(emptyMap()) },
                    itemSpots = overlays?.observeItemSpots(homeId) ?: kotlinx.coroutines.flow.flowOf(emptyList()),
                )
            }
        },
    )
    val state = vm.state.collectAsState().value
    val activity = LocalContext.current
    val shareTitle = stringResource(R.string.house_export_share_title)
    LaunchedEffect(state.shareUri, state.exportFailed) {
        val uri = state.shareUri
        if (uri != null) {
            Intents.shareUri(activity, Uri.parse(uri), "application/pdf", shareTitle)
            vm.onEvent(HouseEvent.ExportHandled)
        } else if (state.exportFailed) {
            Toast.makeText(activity, R.string.house_export_failed, Toast.LENGTH_LONG).show()
            vm.onEvent(HouseEvent.ExportHandled)
        }
    }
    HouseScreen(
        state = state,
        onEvent = vm::onEvent,
        onOpenRoom = onOpenRoom,
        onEditPlan = { onEditPlan(state.floorId) },
        onEditShape = { onEditShape(state.floorId, it) },
        onOpenFloors = onOpenFloors,
        modifier = modifier,
        pins = pins(state.floorId),
        onOpenPin = onOpenPin,
        on3D = on3D?.let { open -> { open(state.floorId) } },
        onBehindWall = onBehindWall,
    )
}
