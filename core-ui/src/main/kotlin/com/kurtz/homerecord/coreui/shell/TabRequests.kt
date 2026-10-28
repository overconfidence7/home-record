package com.kurtz.homerecord.coreui.shell

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Tab ids asked for from outside the UI (a widget, a notification). Each request is handed to the
 * shell once and held until the tabs are on screen; a later composition (rotation, relaunch in the
 * same process) does not see it again, so the person's own tab choice sticks.
 */
class TabRequests {
    private val channel = Channel<String>(Channel.CONFLATED)

    val flow: Flow<String> = channel.receiveAsFlow()

    fun request(tabId: String) {
        channel.trySend(tabId)
    }
}
