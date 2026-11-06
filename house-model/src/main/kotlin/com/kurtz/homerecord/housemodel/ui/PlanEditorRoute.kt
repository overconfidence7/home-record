package com.kurtz.homerecord.housemodel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.media.MediaFiles
import com.kurtz.homerecord.housemodel.HomeRepository
import com.kurtz.homerecord.housemodel.overlays.OverlayRepository
import com.kurtz.homerecord.housemodel.walls.WallStage

/**
 * HM-Plan. With [overlays] the editor has an Overlays mode (AC-C25, AC-C28) recording new overlays at [overlayStage], with
 * a photo kept in [media]; [overlayId] opens that mode on the overlay's floor with it picked. Back steps out of the overlay
 * form, the drawing or the picked overlay before it leaves the editor.
 */
@Composable
fun PlanEditorRoute(
    repo: HomeRepository,
    homeId: String,
    homeName: String,
    onBack: () -> Unit,
    onOpenFloors: () -> Unit,
    floorId: String? = null,
    roomId: String? = null,
    editShape: Boolean = false,
    overlays: OverlayRepository? = null,
    media: MediaFiles? = null,
    overlayStage: WallStage = WallStage.HouseManualDefault,
    overlayId: String? = null,
    /** A viewer of a shared home (AC-H16): the plan opens read only in Overlays mode, [overlayId] picked; changes are dropped. */
    viewOnly: Boolean = false,
) {
    val system = LocalUnitSystem.current
    val vm = viewModel<PlanEditorViewModel>(
        factory = viewModelFactory {
            initializer {
                PlanEditorViewModel(
                    repo, homeId, createSavedStateHandle(), initialFloorId = floorId, initialRoomId = roomId, initialEditShape = editShape,
                    overlays = overlays, media = media, overlayStage = overlayStage, initialOverlayId = overlayId, system = system,
                )
            }
        },
    )
    // Leaving the editor (back arrow, system back, Floors) keeps a name or size still being typed.
    DisposableEffect(vm) { onDispose { if (!viewOnly) vm.onEvent(PlanEditorEvent.CommitFields) } }
    val state = vm.state.collectAsState().value.let { if (viewOnly) it.copy(viewOnly = true, overlayMode = true) else it }.copy(system = system)
    val onOverlay: (WallOverlaysEvent) -> Unit = { vm.onEvent(PlanEditorEvent.OverlayEvent(it)) }
    OverlayPhotoLaunchers(state.photoLaunch, onOverlay)
    // The overlay form's own handler, composed later, asks before discarding changes.
    BackHandler(enabled = state.overlayForm != null || (state.overlayMode && (state.drawing != null || state.overlaySelectedId != null))) {
        onOverlay(
            when {
                state.overlayForm != null -> WallOverlaysEvent.CloseForm
                state.drawing != null -> WallOverlaysEvent.CancelDrawing
                else -> WallOverlaysEvent.Select(null)
            },
        )
    }
    PlanEditorScreen(state, { if (!viewOnly || PlanViewOnly.allows(it)) vm.onEvent(it) }, onBack, homeName, onOpenFloors)
}

/** What a viewer may do on the plan (AC-H16): change floor, pan, zoom, fit, and pick overlays; every edit is dropped. */
internal object PlanViewOnly {
    fun allows(event: PlanEditorEvent): Boolean = when (event) {
        is PlanEditorEvent.SelectFloor, is PlanEditorEvent.Transform, is PlanEditorEvent.Fit, is PlanEditorEvent.Reveal -> true
        is PlanEditorEvent.OverlayEvent -> WallViewOnly.allows(event.event)
        else -> false
    }
}
