package com.kurtz.homerecord.housemanual.mapping

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.core.mapping.MappingAlarm
import com.kurtz.homerecord.core.mapping.PowerMonitor
import com.kurtz.homerecord.housemodel.items.ItemRepository
import com.kurtz.homerecord.housemodel.panel.PanelRepository
import kotlinx.coroutines.flow.map

@Composable
fun MappingRoute(repo: PanelRepository, items: ItemRepository, power: PowerMonitor, alarm: MappingAlarm, homeId: String, itemId: String, onBack: () -> Unit) {
    val vm = viewModel<MappingViewModel>(
        key = "mapping:$itemId",
        factory = viewModelFactory {
            initializer { MappingViewModel(repo, itemId, items.observeItem(itemId).map { it?.name }, repo.observeChoices(homeId), power, alarm) }
        },
    )
    val state = vm.state.collectAsState().value
    val view = LocalView.current
    DisposableEffect(state.keepScreenOn) {
        view.keepScreenOn = state.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    // No alarm once the screen is gone: back, or the app sent to the background (AC-C42).
    val activity = LocalActivity.current
    val rotating = { activity?.isChangingConfigurations == true }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP && !rotating()) vm.leave() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (!rotating()) vm.leave()
        }
    }
    MappingScreen(state, onEvent = {
        when (it) {
            MappingEvent.Back -> onBack()
            else -> vm.onEvent(it)
        }
    })
}
