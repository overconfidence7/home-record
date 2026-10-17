package com.kurtz.homerecord.housemanual.nav

import com.kurtz.homerecord.core.model.Home
import com.kurtz.homerecord.core.repo.HomeRepository

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val PANEL = "panel"
    const val PANEL_PATTERN = "panel?highlight={highlight}"
    const val EMERGENCY = "emergency"
    const val EQUIPMENT = "equipment"
    const val EQUIPMENT_EDIT = "equipment/edit?id={id}"
    const val ROOMS = "rooms"
    const val SHUTOFFS = "shutoffs"
    const val SHUTOFF_EDIT = "shutoff?id={id}"
    const val ROOM = "room/{roomId}"
    const val MAPPING = "mapping"
    const val IMPORT = "import"

    const val BREAKER = "breaker/{panelId}/{slot}?breakerId={breakerId}&tandem={tandem}"

    fun panelAt(slot: Int) = "panel?highlight=$slot"

    fun shutoffEdit(id: String?) = "shutoff?id=${id.orEmpty()}"

    fun equipmentEdit(id: String?) = "equipment/edit?id=${id.orEmpty()}"

    fun room(id: String) = "room/$id"

    fun breaker(panelId: String, slot: Int, breakerId: String?, tandemIndex: Int?) =
        "breaker/$panelId/$slot?breakerId=${breakerId.orEmpty()}&tandem=${tandemIndex ?: -1}"
}

/** AC-1 / AC-4: no Home yet means onboarding, otherwise the hub. */
object StartDestination {
    fun resolve(home: Home?): String = if (home == null) Routes.ONBOARDING else Routes.HOME

    suspend fun resolve(homes: HomeRepository): String = resolve(homes.getHome())
}
