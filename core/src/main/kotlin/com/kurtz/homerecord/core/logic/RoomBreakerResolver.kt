package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerRoom
import com.kurtz.homerecord.core.model.Endpoint

/** AC-20: breakers linked to a room directly or through an endpoint located in it. */
object RoomBreakerResolver {
    fun resolve(
        roomId: String,
        breakers: List<Breaker>,
        links: List<BreakerRoom>,
        endpoints: List<Endpoint>,
    ): List<Breaker> {
        val ids = links.filter { it.roomId == roomId }.map { it.breakerId }.toMutableSet()
        endpoints.filter { it.roomId == roomId }.mapNotNullTo(ids) { it.breakerId }
        return breakers.filter { it.id in ids }.sortedWith(compareBy({ it.slots.minOrNull() ?: Int.MAX_VALUE }, { it.label }))
    }
}
