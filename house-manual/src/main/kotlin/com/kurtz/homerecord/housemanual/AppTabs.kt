package com.kurtz.homerecord.housemanual

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.kurtz.homerecord.housemanual.contacts.ContactsRoute
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.housemodel.ui.PlanEditorRoute
import com.kurtz.homerecord.housemodel.ui.FloorsRoute
import com.kurtz.homerecord.housemodel.ui.HouseRoute
import com.kurtz.homerecord.housemodel.ui.RoomRoute
import com.kurtz.homerecord.housemodel.ui.ItemEditRoute
import com.kurtz.homerecord.housemanual.home.HomeRoute
import com.kurtz.homerecord.housemanual.items.ItemDetailRoute
import com.kurtz.homerecord.housemanual.items.ItemsRoute
import com.kurtz.homerecord.housemanual.onboarding.foundationLabel
import com.kurtz.homerecord.housemanual.onboarding.houseTypeLabel
import com.kurtz.homerecord.housemodel.Foundation
import com.kurtz.homerecord.housemodel.HouseType
import com.kurtz.homerecord.housemanual.house.OnboardingRoute
import com.kurtz.homerecord.coreui.auth.AccountRoute
import com.kurtz.homerecord.coreui.shell.AppTab
import com.kurtz.homerecord.coreui.shell.HomeRecordApp
import com.kurtz.homerecord.coreui.shell.PlaceholderDetail
import com.kurtz.homerecord.coreui.shell.PlaceholderKey
import com.kurtz.homerecord.coreui.shell.PlaceholderRoot

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
fun HouseManualApp(container: AppContainer) {
    var home by remember { mutableStateOf<HomeEntity?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(container) {
        container.homes.observeHome().collect { home = it; loaded = true }
    }
    val current = home
    // Set by "Draw your floor plan" so the new home opens straight on the plan editor, House tab.
    var openEditor by rememberSaveable { mutableStateOf(false) }
    when {
        !loaded -> Scaffold { LoadingState(Modifier.padding(it)) }
        current == null -> OnboardingRoute(container.homes, onDrawPlan = { openEditor = true })
        else -> HouseManualTabs(container, current, openEditor)
    }
}

@Composable
private fun HouseManualTabs(container: AppContainer, home: HomeEntity, openEditor: Boolean = false) {
    val tabs = tabDefs.map {
        AppTab(it.id, stringResource(it.label), it.icon, it.selected, when (it.id) { "home" -> HomeKey; "house" -> HouseKey; "items" -> ItemsKey; else -> PlaceholderKey(it.id) })
    }
    val titles = tabDefs.associate { it.id to stringResource(it.label) }
    HomeRecordApp(
        tabs = tabs,
        startTabId = if (openEditor) "house" else null,
        startKeys = if (openEditor) listOf(PlanKey()) else emptyList(),
        entries = { nav ->
        entry<HomeKey> {
            HomeRoute(
                container.items, container.search, home.id, home.name,
                onAddItem = { nav.push(ItemEditKey()) }, onOpenItem = { nav.push(ItemDetailKey(it)) },
                onOpenRoom = { nav.push(RoomKey(it)) }, onOpenDocument = { nav.push(DocumentViewerKey(it)) }, onOpenContacts = { nav.push(ContactsKey) },
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
            )
        }
        entry<PlanKey> { key ->
            PlanEditorRoute(
                container.homes, home.id, home.name, onBack = nav::pop, onOpenFloors = { nav.push(FloorsKey) },
                floorId = key.floorId, roomId = key.roomId, editShape = key.editShape,
            )
        }
        entry<ItemsKey> { ItemsRoute(container.items, home.id, onAdd = { nav.push(ItemEditKey()) }, onOpen = { nav.push(ItemDetailKey(it)) }) }
        entry<ItemDetailKey> { key ->
            ItemDetailRoute(
                container.items, container.warrantyLookup, container.upkeep, container.documents, home.id, key.itemId, onBack = nav::pop, onEdit = { nav.push(ItemEditKey(itemId = key.itemId)) },
                onOpenDocument = { nav.push(DocumentViewerKey(it)) },
                onOpenCopy = { nav.pop(); nav.push(ItemDetailKey(it)) },
            )
        }
        entry<ItemEditKey> { key ->
            ItemEditRoute(container.items, container.homes, container.media, container.textRecognizer, home.id, key.itemId, key.roomId, onClose = nav::pop, afterCreate = container::autoLookupWarranty)
        }
        entry<ContactsKey> { ContactsRoute(container.upkeep, home.id, onBack = nav::pop) }
        entry<DocumentsKey> { DocumentsRoute(container.documents, home.id, onBack = nav::pop, onOpen = { nav.push(DocumentViewerKey(it)) }) }
        entry<DocumentViewerKey> { key -> DocumentViewerRoute(container.documents, key.documentId, onBack = nav::pop) }
        entry<RoomKey> { key -> RoomRoute(container.homes, key.roomId, onBack = nav::pop) }
        entry<FloorsKey> { FloorsRoute(container.homes, home.id, home.name, onBack = nav::pop) }
        entry<PlaceholderKey> { key ->
            val title = titles[key.title] ?: key.title
            if (key.depth == 0 && key.title == "more") {
                Scaffold {
                    Column(Modifier.padding(it)) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.more_contacts)) },
                            supportingContent = { Text(stringResource(R.string.more_contacts_supporting)) },
                            leadingContent = { Icon(Icons.Outlined.Contacts, contentDescription = null) },
                            modifier = Modifier.heightIn(min = 56.dp).clickable { nav.push(ContactsKey) }.testTag("hm_more_contacts"),
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.more_documents)) },
                            supportingContent = { Text(stringResource(R.string.more_documents_supporting)) },
                            leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            modifier = Modifier.heightIn(min = 56.dp).clickable { nav.push(DocumentsKey) }.testTag("hm_more_documents"),
                        )
                        AccountRoute(container.auth, Modifier.weight(1f))
                    }
                }
            } else if (key.depth == 0) {
                PlaceholderRoot(title, onOpenDetail = { nav.push(key.copy(depth = 1)) })
            } else {
                PlaceholderDetail(title, onBack = nav::pop, onOpenDetail = { nav.push(key.copy(depth = key.depth + 1)) })
            }
        }
    })
}
