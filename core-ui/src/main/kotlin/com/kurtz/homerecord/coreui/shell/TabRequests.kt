package com.kurtz.homerecord.coreui.shell

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Where an outside entry point wants the app: a tab and, optionally, the screens to open on it. With [keys] the tab's
 * stack is cut back to its root and the keys are pushed in order, so back walks from the deep screen to the tab's root.
 */
data class TabRequest(val tabId: String, val keys: List<NavKey> = emptyList())

/**
 * Tabs (and screens) asked for from outside the UI (a widget, a notification). Each request is handed to the
 * shell once and held until the tabs are on screen; a later composition (rotation, relaunch in the
 * same process) does not see it again, so the person's own tab choice sticks.
 */
class TabRequests {
    private val channel = Channel<TabRequest>(Channel.CONFLATED)

    val flow: Flow<TabRequest> = channel.receiveAsFlow()

    fun request(tabId: String, keys: List<NavKey> = emptyList()) {
        channel.trySend(TabRequest(tabId, keys))
    }
}
