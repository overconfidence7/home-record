package com.kurtz.homerecord.housemanual

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.kurtz.homerecord.core.data.HomeEntity
import com.kurtz.homerecord.coreui.components.LoadingState
import com.kurtz.homerecord.housemanual.house.FloorsRoute
import com.kurtz.homerecord.housemanual.house.HouseRoot
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
    when {
        !loaded -> Scaffold { LoadingState(Modifier.padding(it)) }
        current == null -> OnboardingRoute(container.homes)
        else -> HouseManualTabs(container, current)
    }
}

@Composable
private fun HouseManualTabs(container: AppContainer, home: HomeEntity) {
    val tabs = tabDefs.map {
        AppTab(it.id, stringResource(it.label), it.icon, it.selected, if (it.id == "house") HouseKey else PlaceholderKey(it.id))
    }
    val titles = tabDefs.associate { it.id to stringResource(it.label) }
    HomeRecordApp(tabs = tabs, entries = { nav ->
        entry<HouseKey> {
            val floors by remember(home.id) { container.homes.observeFloors(home.id) }.collectAsState(initial = emptyList())
            HouseRoot(home, floors.size, onOpenFloors = { nav.push(FloorsKey) })
        }
        entry<FloorsKey> { FloorsRoute(container.homes, home.id, home.name, onBack = nav::pop) }
        entry<PlaceholderKey> { key ->
            val title = titles[key.title] ?: key.title
            if (key.depth == 0 && key.title == "more") {
                AccountRoute(container.auth)
            } else if (key.depth == 0) {
                PlaceholderRoot(title, onOpenDetail = { nav.push(key.copy(depth = 1)) })
            } else {
                PlaceholderDetail(title, onBack = nav::pop, onOpenDetail = { nav.push(key.copy(depth = key.depth + 1)) })
            }
        }
    })
}
