package com.kurtz.homerecord.housemanual

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import com.kurtz.homerecord.housemanual.contacts.ContactsRoute
import com.kurtz.homerecord.housemanual.paywall.PaywallRoute
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import com.kurtz.homerecord.housemodel.overlays.OverlayTarget
import androidx.compose.runtime.rememberCoroutineScope
import com.kurtz.homerecord.housemanual.documents.DocumentViewerRoute
import com.kurtz.homerecord.housemanual.documents.DocumentsRoute
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.housemodel.ui.PlanEditorRoute
import com.kurtz.homerecord.housemodel.ui.FloorsRoute
import com.kurtz.homerecord.housemodel.ui.HouseRoute
import com.kurtz.homerecord.housemodel.ui.RoomRoute
import com.kurtz.homerecord.housemodel.ui.ItemEditRoute
import com.kurtz.homerecord.housemodel.ui.ItemPlaceRoute
import com.kurtz.homerecord.housemanual.home.HomeRoute
import com.kurtz.homerecord.housemanual.items.ItemDetailRoute
import com.kurtz.homerecord.housemanual.items.ItemsRoute
import com.kurtz.homerecord.housemanual.onboarding.foundationLabel
import com.kurtz.homerecord.housemanual.onboarding.houseTypeLabel
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemanual.house.OnboardingRoute
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kurtz.homerecord.coreui.auth.AccountRoute
import com.kurtz.homerecord.housemanual.more.MoreEvent
import com.kurtz.homerecord.housemanual.more.MoreScreen
import com.kurtz.homerecord.core.billing.LimitedFeature
import com.kurtz.homerecord.coreui.components.SyncStatus
import com.kurtz.homerecord.coreui.shell.AppTab
import com.kurtz.homerecord.coreui.shell.HomeRecordApp
import com.kurtz.homerecord.coreui.shell.PlaceholderDetail
import com.kurtz.homerecord.coreui.shell.PlaceholderKey
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag

private data class TabDef(val id: String, val label: Int, val icon: ImageVector, val selected: ImageVector)

private fun tab(id: String, label: Int, icon: ImageVector, selected: ImageVector) = TabDef(id, label, icon, selected)

private val tabDefs = listOf(
        tab("home", R.string.tab_home, Icons.Outlined.Home, Icons.Rounded.Home),
        tab("house", R.string.tab_house, Icons.Outlined.Cottage, Icons.Rounded.Cottage),
        tab("items", R.string.tab_items, Icons.Outlined.Inventory2, Icons.Rounded.Inventory2),
        tab("reminders", R.string.tab_reminders, Icons.Outlined.Notifications, Icons.Rounded.Notifications),
        tab("more", R.string.tab_more, Icons.Outlined.MoreHoriz, Icons.Rounded.MoreHoriz),
)

