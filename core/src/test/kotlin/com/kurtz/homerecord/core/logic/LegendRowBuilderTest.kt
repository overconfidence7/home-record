package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Source
import org.junit.Assert.assertEquals
import org.junit.Test

class LegendRowBuilderTest {
    private fun breaker(id: String, vararg slots: Int, poles: Int = 1, amps: Int? = 15, tandem: Int? = null, label: String = id) =
        Breaker(id, "p", slots.toList(), poles, amps, label = label, tandemIndex = tandem, createdAt = 0, updatedAt = 0, source = Source.HOUSE_MANUAL)

    private fun build(count: Int, vararg breakers: Breaker, rooms: Map<String, List<String>> = emptyMap()) =
        LegendRowBuilder.build(count, breakers.toList(), { rooms[it.id].orEmpty() }, { "Standard" })

    @Test fun `AC-23 one row per slot in order with dashes for empty`() {
        val rows = build(3, breaker("Kitchen", 2, amps = 20), rooms = mapOf("Kitchen" to listOf("Pantry", "Kitchen")))
        assertEquals(listOf("1", "2", "3"), rows.map { it.slot })
        assertEquals(LegendRow("1", "—", "—", "—", "—", "—"), rows[0])
        assertEquals(LegendRow("2", "Kitchen", "20", "1", "Standard", "Kitchen, Pantry"), rows[1])
    }

    @Test fun `AC-24 two-pole prints once as N, N+2`() {
        val rows = build(6, breaker("Dryer", 1, 3, poles = 2, amps = 30))
        assertEquals(listOf("1, 3", "2", "4", "5", "6"), rows.map { it.slot })
        assertEquals("2", rows[0].poles)
        assertEquals("30", rows[0].amps)
    }

    @Test fun `AC-24 tandem prints N a and N b`() {
        val rows = build(2, breaker("A", 1, tandem = 0), breaker("B", 1, tandem = 1))
        assertEquals(listOf("1 a", "1 b", "2"), rows.map { it.slot })
        assertEquals(listOf("A", "B", "—"), rows.map { it.label })
    }

    @Test fun `tandem with one empty half still prints both halves`() {
        val rows = build(1, breaker("B", 1, tandem = 1))
        assertEquals(listOf("1 a", "1 b"), rows.map { it.slot })
        assertEquals(listOf("—", "B"), rows.map { it.label })
    }

    @Test fun `blank label and unknown amps print dashes`() {
        val rows = build(1, breaker("x", 1, amps = null, label = " "))
        assertEquals("—", rows[0].label)
        assertEquals("—", rows[0].amps)
    }
}
