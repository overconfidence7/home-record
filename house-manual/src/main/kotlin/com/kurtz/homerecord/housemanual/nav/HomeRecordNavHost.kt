package com.kurtz.homerecord.housemanual.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NamedNavArgument
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.getAndUpdate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable as navComposable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kurtz.homerecord.housemanual.breaker.BreakerRoute
import com.kurtz.homerecord.housemanual.breaker.BreakerViewModel
import com.kurtz.homerecord.housemanual.emergency.EmergencyRoute
import com.kurtz.homerecord.housemanual.emergency.EmergencyViewModel
import com.kurtz.homerecord.housemanual.equipment.EquipmentEditRoute
import com.kurtz.homerecord.housemanual.equipment.EquipmentEditViewModel
import com.kurtz.homerecord.housemanual.equipment.EquipmentRoute
import com.kurtz.homerecord.housemanual.equipment.EquipmentViewModel
import com.kurtz.homerecord.housemanual.paywall.PaywallRoute
import com.kurtz.homerecord.housemanual.paywall.PaywallViewModel
import com.kurtz.homerecord.housemanual.home.HomeRoute
import com.kurtz.homerecord.housemanual.imports.AndroidBundleSource
import com.kurtz.homerecord.housemanual.imports.ImportRoute
import com.kurtz.homerecord.housemanual.settings.AndroidBackupExporter
import com.kurtz.homerecord.housemanual.settings.SettingsRoute
import com.kurtz.homerecord.housemanual.settings.SettingsViewModel
import com.kurtz.homerecord.housemanual.imports.ImportViewModel
import com.kurtz.homerecord.housemanual.mapping.MappingRoute
import com.kurtz.homerecord.housemanual.mapping.MappingViewModel
import com.kurtz.homerecord.housemanual.home.HomeViewModel
import com.kurtz.homerecord.housemanual.reminders.RemindersRoute
import com.kurtz.homerecord.housemanual.reminders.RemindersViewModel
import com.kurtz.homerecord.housemanual.rooms.labelRes
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

/** Opaque destination so transitions never show the previous screen through (and dark mode stays dark). */
private fun NavGraphBuilder.composable(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable (NavBackStackEntry) -> Unit,
) {
    navComposable(route, arguments) { entry ->
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content(entry) }
    }
}

