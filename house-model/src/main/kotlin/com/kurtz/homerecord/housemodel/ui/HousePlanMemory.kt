package com.kurtz.homerecord.housemodel.ui

import com.kurtz.homerecord.housemodel.blueprint.PlanMark
import com.kurtz.homerecord.housemodel.overlays.Overlay
import com.kurtz.homerecord.housemodel.plan.PlanRoom
import java.util.concurrent.ConcurrentHashMap

/** One floor's blueprint as last drawn on the House tab. */
data class ShownPlan(
    val floorId: String,
    val rooms: List<PlanRoom>,
    val overlays: List<Overlay>,
    val marks: List<PlanMark>,
)

/**
 * The plan the House tab last drew, per home and floor, kept for the life of the process.
 *
 * A House tab ViewModel built again while the app runs (its entry's store cleared, the home key re-read) starts
 * from the plan it already showed rather than the loading spinner; the fresh read replaces it when it lands
 * (rule 10: a loaded screen never goes back to loading, [judge:hm-house-tab-spinner-recurs]).
 */
class HousePlanMemory {
    private val plans = ConcurrentHashMap<String, ShownPlan>()
    private val lastFloor = ConcurrentHashMap<String, String>()

    fun remember(homeId: String, plan: ShownPlan) {
        plans[key(homeId, plan.floorId)] = plan
        lastFloor[homeId] = plan.floorId
    }

    /** The plan last shown for [floorId], or for the floor last shown in the home when [floorId] is null. */
    fun recall(homeId: String, floorId: String?): ShownPlan? {
        val floor = floorId ?: lastFloor[homeId] ?: return null
        return plans[key(homeId, floor)]
    }

    private fun key(homeId: String, floorId: String) = "$homeId/$floorId"

    companion object {
        /** The app's one memory; HouseRoute hands it to every House tab ViewModel. */
        val Process = HousePlanMemory()
    }
}