/** No home yet shows onboarding; once one exists the tabs take over (AC-C1, AC-H1). */
@Composable
fun HouseManualApp(container: AppContainer, incoming: android.net.Uri? = null, onIncomingHandled: () -> Unit = {}) {
    var home by remember { mutableStateOf<HomeEntity?>(null) }
    // A `.homerecord` handoff opened from another app or picked from More (AC-H17); shown over onboarding or the tabs.
    var importUri by rememberSaveable { mutableStateOf<String?>(null) }
    // A claim link (`homerecord://handoff/<token>`) from the handoff email page; downloads, then opens the preview.
    var claimToken by rememberSaveable { mutableStateOf<String?>(null) }
    // A `.hrbackup` picked on onboarding or HM-Backup (AC-C48); its passphrase prompt and preview go over everything.
    var restoreUri by rememberSaveable { mutableStateOf<String?>(null) }
    // Onboarding → "Sign in to get your home" (AC-H15 second phone); closes on its own once the home arrives.
    var signingIn by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(incoming) {
        if (incoming != null) {
            val token = com.kurtz.homerecord.core.bundle.HandoffLink.token(incoming.toString())
            if (token != null) {
                claimToken = token
                importUri = null
            } else if (incoming.scheme != "homerecord") {
                importUri = incoming.toString()
            }
            onIncomingHandled()
        }
    }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(container) {
        container.homes.observeHome().collect { home = it; loaded = true }
    }
    val current = home
    LaunchedEffect(current != null) { if (current != null) signingIn = false }
    // Set by "Draw your floor plan" so the new home opens straight on the plan editor, House tab.
    var openEditor by rememberSaveable { mutableStateOf(false) }
    val pendingImport = importUri
    val pendingClaim = claimToken
    val pendingRestore = restoreUri
    when {
        pendingRestore != null -> com.kurtz.homerecord.housemanual.importing.RestoreRoute(
            container.importFiles, android.net.Uri.parse(pendingRestore), onClose = { restoreUri = null },
        )
        pendingClaim != null -> com.kurtz.homerecord.housemanual.importing.ClaimRoute(
            container.auth, container.handoffClaims, LocalContext.current.cacheDir, pendingClaim,
            onReady = { file ->
                claimToken = null
                importUri = file.toString()
            },
            onClose = { claimToken = null },
        )
        pendingImport != null -> com.kurtz.homerecord.housemanual.importing.ImportRoute(
            container.importFiles, android.net.Uri.parse(pendingImport), onClose = { importUri = null },
        )
        !loaded -> Scaffold { LoadingState(Modifier.padding(it)) }
        current == null && signingIn -> com.kurtz.homerecord.housemanual.account.CloudRestoreRoute(container, onClose = { signingIn = false })
        current == null -> OnboardingRoute(
            container.homes, onDrawPlan = { openEditor = true }, onRestore = { restoreUri = it.toString() }, onSignIn = { signingIn = true },
        )
        else -> HouseManualTabs(container, current, openEditor, onImport = { importUri = it.toString() }, onRestore = { restoreUri = it.toString() })
    }
}

