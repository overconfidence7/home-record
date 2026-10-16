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
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kurtz.homerecord.housemanual.breaker.BreakerRoute
import com.kurtz.homerecord.housemanual.breaker.BreakerViewModel
import com.kurtz.homerecord.housemanual.emergency.EmergencyRoute
import com.kurtz.homerecord.housemanual.emergency.EmergencyViewModel
import com.kurtz.homerecord.housemanual.home.HomeRoute
import com.kurtz.homerecord.housemanual.mapping.MappingRoute
import com.kurtz.homerecord.housemanual.mapping.MappingViewModel
import com.kurtz.homerecord.housemanual.home.HomeViewModel
import com.kurtz.homerecord.housemanual.panel.PanelRoute
import com.kurtz.homerecord.housemanual.panel.PanelViewModel
import com.kurtz.homerecord.housemanual.rooms.RoomRoute
import com.kurtz.homerecord.housemanual.rooms.RoomViewModel
import com.kurtz.homerecord.housemanual.rooms.RoomsRoute
import com.kurtz.homerecord.housemanual.rooms.RoomsViewModel
import com.kurtz.homerecord.housemanual.AppContainer
import com.kurtz.homerecord.housemanual.shutoffs.ShutoffEditRoute
import com.kurtz.homerecord.housemanual.shutoffs.ShutoffEditViewModel
import com.kurtz.homerecord.housemanual.shutoffs.ShutoffsRoute
import com.kurtz.homerecord.housemanual.shutoffs.ShutoffsViewModel
import com.kurtz.homerecord.housemanual.R
import com.kurtz.homerecord.housemanual.onboarding.OnboardingRoute
import com.kurtz.homerecord.housemanual.onboarding.OnboardingViewModel
import com.kurtz.homerecord.housemanual.onboarding.QuickRoom

@Composable
fun HomeRecordNavHost(container: AppContainer, emergencyRequests: Int = 0) {
    val start by produceState<String?>(initialValue = null, container) {
        value = StartDestination.resolve(container.homeRepository)
    }
    val startRoute = start ?: return
    val nav = rememberNavController()
    val resources = androidx.compose.ui.platform.LocalResources.current
    // Quick Settings tile deep link (AC-33): only meaningful once a home exists.
    androidx.compose.runtime.LaunchedEffect(emergencyRequests, startRoute) {
        if (emergencyRequests > 0 && startRoute != Routes.ONBOARDING) {
            nav.navigate(Routes.EMERGENCY) { launchSingleTop = true }
        }
    }
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
                onSlot = { nav.navigate(Routes.panelAt(it)) },
                onRoom = { nav.navigate(Routes.room(it)) },
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
        composable(Routes.EMERGENCY) {
            val vm: EmergencyViewModel = viewModel(
                factory = remember { simpleFactory { EmergencyViewModel(container.homeRepository, container.shutoffRepository) } },
            )
            EmergencyRoute(vm, container.mediaStore, onManage = { nav.navigate(Routes.SHUTOFFS) })
        }
        composable(Routes.SHUTOFFS) { ShutoffsDestination(container, nav) }
        composable(
            Routes.SHUTOFF_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            val id = entry.arguments?.getString("id")?.takeIf { it.isNotEmpty() }
            val vm: ShutoffEditViewModel = viewModel(
                factory = remember(id) {
                    simpleFactory { ShutoffEditViewModel(id, container.homeRepository, container.shutoffRepository, container.mediaStore) }
                },
            )
            ShutoffEditRoute(vm, container.mediaStore, onDone = { nav.popBackStack() })
        }
        // Placeholder until equipment lands.
        composable(Routes.EQUIPMENT) { CenteredText(stringResource(R.string.placeholder_equipment), tag = "equipment_screen") }
        composable(
            Routes.PANEL_PATTERN,
            arguments = listOf(navArgument("highlight") { type = NavType.IntType; defaultValue = -1 }),
        ) { entry ->
            val highlight = entry.arguments?.getInt("highlight")?.takeIf { it > 0 }
            val vm: PanelViewModel = viewModel(
                factory = remember { simpleFactory { PanelViewModel(container.homeRepository, container.panelRepository, resources.getString(R.string.panel_title)) } },
            )
            PanelRoute(vm, container.mediaStore, highlightSlot = highlight, onOpenBreaker = { panelId, e -> nav.navigate(Routes.breaker(panelId, e.slot, e.breakerId, e.tandemIndex)) }, onMapping = { nav.navigate(Routes.MAPPING) })
        }
        composable(Routes.MAPPING) {
            val vm: MappingViewModel = viewModel(
                factory = remember {
                    simpleFactory {
                        MappingViewModel(container.panelRepository, container.homeRepository, container.chargingMonitor, container.alarmPlayer)
                    }
                },
            )
            MappingRoute(vm)
        }
        composable(
            Routes.BREAKER,
            arguments = listOf(
                navArgument("panelId") { type = NavType.StringType },
                navArgument("slot") { type = NavType.IntType },
                navArgument("breakerId") { type = NavType.StringType; defaultValue = "" },
                navArgument("tandem") { type = NavType.IntType; defaultValue = -1 },
            ),
        ) { entry ->
            val panelId = entry.arguments?.getString("panelId").orEmpty()
            val slot = entry.arguments?.getInt("slot") ?: 1
            val breakerId = entry.arguments?.getString("breakerId")?.takeIf { it.isNotEmpty() }
            val tandem = entry.arguments?.getInt("tandem")?.takeIf { it >= 0 }
            val vm: BreakerViewModel = viewModel(
                factory = remember(panelId, slot, breakerId, tandem) {
                    simpleFactory { BreakerViewModel(panelId, slot, breakerId, tandem, container.panelRepository, container.homeRepository) }
                },
            )
            BreakerRoute(vm, onDone = { nav.popBackStack() })
        }
    }
}

@Composable
private fun ShutoffsDestination(container: AppContainer, nav: NavHostController) {
    val vm: ShutoffsViewModel = viewModel(
        factory = remember { simpleFactory { ShutoffsViewModel(container.homeRepository, container.shutoffRepository) } },
    )
    ShutoffsRoute(
        vm,
        container.mediaStore,
        onAdd = { nav.navigate(Routes.shutoffEdit(null)) },
        onOpen = { nav.navigate(Routes.shutoffEdit(it)) },
    )
}

@Composable
private fun CenteredText(text: String, tag: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, modifier = Modifier.testTag(tag))
    }
}
