package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.BreakerRoom
import com.kurtz.homerecord.core.model.Endpoint
import com.kurtz.homerecord.core.model.EndpointKind
import com.kurtz.homerecord.core.model.Equipment
import com.kurtz.homerecord.core.model.EquipmentCategory
import com.kurtz.homerecord.core.model.Room
import com.kurtz.homerecord.core.model.Shutoff
import com.kurtz.homerecord.core.model.ShutoffKind
import com.kurtz.homerecord.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    private val src = Source.HOUSE_MANUAL
    private fun room(id: String, name: String) = Room(id, "h", name, null, 0, 0, src)
    private fun breaker(id: String, label: String, slot: Int) = Breaker(id = id, panelId = "p", slots = listOf(slot), label = label, createdAt = 0, updatedAt = 0, source = src)
    private fun endpoint(id: String, label: String, room: String, breaker: String?) =
        Endpoint(id, breaker, room, EndpointKind.OUTLET, label, 0, 0, src)

    private val data = SearchData(
        breakers = listOf(breaker("b1", "Garage", 1), breaker("b2", "Kitchen", 2), breaker("b3", "Freezer circuit", 3)),
        breakerRooms = listOf(BreakerRoom("l1", "b2", "r2", 0, 0, src)),
        endpoints = listOf(endpoint("e1", "Freezer", "r1", "b1"), endpoint("e2", "Loose plug", "r2", null)),
        rooms = listOf(room("r1", "Garage"), room("r2", "Kitchen")),
        equipment = listOf(Equipment(id = "q1", homeId = "h", category = EquipmentCategory.FURNACE, make = "Carrier", model = "59TP6", createdAt = 0, updatedAt = 0, source = src)),
        shutoffs = listOf(Shutoff(id = "s1", homeId = "h", kind = ShutoffKind.WATER_MAIN, roomId = "r1", instructions = "Turn clockwise", createdAt = 0, updatedAt = 0, source = src)),
    )

    @Test fun `garage freezer returns garage breaker first (AC-17)`() {
        val results = SearchEngine.search("Garage Freezer", data)
        val top = results.first() as SearchResult.BreakerHit
        assertEquals("b1", top.breaker.id)
        assertEquals(listOf("Freezer"), top.matchedEndpoints)
    }

    @Test fun `AC-98 search covers breakers in every panel and reports each hit's panel`() {
        val sub = Breaker(id = "b9", panelId = "sub", slots = listOf(4), label = "Workshop saw", createdAt = 0, updatedAt = 0, source = src)
        val hits = SearchEngine.search("workshop", data.copy(breakers = data.breakers + sub))
        val hit = hits.single() as SearchResult.BreakerHit
        assertEquals("sub", hit.breaker.panelId)
        assertEquals(listOf("p"), (SearchEngine.search("kitchen", data.copy(breakers = data.breakers + sub)).filterIsInstance<SearchResult.BreakerHit>()).map { it.breaker.panelId }.distinct())
    }

    @Test fun `matching is case insensitive`() {
        assertEquals("b2", ((SearchEngine.search("KITCHEN", data).first()) as SearchResult.BreakerHit).breaker.id)
    }

    @Test fun `every token must match`() {
        assertTrue(SearchEngine.search("garage nonsense", data).isEmpty())
    }

    @Test fun `blank query returns nothing`() {
        assertTrue(SearchEngine.search("   ", data).isEmpty())
    }

    @Test fun `room link counts as a match`() {
        val ids = SearchEngine.search("kitchen", data).filterIsInstance<SearchResult.BreakerHit>().map { it.breaker.id }
        assertEquals(listOf("b2"), ids)
    }

    @Test fun `unmapped endpoint is found`() {
        assertTrue(SearchEngine.search("loose", data).single() is SearchResult.EndpointHit)
    }

    @Test fun `equipment by make model and category`() {
        for (q in listOf("carrier", "59tp6", "furnace carrier")) {
            assertTrue(q, SearchEngine.search(q, data).single() is SearchResult.EquipmentHit)
        }
    }

    @Test fun `shutoff by kind and instructions`() {
        assertTrue(SearchEngine.search("water main", data).single() is SearchResult.ShutoffHit)
        assertTrue(SearchEngine.search("clockwise", data).single() is SearchResult.ShutoffHit)
    }
}