@Composable
private fun HouseManualTabs(
    container: AppContainer,
    home: HomeEntity,
    openEditor: Boolean = false,
    onImport: (android.net.Uri) -> Unit = {},
    onRestore: (android.net.Uri) -> Unit = {},
) {
    val pickHandoff = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) onImport(uri) }
    val tabs = tabDefs.map {
        AppTab(it.id, stringResource(it.label), it.icon, it.selected, when (it.id) { "home" -> HomeKey; "house" -> HouseKey; "items" -> ItemsKey; "reminders" -> RemindersKey; else -> PlaceholderKey(it.id) })
    }
    val titles = tabDefs.associate { it.id to stringResource(it.label) }
    val scope = rememberCoroutineScope()
    AskNotificationsOnceRemindersExist(container, home.id)
    HomeRecordApp(
        tabs = tabs,
        startTabId = if (openEditor) "house" else null,
        startKeys = if (openEditor) listOf(PlanKey()) else emptyList(),
        tabRequests = container.tabRequests.flow,
        fullScreen = ::isFullScreen,
        entries = { nav ->
        entry<HomeKey> {
            HomeRoute(
                container.items, container.search, home.id, home.name,
                onAddItem = { addItem(scope, container, home.id, nav) },
                onScanDataPlate = { addItem(scope, container, home.id, nav, scanPlate = true) },
                onAddShutoff = { addItem(scope, container, home.id, nav, subtypeId = "water_main") }, onOpenItem = { nav.push(ItemDetailKey(it)) },
                onOpenRoom = { nav.push(RoomKey(it)) }, onOpenDocument = { nav.push(DocumentViewerKey(it)) }, onOpenContacts = { nav.push(ContactsKey) },
                onOpenEmergency = { nav.push(EmergencyKey) },
                onOpenReminders = { container.tabRequests.request("reminders") },
                onOpenOverlay = { id ->
                    scope.launch { container.overlays.get(id)?.let { openOverlay(nav, it) } }
                },
                onOpenPanel = { panelItemId, slot -> nav.push(PanelKey(panelItemId, highlightSlot = slot)) },
            )
        }
        entry<HouseKey> {
            val summary = stringResource(
                R.string.house_summary,
                houseTypeLabel(HouseType.parse(home.houseType)),
                foundationLabel(Foundation.parse(home.foundation)),
            )
            HouseRoute(
                repo = container.homes,
                layerStore = container.layers,
                homeId = home.id,
                homeName = home.name,
                summary = summary,
                onOpenRoom = { nav.push(RoomKey(it.id)) },
                onEditPlan = { nav.push(PlanKey(floorId = it)) },
                onEditShape = { floorId, room -> nav.push(PlanKey(floorId, room.id, editShape = true)) },
                onOpenFloors = { nav.push(FloorsKey) },
                on3D = { nav.push(House3DKey(it)) },
                overlays = container.overlays,
                onOpenOverlay = { openOverlay(nav, it) },
                onOpenItem = { itemId, _ -> nav.push(ItemDetailKey(itemId)) },
            )
        }
        entry<House3DKey> { key ->
            com.kurtz.homerecord.housemodel.ui.House3DRoute(
                container.homes, container.wallPhotos, home.id, home.name, key.floorId, onBack = nav::pop,
                onEditPlan = { nav.push(PlanKey(floorId = it)) },
                roomId = key.roomId,
                overlays = container.overlays,
                layerStore = container.layers,
                onOpenOverlay = { openOverlay(nav, it) },
                onOpenItem = { itemId, _ -> nav.push(ItemDetailKey(itemId)) },
            )
        }
        entry<PlanKey> { key ->
            PlanEditorRoute(
                container.homes, home.id, home.name, onBack = nav::pop, onOpenFloors = { nav.push(FloorsKey) },
                floorId = key.floorId, roomId = key.roomId, editShape = key.editShape,
                overlays = container.overlays, media = container.media, overlayId = key.overlayId,
            )
        }
        entry<RemindersKey> { com.kurtz.homerecord.housemanual.reminders.RemindersRoute(container.reminders, home.id, onOpenItem = { nav.push(ItemDetailKey(it)) }) }
        entry<ItemsKey> { ItemsRoute(container.items, home.id, onAdd = { addItem(scope, container, home.id, nav) }, onOpen = { nav.push(ItemDetailKey(it)) }) }
        entry<ItemDetailKey> { key ->
            ItemDetailRoute(
                container.items, container.warrantyLookup, container.upkeep, container.documents, home.id, key.itemId, onBack = nav::pop, onEdit = { nav.push(ItemEditKey(itemId = key.itemId)) },
                onOpenDocument = { nav.push(DocumentViewerKey(it)) },
                onOpenCopy = { nav.pop(); nav.push(ItemDetailKey(it)) },
                recalls = container.lookupApi,
                overlays = container.overlays,
                onOpenOverlay = { link -> openOverlay(nav, link.overlay) },
                onPlace = { nav.push(ItemPlaceKey(key.itemId)) },
                onOpenPanel = { nav.push(PanelKey(key.itemId)) },
                panels = container.panels,
                onOpenBreaker = { nav.push(PanelKey(it.panelItemId, highlightSlot = it.breaker.number)) },
                onOpenItem = { nav.push(ItemDetailKey(it)) },
                roomChoices = { container.homes.roomChoices(home.id) },
                onOpenRoom = { nav.push(RoomKey(it)) },
                reminders = container.reminders,
            )
        }
        entry<ItemPlaceKey> { key ->
            ItemPlaceRoute(
                container.homes, container.items, home.id, key.itemId, onBack = nav::pop, onEditPlan = { nav.push(PlanKey(floorId = it)) },
                roomWalls = container.wallPhotos::observeRoomWalls, homeItems = container.overlays.observeItemSpots(home.id),
            )
        }
        entry<ItemEditKey> { key ->
            ItemEditRoute(container.items, container.homes, container.media, container.textRecognizer, container.lookupApi, home.id, key.itemId, key.roomId, key.subtypeId, key.scanPlate, onClose = nav::pop, afterCreate = container::autoLookupWarranty,
                saveBlocked = { container.addItemGate.saveBlocked(home.id, it) }, onLimit = { nav.push(PaywallKey(LimitedFeature.SecondPanel)) },
            )
        }
        entry<PaywallKey> { key -> PaywallRoute(container.billing, key.feature, onClose = nav::pop) }
        entry<PanelKey> { key -> com.kurtz.homerecord.housemanual.panel.PanelRoute(container.panels, container.items, container.media, home.id, key.itemId, onBack = nav::pop, highlightSlot = key.highlightSlot, onMap = { nav.push(MappingKey(key.itemId)) }) }
        entry<MappingKey> { key -> com.kurtz.homerecord.housemanual.mapping.MappingRoute(container.panels, container.items, container.power, container.mappingAlarm, home.id, key.itemId, onBack = nav::pop) }
        entry<PanelsKey> {
            com.kurtz.homerecord.housemanual.panel.PanelsRoute(
                container.items, home.id, onBack = nav::pop, onOpen = { nav.push(PanelKey(it)) },
                onAdd = { addItem(scope, container, home.id, nav, subtypeId = "breaker_panel") },
            )
        }
        entry<EmergencyKey> {
            com.kurtz.homerecord.housemanual.emergency.EmergencyRoute(
                container.items, home.id, onBack = nav::pop, onOpen = { nav.push(ItemDetailKey(it)) },
                onAdd = { addItem(scope, container, home.id, nav, subtypeId = "water_main") },
            )
        }
        entry<BackupKey> {
            com.kurtz.homerecord.housemanual.backup.BackupRoute(container.backupFiles, home.id, home.name, onRestore = onRestore, onBack = nav::pop)
        }
        entry<AccountKey> {
            val cloud = viewModel<com.kurtz.homerecord.housemanual.account.CloudCopyViewModel>(
                factory = viewModelFactory { initializer { com.kurtz.homerecord.housemanual.account.CloudCopyViewModel(home.id, container.cloudCopy::delete, createSavedStateHandle()) } },
            )
            val cloudState by cloud.state.collectAsState()
            com.kurtz.homerecord.housemanual.account.AccountSyncScreen(onEvent = { nav.pop() }, cloudDeleted = cloudState.deleted) { accountModifier ->
                AccountRoute(
                    container.auth, accountModifier, loadEmail = container.account::email,
                    footer = { com.kurtz.homerecord.housemanual.account.CloudCopySection(cloudState, cloud::onEvent) },
                ) {
                    val phase by container.syncEngine.phase.collectAsState()
                    val pending by container.syncEngine.pendingCount.collectAsState(0)
                    val refused by container.syncEngine.refused.collectAsState(emptyList())
                    SyncStatus(
                        phase, pending, refused,
                        onSyncNow = container.syncCoordinator::requestSync,
                        onClearRefused = { scope.launch { container.syncEngine.clearRefused() } },
                    )
                    com.kurtz.homerecord.housemanual.account.SyncExplainer()
                }
            }
        }
        entry<ReportKey> { com.kurtz.homerecord.housemanual.report.ReportRoute(container.reportSource, container.reportFile, home.id, onBack = nav::pop) }
        entry<ContactsKey> { ContactsRoute(container.upkeep, home.id, onBack = nav::pop) }
        entry<DocumentsKey> { DocumentsRoute(container.documents, home.id, onBack = nav::pop, onOpen = { nav.push(DocumentViewerKey(it)) }) }
        entry<DocumentViewerKey> { key -> DocumentViewerRoute(container.documents, key.documentId, onBack = nav::pop, fetch = container::fetchDocument) }
        entry<RoomKey> { key -> RoomRoute(
            container.homes, container.items, container.wallPhotos, container.media, home.id, key.roomId, onBack = nav::pop,
            onOpenItem = { nav.push(ItemDetailKey(it)) }, onAddItem = { addItem(scope, container, home.id, nav, it) },
            onOutline = { nav.push(WallOutlineKey(key.roomId, it.wallId, it.stage.name, it.photoPath)) },
            onView3D = { nav.push(House3DKey(it.floorId, it.roomId)) },
            overlays = container.overlays,
            onOpenWall = { nav.push(RoomWallKey(key.roomId, it.wallId, it.stage.name, it.overlayId)) },
            onOpenPlanOverlay = { nav.push(PlanKey(overlayId = it)) },
            panels = container.panels,
            onOpenBreaker = { nav.push(PanelKey(it.panelItemId, highlightSlot = it.breaker.number)) },
        ) }
        entry<RoomWallKey> { key ->
            com.kurtz.homerecord.housemodel.ui.WallOverlaysRoute(
                container.homes, container.wallPhotos, container.overlays, container.wallTextures, key.roomId, key.wallId,
                com.kurtz.homerecord.housemodel.walls.WallStage.fromStored(key.stage), key.overlayId, onBack = nav::pop, media = container.media,
            )
        }
        entry<WallOutlineKey> { key ->
            com.kurtz.homerecord.housemodel.ui.WallOutlineRoute(
                container.wallPhotos, container.wallTextures, container.media, key.roomId, key.wallId,
                com.kurtz.homerecord.housemodel.walls.WallStage.fromStored(key.stage), key.photoPath, onClose = nav::pop,
            )
        }
        entry<FloorsKey> { FloorsRoute(container.homes, home.id, home.name, onBack = nav::pop) }
        entry<PlaceholderKey> { key ->
            val title = titles[key.title] ?: key.title
            if (key.depth == 0 && key.title == "more") {
                MoreScreen(
                    onEvent = { event ->
                        when (event) {
                            MoreEvent.OpenPro -> nav.push(PaywallKey())
                            MoreEvent.OpenContacts -> nav.push(ContactsKey)
                            MoreEvent.OpenEmergency -> nav.push(EmergencyKey)
                            MoreEvent.OpenReport -> nav.push(ReportKey)
                            MoreEvent.OpenBackup -> nav.push(BackupKey)
                            MoreEvent.OpenAccount -> nav.push(AccountKey)
                            MoreEvent.OpenPanels -> scope.launch {
                                nav.push(com.kurtz.homerecord.housemanual.panel.PanelsEntry.destination(container.items.observeRows(home.id, emptyMap()).first()))
                            }
                            MoreEvent.OpenDocuments -> nav.push(DocumentsKey)
                            MoreEvent.OpenImport -> pickHandoff.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
                        }
                    },
                )
            } else {
                PlaceholderDetail(title, onBack = nav::pop, onOpenDetail = { nav.push(key.copy(depth = key.depth + 1)) })
            }
        }
    })
}

