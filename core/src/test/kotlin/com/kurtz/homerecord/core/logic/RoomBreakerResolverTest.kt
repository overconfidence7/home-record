package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerRoom
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.EndpointKind
import com.kurtz.homerecord.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomBreakerResolverTest {
    private fun breaker(id: String, slot: Int, label: String = id) =
        Breaker(id = id, panelId = "p", slots = listOf(slot), label = label, createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL)

    private fun link(b: String, r: String) = BreakerRoom("l$b$r", b, r, 0, 0, Source.HOUSE_MANUAL)

    private fun endpoint(b: String?, r: String) =
        Endpoint("e$b$r", b, r, EndpointKind.OUTLET, "x", 0, 0, Source.HOUSE_MANUAL)

    private val all = listOf(breaker("b3", 3), breaker("b1", 1), breaker("b2", 2), breaker("b4", 4))

    @Test fun `includes direct links and endpoint links without duplicates, ordered by slot`() {
        val result = RoomBreakerResolver.resolve(
            "kitchen", all,
            links = listOf(link("b3", "kitchen"), link("b1", "garage")),
            endpoints = listOf(endpoint("b2", "kitchen"), endpoint("b3", "kitchen"), endpoint("b4", "garage"), endpoint(null, "kitchen")),
        )
        assertEquals(listOf("b2", "b3"), result.map { it.id })
    }

    @Test fun `empty when nothing links to the room`() {
        assertEquals(emptyList<Breaker>(), RoomBreakerResolver.resolve("attic", all, emptyList(), emptyList()))
    }
}
