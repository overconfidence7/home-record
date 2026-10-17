package com.kurtz.homerecord.core.logic

import com.kurtz.homerecord.core.model.Breaker
import com.kurtz.homerecord.core.model.Panel
import com.kurtz.homerecord.core.model.PanelKind
import com.kurtz.homerecord.core.model.PanelLayout
import com.kurtz.homerecord.core.model.RoomKind
import com.kurtz.homerecord.core.model.Source
import com.kurtz.homerecord.core.model.TandemPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelProfileTest {
    private fun panel(id: String = "p", kind: PanelKind = PanelKind.MAIN, feed: String? = null, name: String = "Main") = Panel(
        id, "h", name, slotCount = 40, layout = PanelLayout.NA_NUMBERED, createdAt = 0, updatedAt = 0,
        source = Source.HOUSE_MANUAL, kind = kind, feedBreakerId = feed,
    )

    private fun breaker(id: String, panelId: String = "p", main: Boolean = false, slot: Int = 1, tandem: Int? = null) = Breaker(
        id, panelId, listOf(slot), label = id, tandemIndex = tandem, createdAt = 0, updatedAt = 0,
        source = Source.HOUSE_MANUAL, isMain = main,
    )

    @Test fun defaultsAreMainUnknownTandemWithMainBreaker() {
        val result = PanelProfileRule.validate(PanelProfileInput(), 2026) as PanelProfileResult.Valid
        val p = result.profile
        assertEquals(PanelKind.MAIN, p.kind)
        assertEquals(TandemPolicy.UNKNOWN, p.tandemPolicy)
        assertTrue(p.hasMainBreaker)
        assertNull(p.ratingAmps); assertNull(p.brand); assertNull(p.maxCircuits); assertNull(p.yearInstalled)
    }

    @Test fun parsesAndTrimsOptionalFields() {
        val input = PanelProfileInput(ratingAmps = " 200 ", brand = " Square D QO ", model = "QO142", maxCircuits = "42", yearInstalled = "1998")
        val p = (PanelProfileRule.validate(input, 2026) as PanelProfileResult.Valid).profile
        assertEquals(200, p.ratingAmps)
        assertEquals("Square D QO", p.brand)
        assertEquals("QO142", p.model)
        assertEquals(42, p.maxCircuits)
        assertEquals(1998, p.yearInstalled)
    }

    @Test fun badNumbersAreReportedPerField() {
        val input = PanelProfileInput(ratingAmps = "abc", maxCircuits = "0", yearInstalled = "2099")
        val errors = (PanelProfileRule.validate(input, 2026) as PanelProfileResult.Invalid).errors
        assertEquals(setOf(PanelProfileError.RATING, PanelProfileError.MAX_CIRCUITS, PanelProfileError.YEAR), errors)
    }

    @Test fun ratingChoicesMatchSpec() {
        assertEquals(listOf(60, 100, 125, 150, 200, 225, 400), PanelProfileRule.RATING_CHOICES)
        assertEquals("Other", PanelProfileRule.BRAND_SUGGESTIONS.last())
        assertEquals(11, PanelProfileRule.BRAND_SUGGESTIONS.size)
    }

    @Test fun mainPanelDropsFeedBreaker() {
        val input = PanelProfileInput(kind = PanelKind.MAIN, feedBreakerId = "b1")
        val p = (PanelProfileRule.validate(input, 2026) as PanelProfileResult.Valid).profile
        assertNull(p.applyTo(panel(feed = "b1")).feedBreakerId)
    }

    @Test fun subPanelKeepsFeedBreaker() {
        val input = PanelProfileInput(kind = PanelKind.SUB, feedBreakerId = "b1")
        val p = (PanelProfileRule.validate(input, 2026) as PanelProfileResult.Valid).profile
        assertEquals("b1", p.applyTo(panel(kind = PanelKind.SUB)).feedBreakerId)
    }

    @Test fun feedCandidatesComeFromOtherPanelsMainFirstNoTandemHalves() {
        val all = listOf(
            breaker("own", "p"), breaker("a", "q", slot = 3), breaker("m", "q", main = true, slot = 9),
            breaker("half", "q", slot = 5, tandem = 0),
        )
        assertEquals(listOf("m", "a"), PanelProfileRule.feedCandidates("p", all).map { it.id })
    }

    @Test fun feedsByBreakerMapsSubPanelNames() {
        val panels = listOf(panel("p"), panel("s", PanelKind.SUB, feed = "b1", name = "Garage sub"), panel("s2", PanelKind.SUB))
        assertEquals(mapOf("b1" to "Garage sub"), PanelProfileRule.feedsByBreaker(panels))
    }

    @Test fun secondMainClearsTheFirst() {
        val all = listOf(breaker("a", main = true), breaker("b"), breaker("c", main = true))
        assertEquals(listOf("a", "c"), MainBreakerRule.toClear(all, "b").map { it.id })
        assertEquals(listOf("a"), MainBreakerRule.toClear(all, "c").map { it.id })
        assertEquals("a", MainBreakerRule.mainOf(all)?.id)
    }

    @Test fun legendHeaderListsKnownPartsOnly() {
        val text = LegendHeaderText("Main panel", "Sub-panel", { "$it A main" }, { "$it A" }, { "Installed $it" })
        val full = panel().copy(brand = "Eaton BR", model = "BR2040", ratingAmps = 200, yearInstalled = 2004)
        assertEquals("Main panel · Eaton BR · BR2040 · 200 A main · Installed 2004", LegendHeaderBuilder.build(full, text))
        assertEquals("Sub-panel · 100 A", LegendHeaderBuilder.build(panel(kind = PanelKind.SUB).copy(ratingAmps = 100), text))
        assertEquals("Main panel", LegendHeaderBuilder.build(panel(), text))
    }

    @Test fun legendShowsFeedsNote() {
        val rows = LegendRowBuilder.build(2, listOf(breaker("Garage")), { emptyList() }, { "Standard" }, { "→ feeds Sub" })
        assertEquals("Garage → feeds Sub", rows[0].label)
        assertEquals(LegendRowBuilder.EMPTY, rows[1].label)
    }

    @Test fun roomKindsFromQuickAddNames() {
        val expected = mapOf(
            "Kitchen" to RoomKind.KITCHEN, "Primary Bath" to RoomKind.BATHROOM, "Bathroom" to RoomKind.BATHROOM,
            "Bedroom 2" to RoomKind.BEDROOM, "Primary Bedroom" to RoomKind.BEDROOM, "Garage" to RoomKind.GARAGE,
            "Basement" to RoomKind.BASEMENT, "Laundry" to RoomKind.LAUNDRY, "Exterior" to RoomKind.EXTERIOR,
            "Hallway" to RoomKind.HALLWAY, "Living Room" to RoomKind.LIVING, "Dining Room" to RoomKind.DINING,
            "Office" to RoomKind.OFFICE, "Attic" to RoomKind.ATTIC, "Sunroom" to RoomKind.OTHER, "  kitchen " to RoomKind.KITCHEN,
        )
        expected.forEach { (name, kind) -> assertEquals(name, kind, RoomKindMapper.kindFor(name)) }
    }
}