/** The 16th free item, or a second free panel, opens HM-Paywall instead of the form (AC-H18); [AddItemGate] decides. */
private fun addItem(scope: kotlinx.coroutines.CoroutineScope, container: AppContainer, homeId: String, nav: com.kurtz.homerecord.coreui.shell.ShellNav, roomId: String? = null, subtypeId: String? = null, scanPlate: Boolean = false) {
    scope.launch { nav.push(container.addItemGate.destination(homeId, roomId, subtypeId, scanPlate)) }
}

/** Opens an overlay from search or an item's Links (AC-C28): its wall with it picked, or its room for a plan overlay. */
private fun openOverlay(nav: com.kurtz.homerecord.coreui.shell.ShellNav, o: com.kurtz.homerecord.housemodel.overlays.Overlay) {
    when (val target = OverlayTarget.of(o)) {
        is OverlayTarget.Wall -> nav.push(RoomWallKey(target.roomId, target.wallId, target.stage, target.overlayId))
        is OverlayTarget.Plan -> nav.push(PlanKey(overlayId = target.overlayId))
    }
}

/**
 * Asks for the notification permission (Android 13+) the first time the home has a reminder, so the 08:00 summary can
 * show; never before there is something to remind of, and once per install (AC-C50).
 */
@Composable
private fun AskNotificationsOnceRemindersExist(container: AppContainer, homeId: String) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(homeId) {
        container.reminders.observe(homeId).first { it.isNotEmpty() }
        val ask = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            container.shouldAskNotifications(homeId).also { if (it) container.markNotificationsAsked() }
        }
        if (ask) launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}