@Composable
fun HomeRecordNavHost(container: AppContainer, emergencyRequests: Int = 0, reminderRequest: ReminderRequest? = null) {
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
    // Reminder notification deep link (AC-83).
    androidx.compose.runtime.LaunchedEffect(reminderRequest, startRoute) {
        if (reminderRequest != null && startRoute != Routes.ONBOARDING) {
            nav.navigate(Routes.reminders(reminderRequest.taskId)) { launchSingleTop = true }
        }
    }
    val pendingImport by container.pendingImportUri.collectAsState()
    androidx.compose.runtime.LaunchedEffect(pendingImport, startRoute) {
        if (pendingImport != null) {
            nav.navigate(Routes.IMPORT) { launchSingleTop = true }
        }
    }
    NavHost(navController = nav, startDestination = startRoute) {
        composable(Routes.IMPORT) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val vm: ImportViewModel = viewModel(
                factory = remember {
                    simpleFactory {
                        // Consume the shared URI exactly once so re-entering HM-Import starts at the picker.
                        val uri = container.pendingImportUri.getAndUpdate { null }
                        ImportViewModel(container.importApplier, AndroidBundleSource(context.applicationContext), uri)
                    }
                },
            )
            ImportRoute(vm, onDone = {
                // A restore from onboarding creates the Home, so land on the hub instead of the setup form.
                val previous = nav.previousBackStackEntry?.destination?.route
                if (previous == null || previous == Routes.ONBOARDING) {
                    nav.navigate(Routes.HOME) { popUpTo(0) }
                } else {
                    nav.popBackStack()
                }
            })
        }
        composable(Routes.SETTINGS) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val vm: SettingsViewModel = viewModel(
                factory = remember {
                    simpleFactory {
                        SettingsViewModel(container.homeRepository, AndroidBackupExporter(context.applicationContext, container.backupRepository))
                    }
                },
            )
            SettingsRoute(vm, onImport = { nav.navigate(Routes.IMPORT) }, onReminders = { nav.navigate(Routes.reminders()) })
        }
        composable(Routes.ONBOARDING) {
            val vm: OnboardingViewModel = viewModel(
                factory = remember {
                    simpleFactory { OnboardingViewModel(container.homeRepository) { room: QuickRoom -> resources.getString(room.label) } }
                },
            )
            OnboardingRoute(vm, onRestore = { nav.navigate(Routes.IMPORT) }, onDone = {
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
                onImport = { nav.navigate(Routes.IMPORT) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
                onReminders = { nav.navigate(Routes.reminders()) },
                onSlot = { slot, panelId -> nav.navigate(Routes.panelAt(slot, panelId)) },
                onRoom = { nav.navigate(Routes.room(it)) },
            )
        }
        composable(
            Routes.REMINDERS_PATTERN,
            arguments = listOf(navArgument("highlight") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            val highlight = entry.arguments?.getString("highlight")?.takeIf { it.isNotEmpty() }
            val vm: RemindersViewModel = viewModel(
                factory = remember(highlight) { simpleFactory { RemindersViewModel(container.homeRepository, container.maintenanceRepository, highlight) } },
            )
            RemindersRoute(vm)
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
            RoomRoute(vm, container.mediaStore, onDeleted = { nav.popBackStack() })
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
        composable(Routes.EQUIPMENT) {
            val vm: EquipmentViewModel = viewModel(
                factory = remember { simpleFactory { EquipmentViewModel(container.homeRepository, container.equipmentRepository) } },
            )
            EquipmentRoute(
                vm,
                onAdd = { nav.navigate(Routes.equipmentEdit(null)) },
                onOpen = { nav.navigate(Routes.equipmentEdit(it)) },
            )
        }
        composable(
            Routes.EQUIPMENT_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            val id = entry.arguments?.getString("id")?.takeIf { it.isNotEmpty() }
            val vm: EquipmentEditViewModel = viewModel(
                factory = remember(id) {
                    savedStateFactory { handle -> EquipmentEditViewModel(
                        id, container.homeRepository, container.equipmentRepository, container.mediaStore, container.textRecognizer,
                        container.maintenanceRepository, container.limitGate, handle,
                    ) { e -> listOfNotNull(e.make, e.model).joinToString(" ").ifBlank { resources.getString(e.category.labelRes()) } } }
                },
            )
            EquipmentEditRoute(vm, container.mediaStore, onPaywall = { nav.navigate(Routes.PAYWALL) }, onDone = { nav.popBackStack() })
        }
        composable(
            Routes.PANEL_PATTERN,
            arguments = listOf(
                navArgument("highlight") { type = NavType.IntType; defaultValue = -1 },
                navArgument("panelId") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val highlight = entry.arguments?.getInt("highlight")?.takeIf { it > 0 }
            val panelId = entry.arguments?.getString("panelId")?.takeIf { it.isNotBlank() }
            val vm: PanelViewModel = viewModel(
                factory = remember { simpleFactory { PanelViewModel(container.homeRepository, container.panelRepository, container.loadProfileRepository, container.limitGate, resources.getString(R.string.panel_title), resources.getString(R.string.panel_sub_default_name), panelId) } },
            )
            PanelRoute(vm, container.mediaStore, highlightSlot = highlight, onOpenBreaker = { panelId, e -> nav.navigate(Routes.breaker(panelId, e.slot, e.breakerId, e.tandemIndex)) }, onMapping = { nav.navigate(Routes.MAPPING) }, onPaywall = { nav.navigate(Routes.PAYWALL) })
        }
        composable(Routes.PAYWALL) {
            val vm: PaywallViewModel = viewModel(factory = remember { simpleFactory { PaywallViewModel(container.entitlements) } })
            PaywallRoute(vm, onClose = { nav.popBackStack() })
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
