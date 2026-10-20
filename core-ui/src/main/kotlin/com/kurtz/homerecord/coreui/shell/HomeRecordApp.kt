package com.kurtz.homerecord.coreui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay

/** Navigation handle given to screens inside the current tab's back stack. */
interface ShellNav {
    fun push(key: NavKey)
    fun pop()
}

/** A bottom-bar destination. [root] is the key the tab's own back stack starts from. */
class AppTab(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val root: NavKey,
)

/**
 * App shell: a navigation bar (rail from 600 dp) over one Navigation 3 back stack per tab.
 * Each stack and the selected tab survive rotation and process death.
 */
@Composable
fun HomeRecordApp(
    tabs: List<AppTab>,
    entries: EntryProviderScope<NavKey>.(nav: ShellNav) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedId by rememberSaveable { mutableStateOf(tabs.first().id) }
    val stacks = tabs.associate { it.id to rememberNavBackStack(it.root) }
    val selected = tabs.firstOrNull { it.id == selectedId } ?: tabs.first()
    val stack = stacks.getValue(selected.id)

    // The default suite type keeps a bottom bar in phone landscape (compact height); the UI
    // standard wants a rail on any width >= 600 dp.
    val wideEnough = currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = if (wideEnough) NavigationSuiteType.NavigationRail else NavigationSuiteType.NavigationBar,
        navigationSuiteItems = {
            tabs.forEach { tab ->
                val isSelected = tab.id == selected.id
                item(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) {
                            while (stack.size > 1) stack.removeAt(stack.lastIndex)
                        } else {
                            selectedId = tab.id
                        }
                    },
                    icon = {
                        Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null)
                    },
                    label = { Text(tab.label) },
                )
            }
        },
    ) {
        Box {
            NavDisplay(
                backStack = stack,
                // Scopes each screen's ViewModel to its back stack entry, so it is cleared on pop.
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                onBack = { if (stack.size > 1) stack.removeAt(stack.lastIndex) },
                entryProvider = entryProvider {
                    entries(object : ShellNav {
                        override fun push(key: NavKey) { stack.add(key) }
                        override fun pop() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
                    })
                },
            )
        }
    }
}
