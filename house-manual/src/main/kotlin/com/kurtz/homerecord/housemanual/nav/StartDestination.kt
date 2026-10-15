package com.kurtz.homerecord.housemanual.nav

import com.kurtz.homerecord.core.model.Home
import com.kurtz.homerecord.core.repo.HomeRepository

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PANEL = "panel"
    const val EMERGENCY = "emergency"
    const val EQUIPMENT = "equipment"
    const val ROOMS = "rooms"
    const val ROOM = "room/{roomId}"

    fun room(id: String) = "room/$id"
}

/** AC-1 / AC-4: no Home yet means onboarding, otherwise the hub. */
object StartDestination {
    fun resolve(home: Home?): String = if (home == null) Routes.ONBOARDING else Routes.HOME

    suspend fun resolve(homes: HomeRepository): String = resolve(homes.getHome())
}
