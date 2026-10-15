package com.kurtz.homerecord.housemanual.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kurtz.homerecord.housemanual.home.HomeRoute
import com.kurtz.homerecord.housemanual.home.HomeViewModel
import com.kurtz.homerecord.housemanual.panel.PanelRoute
import com.kurtz.homerecord.housemanual.panel.PanelViewModel
import com.kurtz.homerecord.housemanual.rooms.RoomRoute
import com.kurtz.homerecord.housemanual.rooms.RoomViewModel
import com.kurtz.homerecord.housemanual.rooms.RoomsRoute
import com.kurtz.homerecord.housemanual.rooms.RoomsViewModel
import com.kurtz.homerecord.housemanual.AppContainer
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.onboarding.OnboardingRoute
import com.kurtz.homerecord.housemanual.onboarding.OnboardingViewModel
import com.kurtz.homerecord.housemanual.onboarding.QuickRoom

@Composable
fun HomeRecordNavHost(container: AppContainer) {
    val start by produceState<String?>(initialValue = null, container) {
        value = StartDestination.resolve(container.homeRepository)
    }
    val startRoute = start ?: return
    val nav = rememberNavController()
    val resources = androidx.compose.ui.platform.LocalResources.current
    NavHost(navController = nav, startDestination = startRoute) {
        composable(Routes.ONBOARDING) {
            val vm: OnboardingViewModel = viewModel(
                factory = remember {
                    simpleFactory { OnboardingViewModel(container.homeRepository) { room: QuickRoom -> resources.getString(room.label) } }
                },
            )
            OnboardingRoute(vm, onDone = {
                nav.navigate(Routes.PANEL) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(
                factory = remember {
                    simpleFactory {
                        HomeViewModel(container.homeRepository, container.panelRepository, container.shutoffRepository, container.equipmentRepository)
                    }
                },
            )
            HomeRoute(
                vm,
                onPanel = { nav.navigate(Routes.PANEL) },
                onEmergency = { nav.navigate(Routes.EMERGENCY) },
                onEquipment = { nav.navigate(Routes.EQUIPMENT) },
                onRooms = { nav.navigate(Routes.ROOMS) },
            )
        }
        composable(Routes.ROOMS) {
            val vm: RoomsViewModel = viewModel(factory = remember { simpleFactory { RoomsViewModel(container.homeRepository) } })
            RoomsRoute(vm, onOpenRoom = { nav.navigate(Routes.room(it)) })
        }
        composable(Routes.ROOM, arguments = listOf(navArgument("roomId") { type = NavType.StringType })) { entry ->
            val roomId = entry.arguments?.getString("roomId").orEmpty()
            val vm: RoomViewModel = viewModel(
                factory = remember(roomId) {
                    simpleFactory {
                        RoomViewModel(roomId, container.homeRepository, container.panelRepository, container.shutoffRepository, container.equipmentRepository)
                    }
                },
            )
            RoomRoute(vm, onDeleted = { nav.popBackStack() })
        }
        // Placeholders until T11/T12 (shutoffs, emergency) and equipment land.
        composable(Routes.EMERGENCY) { CenteredText(stringResource(R.string.placeholder_emergency), tag = "emergency_screen") }
        composable(Routes.EQUIPMENT) { CenteredText(stringResource(R.string.placeholder_equipment), tag = "equipment_screen") }
        composable(Routes.PANEL) {
            val vm: PanelViewModel = viewModel(
                factory = remember { simpleFactory { PanelViewModel(container.homeRepository, container.panelRepository, resources.getString(R.string.panel_title)) } },
            )
            PanelRoute(vm, container.mediaStore, onSlot = { /* HM-Breaker arrives in T07 */ })
        }
    }
}

@Composable
private fun CenteredText(text: String, tag: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, modifier = Modifier.testTag(tag))
    }
}
